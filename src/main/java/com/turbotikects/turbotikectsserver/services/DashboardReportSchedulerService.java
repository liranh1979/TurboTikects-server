package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.repositorys.CompanyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the three Tickets Dashboard AI caches (recurring-problems report, CSAT analysis, SLA
 * insight) fresh in the background so {@code GET /dashboard/tickets} never has to call an LLM
 * itself — that endpoint only ever reads from {@code dashboard_report_cache}
 * (see {@code DashboardService}/{@code CsatDashboardService}/{@code SlaDashboardService}'s
 * getCached-vs-refreshIfStale split). Deliberately its own class rather than folded into
 * {@link SlaSchedulerService}'s tick — dashboard refresh isn't an SLA concern, and that class is
 * explicitly SLA-scoped.
 *
 * No explicit cache-invalidation hooks exist anywhere in TicketService/WorkflowService — each
 * service's fingerprint is a live COUNT/MAX(updated_at) aggregate query, so it automatically goes
 * stale the moment a ticket or workflow item changes; this poller just has to notice, not be told.
 *
 * Mirrors {@link SlaSchedulerService}'s established @Scheduled + @EventListener(ApplicationReadyEvent)
 * + synchronized shape exactly — that combination (scheduled tick vs. startup catch-up both racing
 * to act on the same stale state before either records progress) is a real bug this codebase has
 * hit and fixed twice already (SLA escalation firing, then AiTriageAgent scoring); synchronized on
 * the shared entry point is what actually prevents it, not the initialDelay alone.
 *
 * Capped on both axes per tick, matching AiTriageAgent's BATCH_LIMIT precedent — each refresh is a
 * real 30-40s+ LLM call, and this whole method is synchronized, so an unbounded loop here would
 * block every subsequent tick for a very long time.
 */
@Slf4j
@Service
public class DashboardReportSchedulerService {

    private static final int MAX_REFRESHES_PER_TICK = 6;
    private static final long MAX_TICK_SECONDS = 45;

    private final CompanyRepository companyRepo;
    private final DashboardService dashboardService;
    private final CsatDashboardService csatDashboardService;
    private final SlaDashboardService slaDashboardService;

    public DashboardReportSchedulerService(CompanyRepository companyRepo, DashboardService dashboardService,
                                            CsatDashboardService csatDashboardService, SlaDashboardService slaDashboardService) {
        this.companyRepo = companyRepo;
        this.dashboardService = dashboardService;
        this.csatDashboardService = csatDashboardService;
        this.slaDashboardService = slaDashboardService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void tick() {
        try {
            refreshStaleReports();
        } catch (Exception e) {
            log.error("[Dashboard] Report scheduler tick failed", e);
        }
    }

    /**
     * Catch-up pass so a period of downtime doesn't leave stale dashboards sitting until the first
     * tick. Run on a fresh thread rather than inline — confirmed live that running this synchronously
     * (up to {@link #MAX_TICK_SECONDS} of real LLM calls) blocks Spring's `ApplicationReadyEvent`
     * dispatch itself, since that's a synchronous, sequential call to every `@EventListener` on the
     * publishing (main) thread: {@link SlaSchedulerService}'s own startup catch-up listener was
     * observed firing ~56s late, stuck waiting behind this one in the same dispatch chain. The HTTP
     * server is already up by this point regardless (Tomcat starts before `ApplicationReadyEvent`
     * fires), so this doesn't delay the app being reachable — only sibling `ApplicationReadyEvent`
     * listeners, which is still worth not doing.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("[Dashboard] Running startup catch-up report refresh");
        new Thread(() -> {
            try {
                refreshStaleReports();
            } catch (Exception e) {
                log.error("[Dashboard] Startup catch-up report refresh failed", e);
            }
        }, "dashboard-report-startup-catchup").start();
    }

    public synchronized void refreshStaleReports() {
        List<Integer> companyIds = new ArrayList<>();
        companyIds.add(null); // global scope (super-admin view)
        companyRepo.findAll().forEach(c -> companyIds.add(c.getId()));

        LocalDateTime tickStart = LocalDateTime.now();
        int refreshed = 0;

        for (Integer companyId : companyIds) {
            if (refreshed >= MAX_REFRESHES_PER_TICK) break;
            if (Duration.between(tickStart, LocalDateTime.now()).toSeconds() >= MAX_TICK_SECONDS) break;
            refreshed += refreshOneCompany(companyId, tickStart);
        }
    }

    /** Returns how many of the 3 dashboards were actually refreshed for this company (0-3), respecting the time budget. */
    private int refreshOneCompany(Integer companyId, LocalDateTime tickStart) {
        int refreshed = 0;

        if (withinBudget(tickStart)) {
            try {
                dashboardService.refreshAiReportIfStale(companyId);
                refreshed++;
            } catch (Exception e) {
                log.error("[Dashboard] AI report refresh failed for company {}", companyId, e);
            }
        }
        if (withinBudget(tickStart)) {
            try {
                csatDashboardService.refreshAiAnalysisIfStale(companyId);
                refreshed++;
            } catch (Exception e) {
                log.error("[Dashboard] CSAT AI analysis refresh failed for company {}", companyId, e);
            }
        }
        if (withinBudget(tickStart)) {
            try {
                slaDashboardService.refreshAiInsightIfStale(companyId);
                refreshed++;
            } catch (Exception e) {
                log.error("[Dashboard] SLA AI insight refresh failed for company {}", companyId, e);
            }
        }
        return refreshed;
    }

    private boolean withinBudget(LocalDateTime tickStart) {
        return Duration.between(tickStart, LocalDateTime.now()).toSeconds() < MAX_TICK_SECONDS;
    }
}
