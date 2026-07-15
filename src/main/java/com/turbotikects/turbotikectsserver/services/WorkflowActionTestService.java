package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.WorkflowActionTestRequestDto;
import com.turbotikects.turbotikectsserver.dto.WorkflowActionTestResult;
import com.turbotikects.turbotikectsserver.repositorys.TemplateVersionRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * FEAT-06 Phase 7 — "test this call now". Orchestrates a live dry run of an external_api/mcp_tool
 * draft straight from the Workflow Designer, before the admin has saved anything, by delegating to
 * the exact same ExternalApiActionExecutor/McpActionExecutor.testRun methods real item activation
 * uses (see those classes' own javadoc for why that reuse matters).
 *
 * This class's own job is narrower: resolve which credential to actually use. The Designer never
 * holds a saved node's real ciphertext (TemplateService.maskWorkflowSecrets strips it on every
 * GET) — so testing a call whose secret the admin *isn't* actively retyping this session means
 * falling back to the currently-persisted encrypted value for that exact node (by templateId +
 * nodeId), while a freshly-typed plaintext value always wins and gets encrypted here the same way
 * TemplateService would on a real save, so the executors' unchanged decrypt-then-use logic works
 * identically in both the test and the real path.
 */
@Service
public class WorkflowActionTestService {

    private final TemplateVersionRepository versionRepo;
    private final AesEncryptionUtils aes;
    private final ExternalApiActionExecutor externalApiActionExecutor;
    private final McpActionExecutor mcpActionExecutor;

    public WorkflowActionTestService(TemplateVersionRepository versionRepo, AesEncryptionUtils aes,
                                      ExternalApiActionExecutor externalApiActionExecutor,
                                      McpActionExecutor mcpActionExecutor) {
        this.versionRepo = versionRepo;
        this.aes = aes;
        this.externalApiActionExecutor = externalApiActionExecutor;
        this.mcpActionExecutor = mcpActionExecutor;
    }

    @SuppressWarnings("unchecked")
    public WorkflowActionTestResult test(WorkflowActionTestRequestDto dto) {
        Map<String, Object> typeConfig = dto.getTypeConfig() != null
                ? deepCopy(dto.getTypeConfig()) : new LinkedHashMap<>();
        Map<String, String> sampleFields = dto.getSampleTicketFields() != null ? dto.getSampleTicketFields() : Map.of();
        Map<String, Object> savedTypeConfig = (dto.getTemplateId() != null && dto.getNodeId() != null)
                ? findSavedNodeTypeConfig(dto.getTemplateId(), dto.getNodeId()) : null;

        if ("mcp_tool".equals(dto.getType())) {
            String bearerToken = resolveMcpBearerToken(typeConfig, savedTypeConfig);
            return mcpActionExecutor.testRun(typeConfig, bearerToken, sampleFields);
        }
        if ("external_api".equals(dto.getType())) {
            resolveExternalApiCallAuths(typeConfig, savedTypeConfig);
            return externalApiActionExecutor.testRun(typeConfig, sampleFields);
        }
        return WorkflowActionTestResult.failure("Unsupported action type: " + dto.getType(), List.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> m) {
        // Shallow-in-structure copies only what this class mutates (auth blocks) — the request DTO
        // is a freshly-deserialized object graph anyway (not JPA-managed), so this is purely
        // defensive against surprising the caller by mutating its input.
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .convertValue(m, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findSavedNodeTypeConfig(Long templateId, String nodeId) {
        return versionRepo.findByTemplateIdAndIsCurrentTrue(templateId).map(v -> {
            Map<String, Object> layout = v.getLayout();
            if (layout == null || !(layout.get("tabs") instanceof List)) return null;
            for (Object tabObj : (List<?>) layout.get("tabs")) {
                if (!(tabObj instanceof Map)) continue;
                Object fieldsObj = ((Map<String, Object>) tabObj).get("fields");
                if (!(fieldsObj instanceof List)) continue;
                for (Object fieldObj : (List<?>) fieldsObj) {
                    if (!(fieldObj instanceof Map)) continue;
                    Map<String, Object> field = (Map<String, Object>) fieldObj;
                    if (!"workflow".equals(field.get("fieldType"))) continue;
                    Object fcObj = field.get("fieldConfig");
                    if (!(fcObj instanceof Map)) continue;
                    Object nodesObj = ((Map<String, Object>) fcObj).get("nodes");
                    if (!(nodesObj instanceof List)) continue;
                    for (Object nodeObj : (List<?>) nodesObj) {
                        if (!(nodeObj instanceof Map)) continue;
                        Map<String, Object> node = (Map<String, Object>) nodeObj;
                        if (nodeId.equals(node.get("id")) && node.get("typeConfig") instanceof Map) {
                            return (Map<String, Object>) node.get("typeConfig");
                        }
                    }
                }
            }
            return null;
        }).orElse(null);
    }

    private String resolveMcpBearerToken(Map<String, Object> draftTypeConfig, Map<String, Object> savedTypeConfig) {
        Map<String, Object> auth = asMap(draftTypeConfig.get("auth"));
        if (!"bearer".equals(auth.get("type"))) return null;
        if (auth.get("token") instanceof String s && !s.isBlank()) return s;
        if (savedTypeConfig != null) {
            Map<String, Object> savedAuth = asMap(savedTypeConfig.get("auth"));
            if (savedAuth.get("tokenEnc") instanceof String s && !s.isBlank()) {
                try { return aes.decrypt(s); } catch (Exception ignored) { /* treated as no token */ }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resolveExternalApiCallAuths(Map<String, Object> draftTypeConfig, Map<String, Object> savedTypeConfig) {
        Map<String, Map<String, Object>> savedAuthByCallId = new HashMap<>();
        if (savedTypeConfig != null && savedTypeConfig.get("calls") instanceof List<?> savedCalls) {
            for (Object c : savedCalls) {
                if (c instanceof Map<?, ?> call && call.get("id") instanceof String cid && call.get("auth") instanceof Map) {
                    savedAuthByCallId.put(cid, (Map<String, Object>) call.get("auth"));
                }
            }
        }
        if (!(draftTypeConfig.get("calls") instanceof List<?> calls)) return;
        for (Object cObj : calls) {
            if (!(cObj instanceof Map)) continue;
            Map<String, Object> call = (Map<String, Object>) cObj;
            if (!(call.get("auth") instanceof Map)) continue;
            Map<String, Object> auth = (Map<String, Object>) call.get("auth");
            Map<String, Object> savedAuth = savedAuthByCallId.get(call.get("id"));
            resolveSecretField(auth, "token", "tokenEnc", savedAuth);
            resolveSecretField(auth, "username", "usernameEnc", savedAuth);
            resolveSecretField(auth, "password", "passwordEnc", savedAuth);
        }
    }

    private void resolveSecretField(Map<String, Object> auth, String plainKey, String encKey, Map<String, Object> savedAuth) {
        Object plain = auth.get(plainKey);
        if (plain instanceof String s && !s.isBlank()) {
            auth.put(encKey, aes.encrypt(s));
            return;
        }
        if (savedAuth != null && savedAuth.get(encKey) instanceof String s && !s.isBlank()) {
            auth.put(encKey, s);
        }
    }

    private Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }
}
