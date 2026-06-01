package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.entitys.AzureConfigEntity;
import com.turbotikects.turbotikectsserver.entitys.AzureFieldMappingEntity;
import com.turbotikects.turbotikectsserver.repositorys.AzureConfigRepository;
import com.turbotikects.turbotikectsserver.repositorys.AzureFieldMappingRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AzureService {

    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";
    private static final String TOKEN_ENDPOINT = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";

    @PersistenceContext
    private EntityManager entityManager;

    private final AzureConfigRepository configRepository;
    private final AzureFieldMappingRepository mappingRepository;
    private final AesEncryptionUtils aes;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AzureService(AzureConfigRepository configRepository,
                        AzureFieldMappingRepository mappingRepository,
                        AesEncryptionUtils aes) {
        this.configRepository = configRepository;
        this.mappingRepository = mappingRepository;
        this.aes = aes;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public List<AzureConfigDto> getAllConfigs() {
        return configRepository.findAll().stream().map(this::toDto).toList();
    }

    public AzureConfigDto getConfig(Long id) {
        return toDto(findOrThrow(id));
    }

    public AzureConfigDto createConfig(AzureConfigDto dto) {
        AzureConfigEntity entity = new AzureConfigEntity();
        applyDto(dto, entity, true);
        return toDto(configRepository.save(entity));
    }

    public AzureConfigDto updateConfig(Long id, AzureConfigDto dto) {
        AzureConfigEntity entity = findOrThrow(id);
        boolean hasNewSecret = dto.getClientSecret() != null && !dto.getClientSecret().isBlank();
        applyDto(dto, entity, hasNewSecret);
        return toDto(configRepository.save(entity));
    }

    public void deleteConfig(Long id) {
        configRepository.deleteById(id);
    }

    public AzureConfigDto setActive(Long id, boolean active) {
        AzureConfigEntity entity = findOrThrow(id);
        entity.setActive(active);
        return toDto(configRepository.save(entity));
    }

    // ── Token ─────────────────────────────────────────────────────────────────

    public String getAccessToken(AzureConfigEntity config) throws Exception {
        String secret = aes.decrypt(config.getClientSecret());
        String body = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(config.getClientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode("https://graph.microsoft.com/.default", StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(String.format(TOKEN_ENDPOINT, config.getTenantId())))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Token request failed (" + response.statusCode() + "): " + response.body());
        }
        JsonNode json = objectMapper.readTree(response.body());
        return json.get("access_token").asText();
    }

    public String getAccessTokenRaw(String tenantId, String clientId, String clientSecret) throws Exception {
        String body = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode("https://graph.microsoft.com/.default", StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(String.format(TOKEN_ENDPOINT, tenantId)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Token request failed (" + response.statusCode() + "): " + response.body());
        }
        JsonNode json = objectMapper.readTree(response.body());
        return json.get("access_token").asText();
    }

    // ── Test connection ───────────────────────────────────────────────────────

    public AzureTestResultDto testConnection(Long id) {
        AzureConfigEntity entity = findOrThrow(id);
        try {
            String token = getAccessToken(entity);
            return doTestWithToken(token);
        } catch (Exception e) {
            return AzureTestResultDto.fail(e.getMessage());
        }
    }

    public AzureTestResultDto testConnectionRaw(AzureConfigDto dto) {
        try {
            String token = getAccessTokenRaw(dto.getTenantId(), dto.getClientId(), dto.getClientSecret());
            return doTestWithToken(token);
        } catch (Exception e) {
            return AzureTestResultDto.fail(e.getMessage());
        }
    }

    private AzureTestResultDto doTestWithToken(String token) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPH_BASE + "/organization"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                return AzureTestResultDto.ok();
            }
            return AzureTestResultDto.fail("Graph API returned " + res.statusCode());
        } catch (Exception e) {
            return AzureTestResultDto.fail(e.getMessage());
        }
    }

    // ── Sample data ───────────────────────────────────────────────────────────

    public AzureSampleDto fetchSampleUser(Long id) {
        return fetchSample(findOrThrow(id), "users");
    }

    public AzureSampleDto fetchSampleGroup(Long id) {
        return fetchSample(findOrThrow(id), "groups");
    }

    private AzureSampleDto fetchSample(AzureConfigEntity entity, String resource) {
        AzureSampleDto dto = new AzureSampleDto();
        dto.setAttributes(Map.of());
        try {
            String token = getAccessToken(entity);
            String url = GRAPH_BASE + "/" + resource + "?$top=1";

            // Apply OData filter if configured
            String filter = "users".equals(resource) ? entity.getUserFilter() : entity.getGroupFilter();
            if (filter != null && !filter.isBlank()) {
                url += "&$filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8);
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                dto.setError("Graph API returned " + res.statusCode() + ": " + res.body());
                return dto;
            }
            JsonNode root = objectMapper.readTree(res.body());
            JsonNode values = root.get("value");
            if (values == null || !values.isArray() || values.isEmpty()) {
                dto.setError("No " + resource + " found in Azure AD tenant.");
                return dto;
            }
            Map<String, String> attrs = new LinkedHashMap<>();
            values.get(0).fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                if (!v.isNull() && !v.isObject() && !v.isArray()) {
                    attrs.put(e.getKey(), v.asText());
                }
            });
            dto.setAttributes(attrs);
        } catch (Exception e) {
            dto.setError(e.getMessage());
        }
        return dto;
    }

    // ── Field mappings ────────────────────────────────────────────────────────

    public List<AzureFieldMappingDto> getMappings(Long configId, String entityType) {
        return mappingRepository.findByAzureConfigIdAndEntityType(configId, entityType)
                .stream().map(this::toMappingDto).toList();
    }

    public Map<String, List<AzureFieldMappingDto>> getAllMappings(Long configId) {
        List<AzureFieldMappingEntity> all = mappingRepository.findByAzureConfigId(configId);
        List<AzureFieldMappingDto> users = new ArrayList<>();
        List<AzureFieldMappingDto> groups = new ArrayList<>();
        for (AzureFieldMappingEntity e : all) {
            if ("group".equals(e.getEntityType())) {
                groups.add(toMappingDto(e));
            } else {
                users.add(toMappingDto(e));
            }
        }
        return Map.of("user", users, "group", groups);
    }

    @Transactional
    public List<AzureFieldMappingDto> saveMappings(Long configId, SaveAzureMappingsRequestDto dto) {
        String entityType = dto.getEntityType();
        mappingRepository.deleteByAzureConfigIdAndEntityType(configId, entityType);
        entityManager.flush();  // send DELETE to DB before INSERT batch runs
        entityManager.clear();  // evict stale entities from first-level cache

        // Use LinkedHashMap to deduplicate by azureAttribute (last mapping wins)
        Map<String, AzureFieldMappingEntity> byAttr = new LinkedHashMap<>();
        if (dto.getMappings() != null) {
            for (AzureFieldMappingDto m : dto.getMappings()) {
                if (m.getSystemFieldKey() == null || m.getSystemFieldKey().isBlank()) continue;
                if (m.getAzureAttribute() == null || m.getAzureAttribute().isBlank()) continue;
                AzureFieldMappingEntity entity = new AzureFieldMappingEntity();
                entity.setAzureConfigId(configId);
                entity.setEntityType(entityType);
                entity.setAzureAttribute(m.getAzureAttribute());
                entity.setSystemFieldKey(m.getSystemFieldKey());
                byAttr.put(m.getAzureAttribute(), entity);
            }
        }
        return mappingRepository.saveAll(new ArrayList<>(byAttr.values()))
                .stream().map(this::toMappingDto).toList();
    }

    // ── Authentication (ROPC) ─────────────────────────────────────────────────

    public boolean authenticateUser(String upn, String password, AzureConfigEntity config) {
        try {
            String secret = aes.decrypt(config.getClientSecret());
            String body = "grant_type=password"
                    + "&client_id=" + URLEncoder.encode(config.getClientId(), StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                    + "&username=" + URLEncoder.encode(upn, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode("https://graph.microsoft.com/.default", StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(TOKEN_ENDPOINT, config.getTenantId())))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<Map<String, String>> findUserInAzure(String username, AzureConfigEntity config) {
        try {
            String token = getAccessToken(config);
            String escaped = username.replace("'", "''");
            String filter = "startswith(userPrincipalName,'" + escaped + "@') or userPrincipalName eq '" + escaped + "'";
            String url = GRAPH_BASE + "/users?$filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8)
                    + "&$select=id,displayName,userPrincipalName,mail,givenName,surname,jobTitle,department,mobilePhone,officeLocation&$top=1";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return Optional.empty();

            JsonNode root = objectMapper.readTree(res.body());
            JsonNode values = root.get("value");
            if (values == null || !values.isArray() || values.isEmpty()) return Optional.empty();

            Map<String, String> attrs = new LinkedHashMap<>();
            values.get(0).fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                if (!v.isNull() && !v.isObject() && !v.isArray()) {
                    attrs.put(e.getKey(), v.asText());
                }
            });
            return Optional.of(attrs);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AzureConfigEntity findOrThrow(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Azure config not found: " + id));
    }

    private void applyDto(AzureConfigDto dto, AzureConfigEntity entity, boolean encryptSecret) {
        if (dto.getDisplayName() != null) entity.setDisplayName(dto.getDisplayName().trim());
        if (dto.getTenantId() != null && !dto.getTenantId().isBlank()) entity.setTenantId(dto.getTenantId().trim());
        if (dto.getClientId() != null && !dto.getClientId().isBlank()) entity.setClientId(dto.getClientId().trim());
        if (encryptSecret && dto.getClientSecret() != null && !dto.getClientSecret().isBlank()) {
            entity.setClientSecret(aes.encrypt(dto.getClientSecret()));
        }
        if (dto.getUserFilter() != null) entity.setUserFilter(dto.getUserFilter().isBlank() ? null : dto.getUserFilter().trim());
        if (dto.getGroupFilter() != null) entity.setGroupFilter(dto.getGroupFilter().isBlank() ? null : dto.getGroupFilter().trim());
    }

    private AzureConfigDto toDto(AzureConfigEntity entity) {
        AzureConfigDto dto = new AzureConfigDto();
        dto.setId(entity.getId());
        dto.setDisplayName(entity.getDisplayName());
        dto.setTenantId(entity.getTenantId());
        dto.setClientId(entity.getClientId());
        // clientSecret intentionally omitted from GET responses
        dto.setUserFilter(entity.getUserFilter());
        dto.setGroupFilter(entity.getGroupFilter());
        dto.setLastSyncedAt(entity.getLastSyncedAt());
        dto.setActive(entity.isActive());
        return dto;
    }

    private AzureFieldMappingDto toMappingDto(AzureFieldMappingEntity entity) {
        AzureFieldMappingDto dto = new AzureFieldMappingDto();
        dto.setId(entity.getId());
        dto.setAzureConfigId(entity.getAzureConfigId());
        dto.setEntityType(entity.getEntityType());
        dto.setAzureAttribute(entity.getAzureAttribute());
        dto.setSystemFieldKey(entity.getSystemFieldKey());
        return dto;
    }

    // ── Last sync update (called by AzureSyncService) ─────────────────────────

    public void updateLastSynced(Long configId, LocalDateTime at) {
        configRepository.findById(configId).ifPresent(cfg -> {
            cfg.setLastSyncedAt(at);
            configRepository.save(cfg);
        });
    }

    public void saveDeltaLinks(Long configId, String userDeltaLink, String groupDeltaLink) {
        configRepository.findById(configId).ifPresent(cfg -> {
            if (userDeltaLink != null) cfg.setUserDeltaLink(userDeltaLink);
            if (groupDeltaLink != null) cfg.setGroupDeltaLink(groupDeltaLink);
            configRepository.save(cfg);
        });
    }
}
