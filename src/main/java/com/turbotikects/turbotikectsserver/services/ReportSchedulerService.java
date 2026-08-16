package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.EmailAttachment;
import com.turbotikects.turbotikectsserver.entitys.EmailMailboxEntity;
import com.turbotikects.turbotikectsserver.entitys.ReportDefinitionEntity;
import com.turbotikects.turbotikectsserver.entitys.ReportRunEntity;
import com.turbotikects.turbotikectsserver.entitys.ReportScheduleEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.GroupMemberRepository;
import com.turbotikects.turbotikectsserver.repositorys.ReportDefinitionRepository;
import com.turbotikects.turbotikectsserver.repositorys.ReportScheduleRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FEAT-05.4 — fires due report schedules and emails the results to their recipients. Same
 * @Scheduled + startup-catch-up + synchronized shape as RecurringTicketSchedulerService, with a
 * per-tick cap (like DashboardReportSchedulerService) for when several reports are due at once.
 * Deliberately NOT Quartz — see V2/repoets/feat-05-04-scheduling-delivery.html for why.
 */
@Slf4j
@Service
public class ReportSchedulerService {

    private static final int MAX_REPORTS_PER_TICK = 10;

    private final ReportScheduleRepository scheduleRepo;
    private final ReportDefinitionRepository reportRepo;
    private final ReportExecutionService reportExecutionService;
    private final EmailSenderService emailSenderService;
    private final FileStorageService fileStorageService;
    private final GroupMemberRepository groupMemberRepo;
    private final UserRepository userRepo;

    public ReportSchedulerService(ReportScheduleRepository scheduleRepo,
                                   ReportDefinitionRepository reportRepo,
                                   ReportExecutionService reportExecutionService,
                                   EmailSenderService emailSenderService,
                                   FileStorageService fileStorageService,
                                   GroupMemberRepository groupMemberRepo,
                                   UserRepository userRepo) {
        this.scheduleRepo = scheduleRepo;
        this.reportRepo = reportRepo;
        this.reportExecutionService = reportExecutionService;
        this.emailSenderService = emailSenderService;
        this.fileStorageService = fileStorageService;
        this.groupMemberRepo = groupMemberRepo;
        this.userRepo = userRepo;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void tick() {
        try {
            runDueSchedules();
        } catch (Exception e) {
            log.error("[ReportScheduler] Tick failed", e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("[ReportScheduler] Running startup catch-up pass");
        try {
            runDueSchedules();
        } catch (Exception e) {
            log.error("[ReportScheduler] Startup catch-up pass failed", e);
        }
    }

    public synchronized void runDueSchedules() {
        List<ReportScheduleEntity> due = scheduleRepo.findByNextRunAtLessThanEqual(LocalDateTime.now());
        int processed = 0;
        for (ReportScheduleEntity schedule : due) {
            if (processed >= MAX_REPORTS_PER_TICK) break;
            try {
                if (runOne(schedule)) processed++;
            } catch (Exception e) {
                log.error("[ReportScheduler] Run failed for schedule {} (report {})",
                        schedule.getId(), schedule.getReportDefinitionId(), e);
            }
        }
    }

    /** @return true if this schedule was actually fired (false if skipped, e.g. its report is
     * disabled — next_run_at still advances either way so a permanently-disabled report doesn't
     * get re-checked every single tick). */
    private boolean runOne(ReportScheduleEntity schedule) {
        Optional<ReportDefinitionEntity> reportOpt = reportRepo.findById(schedule.getReportDefinitionId());
        if (reportOpt.isEmpty() || !Boolean.TRUE.equals(reportOpt.get().getIsActive())) {
            advanceSchedule(schedule);
            return false;
        }
        ReportDefinitionEntity report = reportOpt.get();

        ReportRunEntity run = reportExecutionService.runReport(report.getId(), "scheduler");
        deliver(report, run, schedule);
        advanceSchedule(schedule);
        return true;
    }

    private void deliver(ReportDefinitionEntity report, ReportRunEntity run, ReportScheduleEntity schedule) {
        List<String> recipients = resolveRecipients(schedule);
        if (recipients.isEmpty()) {
            log.warn("[ReportScheduler] Report '{}' has no resolvable recipients — skipping delivery", report.getName());
            return;
        }
        Optional<EmailMailboxEntity> mailboxOpt = emailSenderService.getDefaultSender();
        if (mailboxOpt.isEmpty()) {
            log.warn("[ReportScheduler] No default sender mailbox configured — skipping delivery for report '{}'", report.getName());
            return;
        }
        EmailMailboxEntity mailbox = mailboxOpt.get();

        List<EmailAttachment> attachments = new ArrayList<>();
        try {
            if (run.getCsvPath() != null) {
                attachments.add(new EmailAttachment("report.csv", "text/csv", fileStorageService.retrieve(run.getCsvPath())));
            }
            if (run.getPdfPath() != null) {
                attachments.add(new EmailAttachment("report.pdf", "application/pdf", fileStorageService.retrieve(run.getPdfPath())));
            }
        } catch (Exception e) {
            log.error("[ReportScheduler] Failed to load generated files for report '{}'", report.getName(), e);
        }

        String subject = report.getName() + " — " + ("no_data".equals(run.getStatus()) ? "No data found" : run.getRowCount() + " results");
        String body = "<p>Your scheduled report <strong>" + report.getName() + "</strong> is attached.</p>"
                + (run.getAiSummary() != null ? "<p>" + run.getAiSummary() + "</p>" : "");

        for (String email : recipients) {
            emailSenderService.sendReply(mailbox, email, subject, body, null, attachments);
        }
    }

    private List<String> resolveRecipients(ReportScheduleEntity schedule) {
        if (schedule.getRecipientGroupId() != null) {
            return groupMemberRepo.findByGroupId(schedule.getRecipientGroupId().longValue()).stream()
                    .map(m -> userRepo.findById(m.getUserId()).orElse(null))
                    .filter(u -> u != null && !u.isDeleted() && u.getEmail() != null && !u.getEmail().isBlank())
                    .map(UserEntity::getEmail)
                    .collect(Collectors.toList());
        }
        if (schedule.getRecipientUserIds() != null) {
            return schedule.getRecipientUserIds().stream()
                    .map(id -> userRepo.findById(id.longValue()).orElse(null))
                    .filter(u -> u != null && !u.isDeleted() && u.getEmail() != null && !u.getEmail().isBlank())
                    .map(UserEntity::getEmail)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private void advanceSchedule(ReportScheduleEntity schedule) {
        LocalDateTime now = LocalDateTime.now();
        schedule.setLastRunAt(now);
        schedule.setNextRunAt(CronExpression.parse(schedule.getCronExpression()).next(now));
        scheduleRepo.save(schedule);
    }
}
