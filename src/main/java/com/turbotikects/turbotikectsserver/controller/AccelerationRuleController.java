package com.turbotikects.turbotikectsserver.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.AccelerationRuleDto;
import com.turbotikects.turbotikectsserver.dto.AccelerationTestRunResultDto;
import com.turbotikects.turbotikectsserver.dto.SaveAccelerationRuleDto;
import com.turbotikects.turbotikectsserver.entitys.AccelerationRuleEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketEntity;
import com.turbotikects.turbotikectsserver.dto.AvailableConditionFieldDto;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.repositorys.AccelerationRuleRepository;
import com.turbotikects.turbotikectsserver.repositorys.FieldDefinitionsRepository;
import com.turbotikects.turbotikectsserver.repositorys.GroupRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import com.turbotikects.turbotikectsserver.security.RequirePermission;
import com.turbotikects.turbotikectsserver.services.AccelerationGroovyEngine;
import com.turbotikects.turbotikectsserver.services.AccelerationRuleGeneratorService;
import com.turbotikects.turbotikectsserver.services.AccelerationSchedulerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/acceleration-rules")
@RequirePermission("TICKET_MANAGER")
public class AccelerationRuleController {

    private final AccelerationRuleRepository ruleRepo;
    private final TicketRepository ticketRepo;
    private final FieldDefinitionsRepository fieldDefinitionsRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final AccelerationRuleGeneratorService generatorService;
    private final AccelerationGroovyEngine groovyEngine;
    private final AccelerationSchedulerService schedulerService;
    private final ObjectMapper objectMapper;

    public AccelerationRuleController(AccelerationRuleRepository ruleRepo,
                                       TicketRepository ticketRepo,
                                       FieldDefinitionsRepository fieldDefinitionsRepository,
                                       UserRepository userRepository,
                                       GroupRepository groupRepository,
                                       AccelerationRuleGeneratorService generatorService,
                                       AccelerationGroovyEngine groovyEngine,
                                       AccelerationSchedulerService schedulerService,
                                       ObjectMapper objectMapper) {
        this.ruleRepo = ruleRepo;
        this.ticketRepo = ticketRepo;
        this.fieldDefinitionsRepository = fieldDefinitionsRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.generatorService = generatorService;
        this.groovyEngine = groovyEngine;
        this.schedulerService = schedulerService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<AccelerationRuleDto> getAll() {
        return ruleRepo.findAllByOrderByExecutionOrderAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<AccelerationRuleDto> create(@RequestBody SaveAccelerationRuleDto dto) {
        AccelerationRuleEntity entity = new AccelerationRuleEntity();
        applyDto(entity, dto);
        entity = ruleRepo.save(entity);
        compileIfScriptPresent(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(entity));
    }

    @PutMapping("/{id}")
    public AccelerationRuleDto update(@PathVariable Long id, @RequestBody SaveAccelerationRuleDto dto) {
        AccelerationRuleEntity entity = ruleRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        applyDto(entity, dto);
        entity = ruleRepo.save(entity);
        if (entity.getGeneratedScript() != null && !entity.getGeneratedScript().isBlank()) {
            compileIfScriptPresent(entity);
        } else {
            groovyEngine.evict(id);
        }
        return toDto(entity);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!ruleRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found");
        }
        groovyEngine.evict(id);
        ruleRepo.deleteById(id);
    }

    @GetMapping("/available-fields")
    public List<AvailableConditionFieldDto> getAvailableFields() {
        List<AvailableConditionFieldDto> fields = new java.util.ArrayList<>();

        // 9 hardcoded system fields
        fields.add(new AvailableConditionFieldDto("status", "Status", "combobox", false,
                List.of("new", "open", "in_progress", "waiting", "resolved", "closed")));
        fields.add(new AvailableConditionFieldDto("title", "Title", "text", false, null));
        fields.add(new AvailableConditionFieldDto("acceleration", "Acceleration", "number", false, null));
        fields.add(new AvailableConditionFieldDto("createdAt", "Created At", "date", false, null));
        fields.add(new AvailableConditionFieldDto("updatedAt", "Updated At", "date", false, null));
        fields.add(new AvailableConditionFieldDto("responsibleUserId", "Responsible User ID", "number", false, null));
        fields.add(new AvailableConditionFieldDto("responsibleGroupId", "Responsible Group ID", "number", false, null));
        fields.add(new AvailableConditionFieldDto("requestUserId", "Requestor User ID", "number", false, null));
        fields.add(new AvailableConditionFieldDto("companyId", "Company ID", "number", false, null));

        // Custom ticket fields from field_definitions
        for (FieldDefinitionsEntity f : fieldDefinitionsRepository
                .findByEntityTypeAndIsSystemFalseOrderByDisplayOrder("ticket")) {
            fields.add(new AvailableConditionFieldDto(
                    f.getFieldKey(),
                    formatFieldLabel(f.getFieldKey()),
                    f.getFieldType(),
                    true,
                    f.getFieldOptions()));
        }
        return fields;
    }

    @GetMapping("/assignable-users")
    public List<Map<String, Object>> getAssignableUsers() {
        return userRepository.findAssignableUsers().stream()
                .map(u -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", u.getRed_id());
                    m.put("displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/assignable-groups")
    public List<Map<String, Object>> getAssignableGroups() {
        return groupRepository.findAssignableGroups().stream()
                .map(g -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", g.getRefId());
                    m.put("displayName", g.getDisplayName());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private String formatFieldLabel(String key) {
        if (key == null || key.isBlank()) return key;
        String[] parts = key.split("[_\\-]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    @PostMapping("/generate")
    public Map<String, String> generate(@RequestBody Map<String, String> body) {
        String conditions = body.getOrDefault("triggerConditions", "[]");
        String actions = body.getOrDefault("actions", "[]");
        try {
            String script = generatorService.generate(conditions, actions);
            return Map.of("script", script);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id}/test-run")
    public AccelerationTestRunResultDto testRun(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> body) {
        AccelerationRuleEntity rule = ruleRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));

        if (rule.getGeneratedScript() == null || rule.getGeneratedScript().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rule has no generated script. Generate the script first.");
        }

        Long ticketId = toLong(body.get("ticketId"));
        TicketEntity ticket = ticketRepo.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        // Snapshot before
        Map<String, Object> before = snapshotTicket(ticket);

        // Compile on-the-fly for test (don't update cache unless already cached)
        if (!groovyEngine.isCached(id)) {
            try {
                groovyEngine.compile(id, rule.getGeneratedScript());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Script compilation failed: " + e.getMessage());
            }
        }

        // Execute — do NOT save
        AccelerationGroovyEngine.ExecutionResult result = groovyEngine.execute(id, ticket);
        if (result.error() != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Script execution error: " + result.error());
        }

        // Snapshot after and diff
        Map<String, Object> after = snapshotTicket(ticket);
        Map<String, Object> diff = computeDiff(before, after);

        AccelerationTestRunResultDto response = new AccelerationTestRunResultDto();
        response.setMutated(result.mutated());
        response.setDiff(diff);
        return response;
    }

    @PostMapping("/{id}/run-now")
    public Map<String, String> runNow(@PathVariable Long id) {
        AccelerationRuleEntity rule = ruleRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        if (rule.getGeneratedScript() == null || rule.getGeneratedScript().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule has no generated script.");
        }
        // Fire off in a background thread (same pattern as other async operations in this codebase)
        final Long ruleId = rule.getId();
        new Thread(() -> {
            try {
                AccelerationRuleEntity r = ruleRepo.findById(ruleId).orElse(null);
                if (r != null) schedulerService.runAllRules();
            } catch (Exception e) {
                log.error("run-now failed for rule {}", ruleId, e);
            }
        }, "acc-run-now-" + ruleId).start();
        return Map.of("status", "triggered");
    }

    private void applyDto(AccelerationRuleEntity entity, SaveAccelerationRuleDto dto) {
        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getExecutionOrder() != null) entity.setExecutionOrder(dto.getExecutionOrder());
        if (dto.getTriggerConditions() != null) entity.setTriggerConditions(dto.getTriggerConditions());
        if (dto.getActions() != null) entity.setActions(dto.getActions());
        if (dto.getGeneratedScript() != null) entity.setGeneratedScript(dto.getGeneratedScript());
        if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());
    }

    private void compileIfScriptPresent(AccelerationRuleEntity entity) {
        if (entity.getGeneratedScript() != null && !entity.getGeneratedScript().isBlank()) {
            try {
                groovyEngine.compile(entity.getId(), entity.getGeneratedScript());
            } catch (Exception e) {
                log.warn("Failed to compile script for rule {}: {}", entity.getId(), e.getMessage());
            }
        }
    }

    private Map<String, Object> snapshotTicket(TicketEntity ticket) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("acceleration", ticket.getAcceleration());
        snap.put("status", ticket.getStatus());
        snap.put("responsibleUserId", ticket.getResponsibleUserId());
        snap.put("responsibleGroupId", ticket.getResponsibleGroupId());
        return snap;
    }

    private Map<String, Object> computeDiff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> diff = new LinkedHashMap<>();
        for (String key : before.keySet()) {
            Object bVal = before.get(key);
            Object aVal = after.get(key);
            if (!Objects.equals(bVal, aVal)) {
                diff.put(key, Map.of("from", bVal != null ? bVal : "", "to", aVal != null ? aVal : ""));
            }
        }
        return diff;
    }

    private AccelerationRuleDto toDto(AccelerationRuleEntity e) {
        AccelerationRuleDto dto = new AccelerationRuleDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setDescription(e.getDescription());
        dto.setExecutionOrder(e.getExecutionOrder());
        dto.setTriggerConditions(e.getTriggerConditions());
        dto.setActions(e.getActions());
        dto.setGeneratedScript(e.getGeneratedScript());
        dto.setIsActive(e.getIsActive());
        dto.setLastError(e.getLastError());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}
