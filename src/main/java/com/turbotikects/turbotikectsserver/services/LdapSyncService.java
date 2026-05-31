package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.entitys.*;
import com.turbotikects.turbotikectsserver.repositorys.*;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import com.turbotikects.turbotikectsserver.utils.HashUtils;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LdapSyncService {

    private final LdapConfigRepository configRepository;
    private final LdapFieldMappingRepository mappingRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TaskProgressService taskProgressService;
    private final AesEncryptionUtils aes;

    public LdapSyncService(LdapConfigRepository configRepository,
                           LdapFieldMappingRepository mappingRepository,
                           UserRepository userRepository,
                           GroupRepository groupRepository,
                           GroupMemberRepository groupMemberRepository,
                           TaskProgressService taskProgressService,
                           AesEncryptionUtils aes) {
        this.configRepository = configRepository;
        this.mappingRepository = mappingRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.taskProgressService = taskProgressService;
        this.aes = aes;
    }

    public String startSync(Long configId) {
        String taskId = taskProgressService.createTask("LDAP Sync", 100);
        new Thread(() -> runSync(configId, taskId)).start();
        return taskId;
    }

    public void runSync(Long configId, String taskId) {
        try {
            LdapConfigEntity config = configRepository.findById(configId)
                    .orElseThrow(() -> new RuntimeException("LDAP config not found: " + configId));

            taskProgressService.updateProgress(taskId, 5, "Loading field mappings…");
            List<LdapFieldMappingEntity> userMappings =
                    mappingRepository.findByLdapConfigIdAndEntityType(configId, "user");
            List<LdapFieldMappingEntity> groupMappings =
                    mappingRepository.findByLdapConfigIdAndEntityType(configId, "group");

            taskProgressService.updateProgress(taskId, 10, "Connecting to LDAP…");
            LdapTemplate ldapTemplate = buildTemplate(config);

            taskProgressService.updateProgress(taskId, 15, "Syncing users…");
            syncUsers(config, ldapTemplate, userMappings, taskId);

            if (config.getBaseDnGroups() != null && !config.getBaseDnGroups().isBlank()) {
                taskProgressService.updateProgress(taskId, 65, "Syncing groups…");
                syncGroups(config, ldapTemplate, groupMappings, taskId);
            }

            config.setLastSyncedAt(LocalDateTime.now());
            configRepository.save(config);

            taskProgressService.completeTask(taskId, "LDAP sync completed");

        } catch (Exception e) {
            taskProgressService.failTask(taskId, "Sync failed: " + e.getMessage());
        }
    }

    // ── User sync ─────────────────────────────────────────────────────────────

    private void syncUsers(LdapConfigEntity config, LdapTemplate ldapTemplate,
                           List<LdapFieldMappingEntity> mappings, String taskId) {

        List<Map<String, String>> ldapUsers = ldapTemplate.search(
                LdapQueryBuilder.query().base(config.getBaseDnUsers()).filter(config.getUserFilter()),
                new LdapEntryMapper());

        taskProgressService.updateProgress(taskId, 20,
                "Found " + ldapUsers.size() + " LDAP users. Importing…");

        Set<String> foundDns = new HashSet<>();
        int processed = 0;

        for (Map<String, String> attrs : ldapUsers) {
            String dn = attrs.get("__dn__");
            foundDns.add(dn);

            try {
                Optional<UserEntity> existing = userRepository.findByLdapExternalId(dn);
                if (existing.isPresent()) {
                    updateUser(existing.get(), attrs, mappings);
                } else {
                    createLdapUser(attrs, mappings, dn);
                }
            } catch (Exception e) {
                // Log and continue — one bad record should not abort the whole sync
            }

            processed++;
            int progress = 20 + (processed * 40 / Math.max(ldapUsers.size(), 1));
            taskProgressService.updateProgress(taskId, Math.min(progress, 60),
                    "Processed " + processed + " / " + ldapUsers.size() + " users");
        }

        // Soft-disable LDAP users that are no longer in the directory
        for (UserEntity user : userRepository.findBySourceType(1)) {
            if (!foundDns.contains(user.getLdapExternalId())
                    && !user.getUsername().startsWith("_disabled_")) {
                user.setUsername("_disabled_" + user.getUsername());
                userRepository.save(user);
            }
        }
    }

    public UserEntity createLdapUser(Map<String, String> attrs, List<LdapFieldMappingEntity> mappings, String dn) {
        String username = resolveBuiltIn(attrs, mappings, "username");
        if (username == null || username.isBlank()) {
            username = attrs.getOrDefault("sAMAccountName",
                       attrs.getOrDefault("uid",
                       attrs.getOrDefault("cn", dn)));
        }
        username = sanitizeUsername(username);
        username = makeUsernameUnique(username);

        String displayName = resolveBuiltIn(attrs, mappings, "display_name");
        if (displayName == null || displayName.isBlank()) {
            displayName = attrs.getOrDefault("cn", username);
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setLdapExternalId(dn);
        user.setSourceType(1);
        user.setSuperAdmin(false);
        // Non-guessable placeholder — LDAP users authenticate via LDAP bind, not this hash
        user.setPassword(HashUtils.sha1(UUID.randomUUID() + "_ldap_" + dn));

        Map<String, Object> metadata = new HashMap<>();
        applyCustomMappings(metadata, attrs, mappings);
        if (!metadata.isEmpty()) user.setMetadata(metadata);

        return userRepository.save(user);
    }

    private void updateUser(UserEntity user, Map<String, String> attrs, List<LdapFieldMappingEntity> mappings) {
        String displayName = resolveBuiltIn(attrs, mappings, "display_name");
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

    private void syncGroups(LdapConfigEntity config, LdapTemplate ldapTemplate,
                            List<LdapFieldMappingEntity> mappings, String taskId) {

        List<Map<String, String>> ldapGroups = ldapTemplate.search(
                LdapQueryBuilder.query().base(config.getBaseDnGroups()).filter(config.getGroupFilter()),
                new LdapEntryMapper());

        taskProgressService.updateProgress(taskId, 70,
                "Found " + ldapGroups.size() + " LDAP groups. Importing…");

        int processed = 0;
        for (Map<String, String> attrs : ldapGroups) {
            try {
                String groupName = resolveBuiltIn(attrs, mappings, "display_name");
                if (groupName == null || groupName.isBlank()) {
                    groupName = attrs.getOrDefault("cn", attrs.get("__dn__"));
                }
                final String resolvedGroupName = groupName;

                GroupEntity group = groupRepository.findByDisplayName(resolvedGroupName)
                        .orElseGet(() -> {
                            GroupEntity g = new GroupEntity();
                            g.setDisplayName(resolvedGroupName);
                            return g;
                        });

                Map<String, Object> metadata = group.getMetadata() != null
                        ? new HashMap<>(group.getMetadata()) : new HashMap<>();
                applyCustomMappings(metadata, attrs, mappings);
                group.setMetadata(metadata.isEmpty() ? null : metadata);
                group = groupRepository.save(group);

                syncGroupMembers(group.getRefId(), attrs);

            } catch (Exception ignored) {
            }

            processed++;
            int progress = 70 + (processed * 25 / Math.max(ldapGroups.size(), 1));
            taskProgressService.updateProgress(taskId, Math.min(progress, 95),
                    "Processed " + processed + " / " + ldapGroups.size() + " groups");
        }
    }

    private void syncGroupMembers(Long groupId, Map<String, String> attrs) {
        String memberAttr = attrs.get("member");
        if (memberAttr == null) return;

        // member attribute may contain a single DN; for multi-value we stored only the first
        // (multi-value is handled via the raw attribute in the mapper)
        String[] memberDns = memberAttr.split("\n");

        List<Long> memberUserIds = new ArrayList<>();
        for (String dn : memberDns) {
            String trimmed = dn.trim();
            if (!trimmed.isEmpty()) {
                userRepository.findByLdapExternalId(trimmed)
                        .ifPresent(u -> memberUserIds.add(u.getRed_id()));
            }
        }

        groupMemberRepository.deleteAllByGroupId(groupId);
        for (Long userId : memberUserIds) {
            GroupMemberEntity m = new GroupMemberEntity();
            m.setGroupId(groupId);
            m.setUserId(userId);
            try {
                groupMemberRepository.save(m);
            } catch (Exception ignored) {
                // unique constraint violation — member already exists
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LdapTemplate buildTemplate(LdapConfigEntity config) {
        String password = aes.decrypt(config.getBindPassword());
        LdapContextSource ctx = new LdapContextSource();
        ctx.setUrl((config.isUseSsl() ? "ldaps://" : "ldap://") + config.getHost() + ":" + config.getPort());
        ctx.setBase("");
        ctx.setUserDn(config.getBindDn());
        ctx.setPassword(password);
        ctx.afterPropertiesSet();
        LdapTemplate template = new LdapTemplate(ctx);
        template.setIgnorePartialResultException(true);
        return template;
    }

    private String resolveBuiltIn(Map<String, String> attrs, List<LdapFieldMappingEntity> mappings, String fieldKey) {
        for (LdapFieldMappingEntity m : mappings) {
            if (fieldKey.equals(m.getSystemFieldKey())) {
                return attrs.get(m.getLdapAttribute());
            }
        }
        return null;
    }

    private void applyCustomMappings(Map<String, Object> metadata,
                                     Map<String, String> attrs,
                                     List<LdapFieldMappingEntity> mappings) {
        for (LdapFieldMappingEntity m : mappings) {
            if ("username".equals(m.getSystemFieldKey()) || "display_name".equals(m.getSystemFieldKey())) continue;
            String value = attrs.get(m.getLdapAttribute());
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

    // ── Inner mapper ──────────────────────────────────────────────────────────

    private static class LdapEntryMapper extends AbstractContextMapper<Map<String, String>> {
        @Override
        protected Map<String, String> doMapFromContext(DirContextOperations ctx) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("__dn__", ctx.getNameInNamespace());
            try {
                NamingEnumeration<String> ids = ctx.getAttributes().getIDs();
                while (ids.hasMore()) {
                    String id = ids.next();
                    Attribute attr = ctx.getAttributes().get(id);
                    if (attr == null) continue;
                    // Collect all values, newline-separated (handles multi-value like member)
                    StringBuilder sb = new StringBuilder();
                    NamingEnumeration<?> vals = attr.getAll();
                    while (vals.hasMore()) {
                        Object val = vals.next();
                        if (val instanceof byte[]) continue; // skip binary
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(val.toString());
                    }
                    if (sb.length() > 0) entry.put(id, sb.toString());
                }
            } catch (NamingException ignored) {
            }
            return entry;
        }
    }
}
