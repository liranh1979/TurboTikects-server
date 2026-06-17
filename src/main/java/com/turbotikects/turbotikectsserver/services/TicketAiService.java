package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.TicketAiAnalyzeRequestDto;
import com.turbotikects.turbotikectsserver.dto.TicketAiAnalyzeResponseDto;
import com.turbotikects.turbotikectsserver.dto.llm.LlmStructure;
import com.turbotikects.turbotikectsserver.entitys.AiSettingsEntity;
import com.turbotikects.turbotikectsserver.entitys.DynamicTranslationsEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateEntity;
import com.turbotikects.turbotikectsserver.entitys.TemplateVersionEntity;
import com.turbotikects.turbotikectsserver.entitys.TicketLabelEntity;
import com.turbotikects.turbotikectsserver.repositorys.DynamicTranslationsRepository;
import com.turbotikects.turbotikectsserver.repositorys.TemplateRepository;
import com.turbotikects.turbotikectsserver.repositorys.TemplateVersionRepository;
import com.turbotikects.turbotikectsserver.repositorys.TicketLabelRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TicketAiService {

    private final AiSettingsService aiSettingsService;
    private final TemplateRepository templateRepo;
    private final TemplateVersionRepository versionRepo;
    private final TicketLabelRepository labelRepo;
    private final DynamicTranslationsRepository translationsRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public TicketAiService(AiSettingsService aiSettingsService,
                           TemplateRepository templateRepo,
                           TemplateVersionRepository versionRepo,
                           TicketLabelRepository labelRepo,
                           DynamicTranslationsRepository translationsRepo) {
        this.aiSettingsService = aiSettingsService;
        this.templateRepo = templateRepo;
        this.versionRepo = versionRepo;
        this.labelRepo = labelRepo;
        this.translationsRepo = translationsRepo;
    }

    public TicketAiAnalyzeResponseDto analyzeAndSuggest(String problem)
            throws IOException, URISyntaxException, InterruptedException {

        AiSettingsEntity aiSettings = aiSettingsService.getActiveAi();
        if (aiSettings == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No AI configured");
        }

        // Build template inventory
        List<TemplateEntity> templates = templateRepo.findAll();
        List<Map<String, Object>> templateInventory = new ArrayList<>();
        for (TemplateEntity t : templates) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", t.getId());
            entry.put("name", t.getName());
            entry.put("description", t.getDescription());
            entry.put("aiPurpose", t.getAiPurpose());
            Optional<TemplateVersionEntity> version = versionRepo.findByTemplateIdAndIsCurrentTrue(t.getId());
            version.ifPresent(v -> {
                entry.put("versionId", v.getId());
                entry.put("versionNumber", v.getVersionNumber());
                entry.put("layout", v.getLayout());
            });
            templateInventory.add(entry);
        }

        // Build label inventory with English translations
        List<TicketLabelEntity> labels = labelRepo.findAll();
        Optional<List<DynamicTranslationsEntity>> labelTranslationsOpt =
                translationsRepo.findAllByLangCodeAndType("en", "label");
        Map<String, String> labelTranslationMap = new LinkedHashMap<>();
        labelTranslationsOpt.ifPresent(list ->
                list.forEach(t -> labelTranslationMap.put(t.getTranslationKey(), t.getTranslatedText())));

        List<Map<String, Object>> labelInventory = new ArrayList<>();
        for (TicketLabelEntity label : labels) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", label.getId());
            entry.put("key", label.getLabelKey());
            entry.put("color", label.getColor());
            entry.put("displayName", labelTranslationMap.getOrDefault(label.getLabelKey(), label.getLabelKey()));
            labelInventory.add(entry);
        }

        String templatesJson = mapper.writeValueAsString(templateInventory);
        String labelsJson = mapper.writeValueAsString(labelInventory);

        LlmStructure system = new LlmStructure();
        system.setRole("system");
        system.setContent("You are a ticket routing assistant. Given a problem description and available " +
                "templates with their fields, pick the best template match and suggest pre-filled field values. " +
                "Return ONLY valid JSON, no markdown, no code fences.");

        LlmStructure user = new LlmStructure();
        user.setRole("user");
        user.setContent("Templates:\n" + templatesJson +
                "\n\nAvailable labels:\n" + labelsJson +
                "\n\nProblem description:\n" + problem +
                "\n\nReturn JSON: {\"matchedTemplateId\": N, \"confidence\": 0-100, " +
                "\"suggestedFields\": {\"fieldKey\": \"value\"}, " +
                "\"suggestedLabelIds\": [N], \"suggestedLabelKeys\": [\"key\"], \"reasoning\": \"...\"}");

        String raw = aiSettingsService.sendLlmRequest(aiSettings, List.of(system, user));
        String cleaned = raw.replaceAll("(?s)```[a-zA-Z]*\\n?", "").replace("```", "").trim();

        Map<String, Object> parsed = mapper.readValue(cleaned, new TypeReference<>() {});

        TicketAiAnalyzeResponseDto response = new TicketAiAnalyzeResponseDto();
        if (parsed.get("matchedTemplateId") != null) {
            response.setMatchedTemplateId(((Number) parsed.get("matchedTemplateId")).longValue());
        }
        if (parsed.get("confidence") != null) {
            response.setConfidence(((Number) parsed.get("confidence")).intValue());
        }
        if (parsed.get("suggestedFields") instanceof Map<?, ?> sf) {
            Map<String, Object> fields = new LinkedHashMap<>();
            sf.forEach((k, v) -> fields.put(String.valueOf(k), v));
            response.setSuggestedFields(fields);
        }
        if (parsed.get("suggestedLabelIds") instanceof List<?> ids) {
            List<Long> labelIds = new ArrayList<>();
            ids.forEach(i -> labelIds.add(((Number) i).longValue()));
            response.setSuggestedLabelIds(labelIds);
        }
        if (parsed.get("suggestedLabelKeys") instanceof List<?> keys) {
            List<String> labelKeys = new ArrayList<>();
            keys.forEach(k -> labelKeys.add(String.valueOf(k)));
            response.setSuggestedLabelKeys(labelKeys);
        }
        if (parsed.get("reasoning") != null) {
            response.setReasoning(String.valueOf(parsed.get("reasoning")));
        }

        return response;
    }

    public Long getDefaultTemplateId() {
        return templateRepo.findByIsDefaultTrue()
                .map(TemplateEntity::getId)
                .orElseGet(() -> {
                    List<TemplateEntity> all = templateRepo.findAll();
                    return all.isEmpty() ? null : all.get(0).getId();
                });
    }

    public Long getCurrentVersionId(Long templateId) {
        if (templateId == null) return null;
        return versionRepo.findByTemplateIdAndIsCurrentTrue(templateId)
                .map(v -> v.getId())
                .orElse(null);
    }
}
