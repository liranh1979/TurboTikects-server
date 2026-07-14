package com.turbotikects.turbotikectsserver.controller;

import com.turbotikects.turbotikectsserver.dto.SlaEscalationStepDto;
import com.turbotikects.turbotikectsserver.dto.SlaPolicyDto;
import com.turbotikects.turbotikectsserver.entitys.SlaEscalationStepEntity;
import com.turbotikects.turbotikectsserver.entitys.SlaPolicyEntity;
import com.turbotikects.turbotikectsserver.repositorys.SlaEscalationStepRepository;
import com.turbotikects.turbotikectsserver.repositorys.SlaPolicyRepository;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sla-policies")
@RequirePermission("MANAGE_FIELDS")
public class SlaPolicyController {

    private final SlaPolicyRepository slaPolicyRepository;
    private final SlaEscalationStepRepository slaEscalationStepRepository;

    public SlaPolicyController(SlaPolicyRepository slaPolicyRepository,
                                SlaEscalationStepRepository slaEscalationStepRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.slaEscalationStepRepository = slaEscalationStepRepository;
    }

    @GetMapping
    public List<SlaPolicyDto> getAllPolicies() {
        return slaPolicyRepository.findAllByOrderByPriorityAsc().stream().map(this::toDto).toList();
    }

    @PostMapping
    @Transactional
    public SlaPolicyDto createPolicy(@RequestBody SlaPolicyDto dto) {
        SlaPolicyEntity entity = new SlaPolicyEntity();
        applyDto(entity, dto);
        entity = slaPolicyRepository.save(entity);
        replaceSteps(entity.getId(), dto.getSteps());
        return toDto(entity);
    }

    @PutMapping("/{id}")
    @Transactional
    public SlaPolicyDto updatePolicy(@PathVariable Long id, @RequestBody SlaPolicyDto dto) {
        SlaPolicyEntity entity = slaPolicyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SLA policy not found"));
        applyDto(entity, dto);
        entity = slaPolicyRepository.save(entity);
        if (dto.getSteps() != null) {
            replaceSteps(entity.getId(), dto.getSteps());
        }
        return toDto(entity);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePolicy(@PathVariable Long id) {
        SlaPolicyEntity entity = slaPolicyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SLA policy not found"));
        // Soft delete — ticket_sla_state rows reference the policy, so it can't just be removed.
        entity.setIsActive(false);
        slaPolicyRepository.save(entity);
    }

    private void applyDto(SlaPolicyEntity entity, SlaPolicyDto dto) {
        entity.setPriority(dto.getPriority());
        entity.setTemplateId(dto.getTemplateId());
        entity.setFirstResponseMinutes(dto.getFirstResponseMinutes());
        entity.setResolutionMinutes(dto.getResolutionMinutes());
        entity.setBusinessHours(dto.isBusinessHours());
        entity.setIsActive(dto.getIsActive() == null || dto.getIsActive());
    }

    /**
     * Upserts by id rather than delete-all+reinsert. Step ids are the FK target of
     * {@code ticket_sla_escalations_fired} (ON DELETE CASCADE) — recycling ids on every save
     * (e.g. re-saving a policy's minutes without touching its ladder) would silently wipe that
     * ticket's already-fired escalation history and let the same threshold re-fire and re-notify.
     * Only steps genuinely removed by the caller (id present in DB but absent from the incoming
     * list) are deleted; everything else is updated in place or inserted as new.
     */
    private void replaceSteps(Long policyId, List<SlaEscalationStepDto> steps) {
        if (steps == null) return;
        List<SlaEscalationStepEntity> existing = slaEscalationStepRepository.findBySlaPolicyIdOrderByStepOrderAsc(policyId);
        java.util.Map<Long, SlaEscalationStepEntity> existingById = existing.stream()
                .collect(java.util.stream.Collectors.toMap(SlaEscalationStepEntity::getId, s -> s));

        java.util.Set<Long> keepIds = new java.util.HashSet<>();
        List<SlaEscalationStepEntity> toSave = new ArrayList<>();
        int order = 0;
        for (SlaEscalationStepDto stepDto : steps) {
            SlaEscalationStepEntity step = stepDto.getId() != null ? existingById.get(stepDto.getId()) : null;
            if (step == null) step = new SlaEscalationStepEntity();
            else keepIds.add(step.getId());
            step.setSlaPolicyId(policyId);
            step.setStepOrder(order++);
            step.setTriggerType(stepDto.getTriggerType());
            step.setTriggerValue(stepDto.getTriggerValue());
            step.setActionType(stepDto.getActionType());
            step.setTargetUserId(stepDto.getTargetUserId());
            step.setTargetGroupId(stepDto.getTargetGroupId());
            step.setWebhookUrl(stepDto.getWebhookUrl());
            toSave.add(step);
        }
        slaEscalationStepRepository.saveAll(toSave);

        List<Long> toDelete = existing.stream().map(SlaEscalationStepEntity::getId)
                .filter(id -> !keepIds.contains(id)).toList();
        if (!toDelete.isEmpty()) slaEscalationStepRepository.deleteAllById(toDelete);
    }

    private SlaPolicyDto toDto(SlaPolicyEntity entity) {
        SlaPolicyDto dto = new SlaPolicyDto();
        dto.setId(entity.getId());
        dto.setPriority(entity.getPriority());
        dto.setTemplateId(entity.getTemplateId());
        dto.setFirstResponseMinutes(entity.getFirstResponseMinutes());
        dto.setResolutionMinutes(entity.getResolutionMinutes());
        dto.setBusinessHours(entity.isBusinessHours());
        dto.setIsActive(entity.getIsActive());
        dto.setSteps(slaEscalationStepRepository.findBySlaPolicyIdOrderByStepOrderAsc(entity.getId())
                .stream().map(this::toStepDto).toList());
        return dto;
    }

    private SlaEscalationStepDto toStepDto(SlaEscalationStepEntity entity) {
        SlaEscalationStepDto dto = new SlaEscalationStepDto();
        dto.setId(entity.getId());
        dto.setStepOrder(entity.getStepOrder());
        dto.setTriggerType(entity.getTriggerType());
        dto.setTriggerValue(entity.getTriggerValue());
        dto.setActionType(entity.getActionType());
        dto.setTargetUserId(entity.getTargetUserId());
        dto.setTargetGroupId(entity.getTargetGroupId());
        dto.setWebhookUrl(entity.getWebhookUrl());
        return dto;
    }
}
