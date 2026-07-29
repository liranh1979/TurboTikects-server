package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.SaveActionItemLibraryRequestDto;
import com.turbotikects.turbotikectsserver.entitys.ActionItemLibraryEntity;
import com.turbotikects.turbotikectsserver.repositorys.ActionItemLibraryRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Reusable, cross-template catalog of workflow action items — see WorkflowDesignerModal's "Add
// from Library" picker (copies an entry's type/typeConfig into a fresh node, independent of this
// row afterward) and AiWorkflowBuilderPage (now saves its drafts here instead of into one template).
@Service
public class ActionItemLibraryService {

    private static final Set<String> VALID_TYPES = Set.of("task", "external_api", "mcp_tool");
    private static final Set<String> VALID_STATUSES = Set.of("draft", "complete");
    private static final String[][] SECRET_FIELD_PAIRS = {{"token", "tokenEnc"}, {"username", "usernameEnc"}, {"password", "passwordEnc"}};

    private final ActionItemLibraryRepository repo;
    private final AesEncryptionUtils aes;

    public ActionItemLibraryService(ActionItemLibraryRepository repo, AesEncryptionUtils aes) {
        this.repo = repo;
        this.aes = aes;
    }

    // status is an independent filter from type — either, both, or neither may be given. Callers
    // not yet updated to send status (e.g. ActionItemLibraryPage's general list, AiWorkflowBuilderPage's
    // own resume list) omit it and keep seeing every status, matching pre-draft-support behavior.
    public List<ActionItemLibraryEntity> getAll(String type, String status) {
        boolean hasType = type != null && !type.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        List<ActionItemLibraryEntity> entities;
        if (hasType && hasStatus) entities = repo.findByTypeAndStatusOrderByUpdatedAtDesc(type, status);
        else if (hasStatus) entities = repo.findByStatusOrderByUpdatedAtDesc(status);
        else if (hasType) entities = repo.findByTypeOrderByUpdatedAtDesc(type);
        else entities = repo.findAllByOrderByUpdatedAtDesc();
        return entities.stream().map(this::maskedCopy).toList();
    }

    @Transactional
    public ActionItemLibraryEntity create(SaveActionItemLibraryRequestDto req) {
        validate(req);
        ActionItemLibraryEntity entity = new ActionItemLibraryEntity();
        apply(entity, req, null);
        // Omitted status on create means "complete" — back-compat for manual saves that never
        // send it (a manual save is always a single, complete step, never a wizard draft).
        entity.setStatus(VALID_STATUSES.contains(req.getStatus()) ? req.getStatus() : "complete");
        return maskedCopy(repo.save(entity));
    }

    @Transactional
    public ActionItemLibraryEntity update(Long id, SaveActionItemLibraryRequestDto req) {
        ActionItemLibraryEntity entity = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        validate(req);
        apply(entity, req, entity.getTypeConfig());
        // Omitted status on update means "leave it as-is" — an unrelated field-tweak PUT must
        // never silently flip a draft to complete (or vice versa) just because it didn't mention status.
        if (VALID_STATUSES.contains(req.getStatus())) {
            entity.setStatus(req.getStatus());
        }
        return maskedCopy(repo.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        repo.deleteById(id);
    }

    private void validate(SaveActionItemLibraryRequestDto req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (!VALID_TYPES.contains(req.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be one of " + VALID_TYPES);
        }
        if (req.getStatus() != null && !req.getStatus().isBlank() && !VALID_STATUSES.contains(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be one of " + VALID_STATUSES);
        }
    }

    private void apply(ActionItemLibraryEntity entity, SaveActionItemLibraryRequestDto req, Map<String, Object> oldTypeConfig) {
        entity.setName(req.getName().trim());
        entity.setType(req.getType());
        Map<String, Object> typeConfig = req.getTypeConfig();
        encryptAndCarryForwardSecrets(typeConfig, oldTypeConfig);
        entity.setTypeConfig(typeConfig);
        entity.setSource("ai".equals(req.getSource()) ? "ai" : "manual");
    }

    /**
     * A real gap found live: unlike TemplateService (which encrypts/masks/carries-forward every
     * auth secret in a template's layout), this service persisted a call's auth.token/username/
     * password VERBATIM — a real API key typed in here (e.g. via the AI Workflow Builder, or
     * ExternalApiCallsEditor's own AUTH section) sat in the DB as plaintext forever, and reads
     * back never masked it into a hasToken-style flag either (so the frontend's SecretInput could
     * never even show "configured" for a library entry). Mirrors TemplateService's approach at a
     * smaller scale: one auth slot per external_api call (keyed by the call's own id) or one
     * shared slot for the whole mcp_tool node's typeConfig.
     */
    @SuppressWarnings("unchecked")
    private void encryptAndCarryForwardSecrets(Map<String, Object> typeConfig, Map<String, Object> oldTypeConfig) {
        if (typeConfig == null) return;
        Map<String, Map<String, Object>> oldAuthByCallId = new HashMap<>();
        if (oldTypeConfig != null && oldTypeConfig.get("calls") instanceof List<?> oldCalls) {
            for (Object c : oldCalls) {
                if (c instanceof Map<?, ?> call && call.get("id") instanceof String cid && call.get("auth") instanceof Map) {
                    oldAuthByCallId.put(cid, (Map<String, Object>) call.get("auth"));
                }
            }
        }
        if (typeConfig.get("calls") instanceof List<?> calls) {
            for (Object c : calls) {
                if (!(c instanceof Map)) continue;
                Map<String, Object> call = (Map<String, Object>) c;
                if (!(call.get("auth") instanceof Map)) continue;
                encryptAndCarrySecretFields((Map<String, Object>) call.get("auth"), oldAuthByCallId.get(call.get("id")));
            }
        }
        if (typeConfig.get("auth") instanceof Map) {
            Map<String, Object> oldAuth = oldTypeConfig != null && oldTypeConfig.get("auth") instanceof Map
                    ? (Map<String, Object>) oldTypeConfig.get("auth") : null;
            encryptAndCarrySecretFields((Map<String, Object>) typeConfig.get("auth"), oldAuth);
        }
    }

    private void encryptAndCarrySecretFields(Map<String, Object> auth, Map<String, Object> oldAuth) {
        // These are only ever a read-response artifact (see maskAuthSlot below) — if the frontend
        // echoes them back verbatim in a save payload (e.g. because it spread a previously-fetched
        // masked auth object), they must never actually be persisted.
        auth.remove("hasToken"); auth.remove("hasUsername"); auth.remove("hasPassword");
        for (String[] pair : SECRET_FIELD_PAIRS) {
            String plainKey = pair[0], encKey = pair[1];
            if (auth.containsKey(plainKey)) {
                Object plain = auth.remove(plainKey);
                if (plain instanceof String s && !s.isBlank()) {
                    auth.put(encKey, aes.encrypt(s));
                } else {
                    auth.remove(encKey); // explicit clear (blank/null value)
                }
            } else if (oldAuth != null && oldAuth.get(encKey) instanceof String s && !s.isBlank()) {
                auth.put(encKey, s); // untouched this save — carry the previously-encrypted value forward
            }
        }
    }

    /**
     * Never returns ciphertext to any client — replaces each auth slot's *Enc fields with
     * "has"-prefixed booleans, the same defense-in-depth TemplateService.maskWorkflowSecrets uses.
     * Deep-copies typeConfig first (via Jackson, matching that same method's approach) so the
     * returned object never shares state with — and can never accidentally flush back over —
     * whatever's actually persisted.
     */
    @SuppressWarnings("unchecked")
    private ActionItemLibraryEntity maskedCopy(ActionItemLibraryEntity source) {
        ActionItemLibraryEntity copy = new ActionItemLibraryEntity();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setType(source.getType());
        copy.setSource(source.getSource());
        copy.setStatus(source.getStatus());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        Map<String, Object> typeConfig = source.getTypeConfig();
        if (typeConfig == null) return copy;
        Map<String, Object> masked = new ObjectMapper().convertValue(typeConfig, new TypeReference<Map<String, Object>>() {});
        if (masked.get("calls") instanceof List<?> calls) {
            for (Object c : calls) {
                if (c instanceof Map<?, ?> call && call.get("auth") instanceof Map) {
                    maskAuthSlot((Map<String, Object>) call.get("auth"));
                }
            }
        }
        if (masked.get("auth") instanceof Map) {
            maskAuthSlot((Map<String, Object>) masked.get("auth"));
        }
        copy.setTypeConfig(masked);
        return copy;
    }

    private void maskAuthSlot(Map<String, Object> auth) {
        maskSecretField(auth, "tokenEnc", "hasToken");
        maskSecretField(auth, "usernameEnc", "hasUsername");
        maskSecretField(auth, "passwordEnc", "hasPassword");
    }

    private void maskSecretField(Map<String, Object> auth, String encKey, String hasFlagKey) {
        Object enc = auth.remove(encKey);
        auth.put(hasFlagKey, enc instanceof String s && !s.isBlank());
    }
}
