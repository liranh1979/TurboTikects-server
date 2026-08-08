package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.CreateTicketRequestDto;
import com.turbotikects.turbotikectsserver.entitys.RecurringScheduleEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateVersionEntity;
import com.turbotikects.turbotikectsserver.repositorys.RecurringScheduleRepository;
import com.turbotikects.turbotikectsserver.repositorys.TemplateVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Ticks every minute (plus once on application startup, so downtime doesn't leave a schedule
 * silently stuck) checking every active recurring schedule's next_run_at and creating a ticket for
 * any that are due. See V2/feature-13-recurring.html for the spec.
 */
@Slf4j
@Service
public class RecurringTicketSchedulerService {

    // System user ID used for automated actions (root user's red_id) — same convention as
    // EmailProcessorService.SYSTEM_ACTOR_ID.
    private static final int SYSTEM_ACTOR_ID = 1;

    private final RecurringScheduleRepository scheduleRepo;
    private final TemplateVersionRepository templateVersionRepo;
    private final TicketService ticketService;

    public RecurringTicketSchedulerService(RecurringScheduleRepository scheduleRepo,
                                            TemplateVersionRepository templateVersionRepo,
                                            TicketService ticketService) {
        this.scheduleRepo = scheduleRepo;
        this.templateVersionRepo = templateVersionRepo;
        this.ticketService = ticketService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void tick() {
        try {
            runDueSchedules();
        } catch (Exception e) {
            log.error("[RecurringTickets] Scheduler tick failed", e);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("[RecurringTickets] Running startup catch-up pass");
        try {
            runDueSchedules();
        } catch (Exception e) {
            log.error("[RecurringTickets] Startup catch-up pass failed", e);
        }
    }

    public synchronized void runDueSchedules() {
        List<RecurringScheduleEntity> due =
                scheduleRepo.findByIsActiveTrueAndNextRunAtLessThanEqual(LocalDateTime.now());
        for (RecurringScheduleEntity schedule : due) {
            try {
                runOne(schedule);
            } catch (Exception e) {
                log.error("[RecurringTickets] Run failed for schedule {} ({})", schedule.getId(), schedule.getName(), e);
            }
        }
    }

    private void runOne(RecurringScheduleEntity schedule) {
        TemplateVersionEntity version = templateVersionRepo
                .findByTemplateIdAndIsCurrentTrue(schedule.getTemplateId())
                .orElseGet(() -> templateVersionRepo
                        .findByTemplateIdOrderByVersionNumberDesc(schedule.getTemplateId())
                        .stream().findFirst().orElse(null));
        if (version == null) {
            log.warn("[RecurringTickets] No template version found for schedule {} (template {}) — skipping this run, next_run_at still advances",
                    schedule.getId(), schedule.getTemplateId());
            advanceSchedule(schedule);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        CreateTicketRequestDto dto = new CreateTicketRequestDto();
        dto.setTitle(substituteTitlePlaceholders(schedule.getTitleTemplate(), now));
        dto.setTemplateId(schedule.getTemplateId());
        dto.setTemplateVersionId(version.getId());
        dto.setPriority(schedule.getPriority());
        dto.setResponsibleGroupId(schedule.getAssignGroupId());
        dto.setRequestUserId(SYSTEM_ACTOR_ID);

        ticketService.create(dto, SYSTEM_ACTOR_ID, "recurring");

        advanceSchedule(schedule);
    }

    private void advanceSchedule(RecurringScheduleEntity schedule) {
        LocalDateTime now = LocalDateTime.now();
        schedule.setLastRunAt(now);
        schedule.setNextRunAt(CronExpression.parse(schedule.getCronExpression()).next(now));
        scheduleRepo.save(schedule);
    }

    /**
     * {{month}}/{{year}}/{{week}}/{{quarter}} — pure LocalDate/WeekFields math, independent of any
     * i18n/templating system. Case-insensitive, whitespace-tolerant inside braces, mirrored 1:1 by
     * the frontend's previewTitle() for an accurate live preview.
     */
    static String substituteTitlePlaceholders(String template, LocalDateTime at) {
        LocalDate date = at.toLocalDate();
        String month = date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String year = String.valueOf(date.getYear());
        int week = date.get(WeekFields.of(Locale.ENGLISH).weekOfWeekBasedYear());
        int quarter = (date.getMonthValue() - 1) / 3 + 1;

        String result = template;
        result = result.replaceAll("(?i)\\{\\{\\s*month\\s*\\}\\}", Matcher.quoteReplacement(month));
        result = result.replaceAll("(?i)\\{\\{\\s*year\\s*\\}\\}", Matcher.quoteReplacement(year));
        result = result.replaceAll("(?i)\\{\\{\\s*week\\s*\\}\\}", Matcher.quoteReplacement(String.valueOf(week)));
        result = result.replaceAll("(?i)\\{\\{\\s*quarter\\s*\\}\\}", Matcher.quoteReplacement(String.valueOf(quarter)));
        return result;
    }
}
