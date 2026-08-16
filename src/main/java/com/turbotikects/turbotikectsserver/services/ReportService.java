package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.ReportDefinitionDto;
import com.turbotikects.turbotikectsserver.dto.ReportRunDto;
import com.turbotikects.turbotikectsserver.dto.SaveReportRequestDto;
import com.turbotikects.turbotikectsserver.entitys.GroupEntity;
import com.turbotikects.turbotikectsserver.entitys.ReportDefinitionEntity;
import com.turbotikects.turbotikectsserver.entitys.ReportRunEntity;
import com.turbotikects.turbotikectsserver.entitys.ReportScheduleEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.GroupRepository;
import com.turbotikects.turbotikectsserver.repositorys.ReportDefinitionRepository;
import com.turbotikects.turbotikectsserver.repositorys.ReportRunRepository;
import com.turbotikects.turbotikectsserver.repositorys.ReportScheduleRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** CRUD for report_definitions + its optional 1:1 report_schedules. Execution (Test/scheduled
 * runs) lives in ReportExecutionService — this class only manages the saved definition. */
@Service
public class ReportService {

    private final ReportDefinitionRepository reportRepo;
    private final ReportScheduleRepository scheduleRepo;
    private final ReportRunRepository runRepo;
    private final GroupRepository groupRepo;
    private final UserRepository userRepo;

    public ReportService(ReportDefinitionRepository reportRepo,
                          ReportScheduleRepository scheduleRepo,
                          ReportRunRepository runRepo,
                          GroupRepository groupRepo,
                          UserRepository userRepo) {
        this.reportRepo = reportRepo;
        this.scheduleRepo = scheduleRepo;
        this.runRepo = runRepo;
        this.groupRepo = groupRepo;
        this.userRepo = userRepo;
    }

    /** Scoped to MANAGE_REPORTS only (not MANAGE_GROUPS), same reasoning as
     * RecurringScheduleController's own dedicated /groups endpoint. */
    public List<Map<String, Object>> getAssignableGroups() {
        return groupRepo.findAssignableGroups().stream()
                .map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", g.getRefId());
                    m.put("displayName", g.getDisplayName());
                    return m;
                })
                .collect(Collectors.toList());
    }

    /** Scoped to MANAGE_REPORTS only (not MANAGE_USERS) — for the "Admin user(s)" recipient picker. */
    public List<Map<String, Object>> getAssignableUsers() {
        return userRepo.findAll().stream()
                .filter(u -> !u.isDeleted())
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getRed_id());
                    m.put("displayName", u.getDisplayName());
                    m.put("email", u.getEmail());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public List<ReportDefinitionDto> getAll() {
        return reportRepo.findAllByOrderByNameAsc().stream().map(this::toDto).collect(Collectors.toList());
    }

    public ReportDefinitionDto getOne(Long id) {
        return toDto(findEntity(id));
    }

    @Transactional
    public ReportDefinitionDto create(SaveReportRequestDto dto, Integer actorUserId) {
        ReportDefinitionEntity entity = new ReportDefinitionEntity();
        entity.setCreatedBy(actorUserId);
        applyRequest(entity, dto);
        entity = reportRepo.save(entity);
        applySchedule(entity.getId(), dto);
        return toDto(reportRepo.findById(entity.getId()).orElseThrow());
    }

    @Transactional
    public ReportDefinitionDto update(Long id, SaveReportRequestDto dto) {
        ReportDefinitionEntity entity = findEntity(id);
        applyRequest(entity, dto);
        reportRepo.save(entity);
        applySchedule(id, dto);
        return toDto(reportRepo.findById(id).orElseThrow());
    }

    public void setActive(Long id, boolean active) {
        ReportDefinitionEntity entity = findEntity(id);
        entity.setIsActive(active);
        reportRepo.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        if (!reportRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
        reportRepo.deleteById(id); // cascades to report_schedules/report_runs at the DB level
    }

    public List<ReportRunDto> getRuns(Long reportId) {
        return runRepo.findByReportDefinitionIdOrderByStartedAtDesc(reportId).stream()
                .map(this::toRunDto).collect(Collectors.toList());
    }

    private ReportRunDto toRunDto(ReportRunEntity r) {
        ReportRunDto dto = new ReportRunDto();
        dto.setId(r.getId());
        dto.setReportDefinitionId(r.getReportDefinitionId());
        dto.setTriggeredBy(r.getTriggeredBy());
        dto.setRowCount(r.getRowCount());
        dto.setStatus(r.getStatus());
        dto.setAiSummary(r.getAiSummary());
        dto.setAiTips(r.getAiTips());
        dto.setCsvPath(r.getCsvPath());
        dto.setPdfPath(r.getPdfPath());
        dto.setStartedAt(r.getStartedAt());
        dto.setCompletedAt(r.getCompletedAt());
        dto.setErrorMessage(r.getErrorMessage());
        return dto;
    }

    ReportDefinitionEntity findEntity(Long id) {
        return reportRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    private void applyRequest(ReportDefinitionEntity entity, SaveReportRequestDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report name is required");
        }
        if (dto.getSelectedFields() == null || dto.getSelectedFields().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one field must be selected");
        }
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        Map<String, Object> querySpec = new LinkedHashMap<>();
        querySpec.put("selectedFields", dto.getSelectedFields());
        querySpec.put("conditions", dto.getConditions() != null ? dto.getConditions() : Map.of());
        entity.setQuerySpec(querySpec);
        entity.setExportFormats(dto.getExportFormats() != null && !dto.getExportFormats().isEmpty()
                ? dto.getExportFormats() : List.of("csv", "pdf"));
        entity.setIsActive(dto.getIsActive() == null || dto.getIsActive());
        entity.setAiGenerated(Boolean.TRUE.equals(dto.getAiGenerated()));
        entity.setLastAiPrompt(dto.getLastAiPrompt());
    }

    private void applySchedule(Long reportDefinitionId, SaveReportRequestDto dto) {
        if (!Boolean.TRUE.equals(dto.getScheduleEnabled())) {
            scheduleRepo.deleteByReportDefinitionId(reportDefinitionId);
            return;
        }
        if (dto.getCronExpression() == null || dto.getCronExpression().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A schedule frequency is required");
        }
        boolean hasUsers = dto.getRecipientUserIds() != null && !dto.getRecipientUserIds().isEmpty();
        boolean hasGroup = dto.getRecipientGroupId() != null;
        if (hasUsers == hasGroup) { // both or neither
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose either specific admin user(s) or a group as recipients, not both/neither");
        }
        LocalDateTime nextRunAt;
        try {
            nextRunAt = CronExpression.parse(dto.getCronExpression()).next(LocalDateTime.now());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cron expression: " + e.getMessage());
        }
        if (nextRunAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cron expression never fires");
        }

        ReportScheduleEntity schedule = scheduleRepo.findByReportDefinitionId(reportDefinitionId)
                .orElseGet(ReportScheduleEntity::new);
        schedule.setReportDefinitionId(reportDefinitionId);
        schedule.setCronExpression(dto.getCronExpression());
        schedule.setFrequencyType(dto.getFrequencyType());
        schedule.setRecipientUserIds(hasUsers ? dto.getRecipientUserIds() : null);
        schedule.setRecipientGroupId(hasGroup ? dto.getRecipientGroupId() : null);
        schedule.setNextRunAt(nextRunAt);
        scheduleRepo.save(schedule);
    }

    @SuppressWarnings("unchecked")
    private ReportDefinitionDto toDto(ReportDefinitionEntity e) {
        ReportDefinitionDto dto = new ReportDefinitionDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setDescription(e.getDescription());
        Map<String, Object> qs = e.getQuerySpec() != null ? e.getQuerySpec() : Map.of();
        dto.setSelectedFields((List<String>) qs.getOrDefault("selectedFields", List.of()));
        dto.setConditions((Map<String, Object>) qs.getOrDefault("conditions", Map.of()));
        dto.setExportFormats(e.getExportFormats());
        dto.setIsActive(e.getIsActive());
        dto.setAiGenerated(e.getAiGenerated());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());

        scheduleRepo.findByReportDefinitionId(e.getId()).ifPresentOrElse(s -> {
            dto.setScheduleEnabled(true);
            dto.setCronExpression(s.getCronExpression());
            dto.setFrequencyType(s.getFrequencyType());
            dto.setRecipientUserIds(s.getRecipientUserIds());
            dto.setRecipientGroupId(s.getRecipientGroupId());
            if (s.getRecipientGroupId() != null) {
                dto.setRecipientGroupName(groupRepo.findById(s.getRecipientGroupId().longValue())
                        .map(GroupEntity::getDisplayName).orElse(null));
            }
            dto.setNextRunAt(s.getNextRunAt());
        }, () -> dto.setScheduleEnabled(false));

        runRepo.findFirstByReportDefinitionIdOrderByStartedAtDesc(e.getId()).ifPresent(run -> {
            dto.setLastRunAt(run.getStartedAt());
            dto.setLastRunStatus(run.getStatus());
        });

        return dto;
    }
}
