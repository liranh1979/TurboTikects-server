package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.FieldDefinitionsEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateVersionEntity;
import com.turbotikects.turbotikectsserver.repositorys.TemplateRepository;
import com.turbotikects.turbotikectsserver.repositorys.TemplateVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

@Service
public class TemplateService {

    private final TemplateRepository templateRepo;
    private final TemplateVersionRepository versionRepo;
    private final FieldDefinitionsService fieldDefinitionsService;
    private final AiSettingsService aiSettingsService;

    public TemplateService(TemplateRepository templateRepo,
                           TemplateVersionRepository versionRepo,
                           FieldDefinitionsService fieldDefinitionsService,
                           AiSettingsService aiSettingsService) {
        this.templateRepo = templateRepo;
        this.versionRepo = versionRepo;
        this.fieldDefinitionsService = fieldDefinitionsService;
        this.aiSettingsService = aiSettingsService;
    }

    public List<TemplateSummaryDto> getAll() {
        List<TemplateEntity> templates = templateRepo.findAll();
        List<TemplateSummaryDto> result = new ArrayList<>();
        for (TemplateEntity t : templates) {
            TemplateSummaryDto dto = new TemplateSummaryDto();
            dto.setId(t.getId());
            dto.setName(t.getName());
            dto.setDescription(t.getDescription());
            dto.setCreatedAt(t.getCreatedAt());
            dto.setUpdatedAt(t.getUpdatedAt());
            versionRepo.findByTemplateIdAndIsCurrentTrue(t.getId())
                    .ifPresent(v -> dto.setCurrentVersionNumber(v.getVersionNumber()));
            dto.setDefault(t.isDefault());
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public TemplateWithLayoutDto create(SaveLayoutRequestDto dto) {
        TemplateEntity template = new TemplateEntity();
        template.setName(dto.getName() != null ? dto.getName() : "New Template");
        template.setDescription(dto.getDescription());
        template.setAiPurpose(dto.getAiPurpose());
        template = templateRepo.save(template);

        TemplateVersionEntity version = new TemplateVersionEntity();
        version.setTemplateId(template.getId());
        version.setVersionNumber(1);
        version.setLayout(dto.getLayout() != null ? dto.getLayout() : buildDefaultLayout());
        version.setCurrent(true);
        version = versionRepo.save(version);

        return toWithLayoutDto(template, version);
    }

    public TemplateWithLayoutDto getWithLayout(Long id) {
        TemplateEntity template = templateRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        TemplateVersionEntity version = versionRepo.findByTemplateIdAndIsCurrentTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toWithLayoutDto(template, version);
    }

    @Transactional
    public TemplateWithLayoutDto saveLayout(Long id, SaveLayoutRequestDto dto) {
        TemplateEntity template = templateRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (dto.getName() != null) template.setName(dto.getName());
        if (dto.getDescription() != null) template.setDescription(dto.getDescription());
        if (dto.getAiPurpose() != null) template.setAiPurpose(dto.getAiPurpose());
        template = templateRepo.save(template);

        TemplateVersionEntity current = versionRepo.findByTemplateIdAndIsCurrentTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        current.setCurrent(false);
        versionRepo.save(current);

        TemplateVersionEntity newVersion = new TemplateVersionEntity();
        newVersion.setTemplateId(id);
        newVersion.setVersionNumber(current.getVersionNumber() + 1);
        newVersion.setLayout(dto.getLayout() != null ? dto.getLayout() : current.getLayout());
        newVersion.setCurrent(true);
        newVersion = versionRepo.save(newVersion);

        return toWithLayoutDto(template, newVersion);
    }

    @Transactional
    public TemplateSummaryDto setDefault(Long id) {
        TemplateEntity template = templateRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        templateRepo.clearAllDefaults();
        template.setDefault(true);
        template = templateRepo.save(template);
        TemplateSummaryDto dto = new TemplateSummaryDto();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setDefault(true);
        dto.setCreatedAt(template.getCreatedAt());
        dto.setUpdatedAt(template.getUpdatedAt());
        versionRepo.findByTemplateIdAndIsCurrentTrue(id)
                .ifPresent(v -> dto.setCurrentVersionNumber(v.getVersionNumber()));
        return dto;
    }

    public void delete(Long id) {
        if (!templateRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        templateRepo.deleteById(id);
    }

    public Map<String, Object> aiSuggestLayout(Long id, AiSuggestLayoutRequestDto dto)
            throws IOException, URISyntaxException, InterruptedException {

        if (!templateRepo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No active AI configuration");
        }

        List<FieldDefinitionsEntity> fields = fieldDefinitionsService.getCustomFields("ticket");
        Map<String, String> enTranslations = fieldDefinitionsService.getFieldTranslations("en", "ticket_fields");

        StringBuilder fieldList = new StringBuilder();
        for (FieldDefinitionsEntity f : fields) {
            String label = enTranslations.getOrDefault(f.getFieldKey(), f.getFieldKey());
            fieldList.append("- fieldKey: ").append(f.getFieldKey())
                    .append(", fieldType: ").append(f.getFieldType())
                    .append(", label: ").append(label)
                    .append(", isSystem: ").append(f.isSystem())
                    .append("\n");
        }

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("You are a ticket template designer. Given a list of available fields, " +
                "return a JSON array selecting and ordering fields that best match the admin's requirement. " +
                "Always include all system fields (isSystem: true). " +
                "Use only fieldKeys from the provided list. " +
                "Return ONLY a valid JSON array, no markdown, no explanation.");

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("Available fields:\n" + fieldList +
                "\nAdmin requirement: " + dto.getPrompt() +
                "\n\nReturn a JSON array of objects with this shape: " +
                "[{\"fieldKey\": \"...\", \"fieldType\": \"...\", \"isSystem\": true/false, " +
                "\"displayOrder\": N, \"defaultValue\": \"\", \"width\": \"full\"}]");

        String raw = aiSettingsService.sendLlmRequest(aiSettings, List.of(system, user));
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> suggestedFields = mapper.readValue(cleaned, new TypeReference<>() {});

        // Wrap in tabbed layout structure
        Map<String, Object> tab = new LinkedHashMap<>();
        tab.put("tabKey", "main");
        tab.put("label", "Main");
        tab.put("fields", suggestedFields);

        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("tabs", List.of(tab));
        return layout;
    }

    private Map<String, Object> buildDefaultLayout() {
        List<Map<String, Object>> fields = new ArrayList<>();
        addField(fields, "title",        "text",      true, 1, "",    "full",  null);
        addField(fields, "description",  "rich-text", true, 2, "",    "full",  null);
        addField(fields, "status",       "combobox",  true, 3, "new", "half",
                List.of("new", "open", "in_progress", "waiting", "resolved", "closed"));
        addField(fields, "request_user", "text",      true, 4, "",    "half",  null);
        addField(fields, "responsible",  "text",      true, 5, "",    "half",  null);
        addField(fields, "attachments",  "attachments", true, 6, "",  "full",  null);
        addField(fields, "labels",       "labels",      true, 7, "",  "full",  null);

        Map<String, Object> tab = new LinkedHashMap<>();
        tab.put("tabKey", "main");
        tab.put("label", "Main");
        tab.put("fields", fields);

        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("tabs", List.of(tab));
        return layout;
    }

    private void addField(List<Map<String, Object>> fields, String key, String type,
                          boolean system, int order, String defaultValue, String width,
                          List<String> fieldOptions) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldKey", key);
        field.put("fieldType", type);
        field.put("isSystem", system);
        field.put("displayOrder", order);
        field.put("defaultValue", defaultValue);
        field.put("width", width);
        if (fieldOptions != null && !fieldOptions.isEmpty()) {
            field.put("fieldOptions", fieldOptions);
        }
        fields.add(field);
    }

    private TemplateWithLayoutDto toWithLayoutDto(TemplateEntity t, TemplateVersionEntity v) {
        TemplateWithLayoutDto dto = new TemplateWithLayoutDto();
        dto.setId(t.getId());
        dto.setName(t.getName());
        dto.setDescription(t.getDescription());
        dto.setAiPurpose(t.getAiPurpose());
        dto.setCurrentVersionNumber(v.getVersionNumber());
        dto.setCurrentVersionId(v.getId());
        dto.setLayout(enrichLayoutWithAdminOnly(v.getLayout()));
        dto.setDefault(t.isDefault());
        dto.setCreatedAt(t.getCreatedAt());
        dto.setUpdatedAt(t.getUpdatedAt());
        return dto;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> enrichLayoutWithAdminOnly(Map<String, Object> layout) {
        if (layout == null) return null;

        Map<String, Boolean> adminOnlyMap = new HashMap<>();
        Map<String, List<String>> fieldOptionsMap = new HashMap<>();
        for (FieldDefinitionsEntity f : fieldDefinitionsService.getCustomFields("ticket")) {
            adminOnlyMap.put(f.getFieldKey(), f.isAdminOnly());
            if (f.getFieldOptions() != null && !f.getFieldOptions().isEmpty()) {
                fieldOptionsMap.put(f.getFieldKey(), f.getFieldOptions());
            }
        }

        Object tabs = layout.get("tabs");
        if (!(tabs instanceof List<?>)) return layout;

        List<Object> newTabs = new ArrayList<>();
        for (Object tabObj : (List<?>) tabs) {
            if (!(tabObj instanceof Map)) { newTabs.add(tabObj); continue; }
            Map<String, Object> tab = new LinkedHashMap<>((Map<String, Object>) tabObj);
            Object fields = tab.get("fields");
            if (fields instanceof List<?>) {
                List<Object> newFields = new ArrayList<>();
                for (Object fObj : (List<?>) fields) {
                    if (!(fObj instanceof Map)) { newFields.add(fObj); continue; }
                    Map<String, Object> field = new LinkedHashMap<>((Map<String, Object>) fObj);
                    String fKey = (String) field.get("fieldKey");
                    if (fKey != null) {
                        field.put("isAdminOnly", adminOnlyMap.getOrDefault(fKey, false));
                        if (fieldOptionsMap.containsKey(fKey)) {
                            field.put("fieldOptions", fieldOptionsMap.get(fKey));
                        }
                    }
                    newFields.add(field);
                }
                tab.put("fields", newFields);
            }
            newTabs.add(tab);
        }
        Map<String, Object> result = new LinkedHashMap<>(layout);
        result.put("tabs", newTabs);
        return result;
    }
}
