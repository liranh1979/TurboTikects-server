package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.RecurringScheduleDto;
import com.turbotikects.turbotikectsserver.dto.SaveRecurringScheduleRequestDto;
import com.turbotikects.turbotikectsserver.entitys.GroupEntity;
import com.turbotikects.turbotikectsserver.entitys.RecurringScheduleEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateEntity;
import com.turbotikects.turbotikectsserver.repositorys.GroupRepository;
import com.turbotikects.turbotikectsserver.repositorys.RecurringScheduleRepository;
import com.turbotikects.turbotikectsserver.repositorys.TemplateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RecurringScheduleService {

    private final RecurringScheduleRepository scheduleRepo;
    private final TemplateRepository templateRepo;
    private final GroupRepository groupRepo;

    public RecurringScheduleService(RecurringScheduleRepository scheduleRepo,
                                     TemplateRepository templateRepo,
                                     GroupRepository groupRepo) {
        this.scheduleRepo = scheduleRepo;
        this.templateRepo = templateRepo;
        this.groupRepo = groupRepo;
    }

    public List<RecurringScheduleDto> getAll() {
        return scheduleRepo.findAllByOrderByNameAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public RecurringScheduleDto create(SaveRecurringScheduleRequestDto dto) {
        RecurringScheduleEntity entity = new RecurringScheduleEntity();
        applyRequest(entity, dto);
        entity = scheduleRepo.save(entity);
        return toDto(entity);
    }

    public RecurringScheduleDto update(Long id, SaveRecurringScheduleRequestDto dto) {
        RecurringScheduleEntity entity = scheduleRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring schedule not found"));
        applyRequest(entity, dto);
        entity = scheduleRepo.save(entity);
        return toDto(entity);
    }

    public void setActive(Long id, boolean active) {
        RecurringScheduleEntity entity = scheduleRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring schedule not found"));
        entity.setIsActive(active);
        scheduleRepo.save(entity);
    }

    public void delete(Long id) {
        if (!scheduleRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring schedule not found");
        }
        scheduleRepo.deleteById(id);
    }

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

    private void applyRequest(RecurringScheduleEntity entity, SaveRecurringScheduleRequestDto dto) {
        if (!templateRepo.existsById(dto.getTemplateId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown template");
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

        entity.setName(dto.getName());
        entity.setTemplateId(dto.getTemplateId());
        entity.setCronExpression(dto.getCronExpression());
        entity.setFrequencyType(dto.getFrequencyType());
        entity.setTitleTemplate(dto.getTitleTemplate());
        entity.setAssignGroupId(dto.getAssignGroupId());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : "medium");
        entity.setIsActive(dto.getIsActive() == null || dto.getIsActive());
        entity.setNextRunAt(nextRunAt);
    }

    private RecurringScheduleDto toDto(RecurringScheduleEntity e) {
        RecurringScheduleDto dto = new RecurringScheduleDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setTemplateId(e.getTemplateId());
        dto.setTemplateName(templateRepo.findById(e.getTemplateId()).map(TemplateEntity::getName).orElse(null));
        dto.setCronExpression(e.getCronExpression());
        dto.setFrequencyType(e.getFrequencyType());
        dto.setTitleTemplate(e.getTitleTemplate());
        dto.setAssignGroupId(e.getAssignGroupId());
        if (e.getAssignGroupId() != null) {
            dto.setAssignGroupName(groupRepo.findById(e.getAssignGroupId().longValue())
                    .map(GroupEntity::getDisplayName).orElse(null));
        }
        dto.setPriority(e.getPriority());
        dto.setIsActive(e.getIsActive());
        dto.setLastRunAt(e.getLastRunAt());
        dto.setNextRunAt(e.getNextRunAt());
        return dto;
    }
}
