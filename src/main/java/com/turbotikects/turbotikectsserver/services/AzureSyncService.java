package com.turbotikects.turbotikectsserver.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import com.turbotikects.turbotikectsserver.utils.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AzureSyncService {

    private static final Logger log = LoggerFactory.getLogger(AzureSyncService.class);

    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";
    private static final String USER_SELECT = "id,displayName,userPrincipalName,mail,givenName,surname,jobTitle,department,mobilePhone,officeLocation,accountEnabled";
    private static final String GROUP_SELECT = "id,displayName,description,mail,mailEnabled,securityEnabled";

    private final AzureConfigRepository configRepository;
    private final AzureFieldMappingRepository mappingRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TaskProgressService taskProgressService;
    private final AzureService azureService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AzureSyncService(AzureConfigRepository configRepository,
                             AzureFieldMappingRepository mappingRepository,
                             UserRepository userRepository,
                             GroupRepository groupRepository,
                             GroupMemberRepository groupMemberRepository,
                             TaskProgressService taskProgressService,
                             AzureService azureService) {
        this.configRepository = configRepository;
        this.mappingRepository = mappingRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.taskProgressService = taskProgressService;
        this.azureService = azureService;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    public String startSync(Long configId, boolean full) {
        String taskId = taskProgressService.createTask("Azure AD Sync", 100);
        new Thread(() -> runSync(configId, taskId, full)).start();
        return taskId;
    }

    public void runSync(Long configId, String taskId, boolean full) {
        try {
            AzureConfigEntity config = configRepository.findById(configId)
                    .orElseThrow(() -> new RuntimeException("Azure config not found: " + configId));

            if (full) {
                log.info("Full sync requested for config {} — clearing delta links", configId);
                config.setUserDeltaLink(null);
                config.setGroupDeltaLink(null);
            }

            taskProgressService.updateProgress(taskId, 5, "Loading field mappings…");
            List<AzureFieldMappingEntity> userMappings =
                    mappingRepository.findByAzureConfigIdAndEntityType(configId, "user");
            List<AzureFieldMappingEntity> groupMappings =
                    mappingRepository.findByAzureConfigIdAndEntityType(configId, "group");

            taskProgressService.updateProgress(taskId, 10, "Getting access token…");
            String token = azureService.getAccessToken(config);

            taskProgressService.updateProgress(taskId, 15, full ? "Full sync — syncing all users…" : "Syncing users (delta)…");
            String newUserDeltaLink = syncUsers(config, token, userMappings, taskId);

            taskProgressService.updateProgress(taskId, 65, full ? "Full sync — syncing all groups + members…" : "Syncing groups (delta)…");
            String newGroupDeltaLink = syncGroups(config, token, groupMappings, taskId);

            azureService.saveDeltaLinks(configId, newUserDeltaLink, newGroupDeltaLink);
            azureService.updateLastSynced(configId, LocalDateTime.now());

            taskProgressService.completeTask(taskId, "Azure AD sync completed");

        } catch (Exception e) {
            taskProgressService.failTask(taskId, "Sync failed: " + e.getMessage());
        }
    }

    // ── User sync ─────────────────────────────────────────────────────────────

    private String syncUsers(AzureConfigEntity config, String token,
                             List<AzureFieldMappingEntity> mappings, String taskId) throws Exception {
        String deltaLink = config.getUserDeltaLink();
        String nextUrl = (deltaLink != null && !deltaLink.isBlank())
                ? deltaLink
                : GRAPH_BASE + "/users/delta?$select=" + USER_SELECT;

        String filterClause = config.getUserFilter();
        if (filterClause != null && !filterClause.isBlank() && deltaLink == null) {
            nextUrl += "&$filter=" + java.net.URLEncoder.encode(filterClause, java.nio.charset.StandardCharsets.UTF_8);
        }

        Set<String> foundIds = new HashSet<>();
        int processed = 0;
        String finalDeltaLink = null;

        while (nextUrl != null) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(nextUrl))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new RuntimeException("Graph users request failed: " + res.statusCode());

            JsonNode root = objectMapper.readTree(res.body());
            JsonNode values = root.get("value");

            if (values != null && values.isArray()) {
                for (JsonNode item : values) {
                    String azureId = item.path("id").asText(null);
                    if (azureId == null) continue;

                    // Delta sync: removed objects have @removed annotation
                    boolean isRemoved = item.has("@removed");
                    if (isRemoved) {
                        userRepository.findByAzureExternalId(azureId).ifPresent(u -> {
                            if (!u.getUsername().startsWith("_disabled_")) {
                                u.setUsername("_disabled_" + u.getUsername());
                                userRepository.save(u);
                            }
                        });
                        continue;
                    }

                    Map<String, String> attrs = nodeToMap(item);
                    foundIds.add(azureId);

                    try {
                        Optional<UserEntity> existing = userRepository.findByAzureExternalId(azureId);
                        if (existing.isPresent()) {
                            updateUser(existing.get(), attrs, mappings);
                        } else {
                            createAzureUser(attrs, mappings, azureId);
                        }
                    } catch (Exception ignored) {
                    }

                    processed++;
                    taskProgressService.updateProgress(taskId, Math.min(20 + processed, 60),
                            "Processed " + processed + " users");
                }
            }

            JsonNode nextLink = root.get("@odata.nextLink");
            JsonNode dLink = root.get("@odata.deltaLink");
            if (nextLink != null) {
                nextUrl = nextLink.asText();
            } else {
                finalDeltaLink = dLink != null ? dLink.asText() : null;
                nextUrl = null;
            }
        }

        return finalDeltaLink;
    }

    public UserEntity createAzureUser(Map<String, String> attrs, List<AzureFieldMappingEntity> mappings, String azureId) {
        String username = resolveBuiltIn(attrs, mappings, "username");
        if (username == null || username.isBlank()) {
            username = attrs.getOrDefault("userPrincipalName",
                       attrs.getOrDefault("mail",
                       attrs.getOrDefault("displayName", azureId)));
            // Strip domain suffix from UPN
            int atIdx = username.indexOf('@');
            if (atIdx > 0) username = username.substring(0, atIdx);
        }
        username = sanitizeUsername(username);
        username = makeUsernameUnique(username);

        String displayName = resolveBuiltIn(attrs, mappings, "display_name");
        if (displayName == null || displayName.isBlank()) {
            displayName = attrs.getOrDefault("displayName", username);
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setAzureExternalId(azureId);
        user.setSourceType(2);
        user.setSuperAdmin(false);
        user.setPassword(HashUtils.sha1(UUID.randomUUID() + "_azure_" + azureId));

        Map<String, Object> metadata = new HashMap<>();
        applyCustomMappings(metadata, attrs, mappings);
        if (!metadata.isEmpty()) user.setMetadata(metadata);

        return userRepository.save(user);
    }

    private void updateUser(UserEntity user, Map<String, String> attrs, List<AzureFieldMappingEntity> mappings) {
        String displayName = resolveBuiltIn(attrs, mappings, "display_name");
        if (displayName == null || displayName.isBlank()) {
            displayName = attrs.get("displayName");
        }
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }
        Map<String, Object> metadata = user.getMetadata() != null
                ? new HashMap<>(user.getMetadata()) : new HashMap<>();
        applyCustomMappings(metadata, attrs, mappings);
        user.setMetadata(metadata);
        userRepository.save(user);
    }

    // ── Group sync ────────────────────────────────────────────────────────────

    private String syncGroups(AzureConfigEntity config, String token,
                              List<AzureFieldMappingEntity> mappings, String taskId) throws Exception {
        String deltaLink = config.getGroupDeltaLink();
        String nextUrl = (deltaLink != null && !deltaLink.isBlank())
                ? deltaLink
                : GRAPH_BASE + "/groups/delta?$select=" + GROUP_SELECT;

        int processed = 0;
        String finalDeltaLink = null;

        while (nextUrl != null) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(nextUrl))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new RuntimeException("Graph groups request failed: " + res.statusCode());

            JsonNode root = objectMapper.readTree(res.body());
            JsonNode values = root.get("value");

            if (values != null && values.isArray()) {
                for (JsonNode item : values) {
                    if (item.has("@removed")) continue;
                    try {
                        String groupName = item.path("displayName").asText(null);
                        if (groupName == null || groupName.isBlank()) continue;

                        GroupEntity group = groupRepository.findByDisplayName(groupName)
                                .orElseGet(() -> {
                                    GroupEntity g = new GroupEntity();
                                    g.setDisplayName(groupName);
                                    return g;
                                });

                        String azureGroupId = item.path("id").asText(null);
                        Map<String, String> attrs = nodeToMap(item);
                        Map<String, Object> metadata = group.getMetadata() != null
                                ? new HashMap<>(group.getMetadata()) : new HashMap<>();
                        applyGroupCustomMappings(metadata, attrs, mappings);
                        group.setMetadata(metadata.isEmpty() ? null : metadata);
                        group = groupRepository.save(group);

                        int membersAdded = 0;
                        if (azureGroupId != null) {
                            membersAdded = fetchAndSyncGroupMembers(group.getRefId(), azureGroupId, token);
                        }

                        processed++;
                        taskProgressService.updateProgress(taskId, Math.min(70 + processed, 95),
                                "Processed " + processed + " groups, last: " + groupName + " (" + membersAdded + " members)");
                    } catch (Exception e) {
                        log.error("Error processing group: {}", e.getMessage(), e);
                        processed++;
                    }
                }
            }

            JsonNode nextLink = root.get("@odata.nextLink");
            JsonNode dLink = root.get("@odata.deltaLink");
            if (nextLink != null) {
                nextUrl = nextLink.asText();
            } else {
                finalDeltaLink = dLink != null ? dLink.asText() : null;
                nextUrl = null;
            }
        }

        return finalDeltaLink;
    }

    private int fetchAndSyncGroupMembers(Long groupId, String azureGroupId, String token) {
        List<Long> memberUserIds = new ArrayList<>();
        int rawMemberCount = 0;
        String url = GRAPH_BASE + "/groups/" + azureGroupId + "/members?$top=999";

        try {
            while (url != null) {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();
                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    log.warn("Members endpoint returned HTTP {} for group {} (dbId={}): {}",
                            res.statusCode(), azureGroupId, groupId, res.body());
                    return 0;
                }

                JsonNode root = objectMapper.readTree(res.body());
                JsonNode values = root.get("value");
                if (values != null && values.isArray()) {
                    for (JsonNode member : values) {
                        String azureId = member.path("id").asText(null);
                        if (azureId == null) continue;
                        rawMemberCount++;
                        userRepository.findByAzureExternalId(azureId)
                                .ifPresent(u -> memberUserIds.add(u.getRed_id()));
                    }
                }
                JsonNode nextLink = root.get("@odata.nextLink");
                url = nextLink != null ? nextLink.asText() : null;
            }
        } catch (Exception e) {
            log.error("Error fetching members for group {} (dbId={}): {}", azureGroupId, groupId, e.getMessage(), e);
            return 0;
        }

        log.info("Group {} (dbId={}): {} Azure members, {} matched local users",
                azureGroupId, groupId, rawMemberCount, memberUserIds.size());

        groupMemberRepository.deleteAllByGroupId(groupId);
        int saved = 0;
        for (Long userId : memberUserIds) {
            GroupMemberEntity m = new GroupMemberEntity();
            m.setGroupId(groupId);
            m.setUserId(userId);
            try {
                groupMemberRepository.save(m);
                saved++;
            } catch (Exception e) {
                log.warn("Failed to save group member groupId={} userId={}: {}", groupId, userId, e.getMessage());
            }
        }
        return saved;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> nodeToMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (!v.isNull() && !v.isObject() && !v.isArray()) {
                map.put(e.getKey(), v.asText());
            }
        });
        return map;
    }

    private String resolveBuiltIn(Map<String, String> attrs, List<AzureFieldMappingEntity> mappings, String fieldKey) {
        for (AzureFieldMappingEntity m : mappings) {
            if (fieldKey.equals(m.getSystemFieldKey())) {
                return attrs.get(m.getAzureAttribute());
            }
        }
        return null;
    }

    private void applyCustomMappings(Map<String, Object> metadata,
                                     Map<String, String> attrs,
                                     List<AzureFieldMappingEntity> mappings) {
        for (AzureFieldMappingEntity m : mappings) {
            if ("username".equals(m.getSystemFieldKey()) || "display_name".equals(m.getSystemFieldKey())) continue;
            String value = attrs.get(m.getAzureAttribute());
            if (value != null) metadata.put(m.getSystemFieldKey(), value);
        }
    }

    private void applyGroupCustomMappings(Map<String, Object> metadata,
                                          Map<String, String> attrs,
                                          List<AzureFieldMappingEntity> mappings) {
        for (AzureFieldMappingEntity m : mappings) {
            if ("display_name".equals(m.getSystemFieldKey())) continue;
            String value = attrs.get(m.getAzureAttribute());
            if (value != null) metadata.put(m.getSystemFieldKey(), value);
        }
    }

    private String sanitizeUsername(String raw) {
        return raw.toLowerCase().replaceAll("[^a-z0-9._@-]", "_");
    }

    private String makeUsernameUnique(String base) {
        if (userRepository.findByUsername(base).isEmpty()) return base;
        int suffix = 1;
        String candidate;
        do {
            candidate = base + "_" + suffix++;
        } while (userRepository.findByUsername(candidate).isPresent());
        return candidate;
    }
}
