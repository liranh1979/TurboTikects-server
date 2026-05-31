package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.*;
import com.turbotikects.turbotikectsserver.entitys.LdapConfigEntity;
import com.turbotikects.turbotikectsserver.entitys.LdapFieldMappingEntity;
import com.turbotikects.turbotikectsserver.repositorys.LdapConfigRepository;
import com.turbotikects.turbotikectsserver.repositorys.LdapFieldMappingRepository;
import com.turbotikects.turbotikectsserver.utils.AesEncryptionUtils;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LdapService {

    private final LdapConfigRepository configRepository;
    private final LdapFieldMappingRepository mappingRepository;
    private final AesEncryptionUtils aes;

    public LdapService(LdapConfigRepository configRepository,
                       LdapFieldMappingRepository mappingRepository,
                       AesEncryptionUtils aes) {
        this.configRepository = configRepository;
        this.mappingRepository = mappingRepository;
        this.aes = aes;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public List<LdapConfigDto> getAllConfigs() {
        return configRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public LdapConfigDto getConfig(Long id) {
        return toDto(findOrThrow(id));
    }

    public LdapConfigDto createConfig(LdapConfigDto dto) {
        LdapConfigEntity entity = new LdapConfigEntity();
        applyDto(dto, entity, true);
        return toDto(configRepository.save(entity));
    }

    public LdapConfigDto updateConfig(Long id, LdapConfigDto dto) {
        LdapConfigEntity entity = findOrThrow(id);
        boolean hasNewPassword = dto.getBindPassword() != null && !dto.getBindPassword().isBlank();
        applyDto(dto, entity, hasNewPassword);
        return toDto(configRepository.save(entity));
    }

    public void deleteConfig(Long id) {
        configRepository.deleteById(id);
    }

    public LdapConfigDto setActive(Long id, boolean active) {
        LdapConfigEntity entity = findOrThrow(id);
        entity.setActive(active);
        return toDto(configRepository.save(entity));
    }

    // ── Test connection ───────────────────────────────────────────────────────

    public LdapTestResultDto testConnection(Long id) {
        LdapConfigEntity entity = findOrThrow(id);
        String password = aes.decrypt(entity.getBindPassword());
        return doTest(entity.getHost(), entity.getPort(), entity.isUseSsl(),
                entity.getBindDn(), password);
    }

    public LdapTestResultDto testConnectionRaw(LdapConfigDto dto) {
        String password = dto.getBindPassword();
        return doTest(dto.getHost(), dto.getPort(), dto.isUseSsl(),
                dto.getBindDn(), password);
    }

    private LdapTestResultDto doTest(String host, int port, boolean ssl, String bindDn, String password) {
        try {
            LdapContextSource ctx = buildContextSource(host, port, ssl, bindDn, password);
            ctx.getContext(bindDn, password).close();
            return LdapTestResultDto.ok();
        } catch (Exception e) {
            return LdapTestResultDto.fail(e.getMessage());
        }
    }

    // ── Sample data ───────────────────────────────────────────────────────────

    public LdapSampleDto fetchSampleUser(Long id) {
        LdapConfigEntity entity = findOrThrow(id);
        return fetchSample(entity, entity.getBaseDnUsers(), entity.getUserFilter());
    }

    public LdapSampleDto fetchSampleGroup(Long id) {
        LdapConfigEntity entity = findOrThrow(id);
        if (entity.getBaseDnGroups() == null || entity.getBaseDnGroups().isBlank()) {
            LdapSampleDto empty = new LdapSampleDto();
            empty.setAttributes(Map.of());
            return empty;
        }
        return fetchSample(entity, entity.getBaseDnGroups(), entity.getGroupFilter());
    }

    private LdapSampleDto fetchSample(LdapConfigEntity entity, String baseDn, String filter) {
        LdapSampleDto dto = new LdapSampleDto();
        dto.setAttributes(Map.of());
        try {
            String password = aes.decrypt(entity.getBindPassword());
            LdapContextSource ctx = buildContextSource(
                    entity.getHost(), entity.getPort(), entity.isUseSsl(), entity.getBindDn(), password);
            LdapTemplate ldapTemplate = new LdapTemplate(ctx);
            ldapTemplate.setIgnorePartialResultException(true);

            List<Map<String, String>> results = ldapTemplate.search(
                    LdapQueryBuilder.query().base(baseDn).filter(filter),
                    (Attributes attrs) -> {
                        Map<String, String> map = new LinkedHashMap<>();
                        NamingEnumeration<String> ids = attrs.getIDs();
                        while (ids.hasMoreElements()) {
                            String attrId = ids.nextElement();
                            Attribute attr = attrs.get(attrId);
                            try {
                                Object val = attr.get();
                                map.put(attrId, val != null ? val.toString() : "");
                            } catch (Exception ignored) {
                                map.put(attrId, "");
                            }
                        }
                        return map;
                    });

            dto.setAttributes(results.isEmpty() ? Map.of() : results.get(0));
        } catch (org.springframework.ldap.NameNotFoundException e) {
            dto.setError("Base DN \"" + baseDn + "\" was not found. Verify your Base DN setting matches your LDAP directory structure.");
        } catch (Exception e) {
            dto.setError(e.getMessage());
        }
        return dto;
    }

    // ── Field mappings ────────────────────────────────────────────────────────

    public List<LdapFieldMappingDto> getMappings(Long configId, String entityType) {
        return mappingRepository.findByLdapConfigIdAndEntityType(configId, entityType)
                .stream().map(this::toMappingDto).toList();
    }

    public Map<String, List<LdapFieldMappingDto>> getAllMappings(Long configId) {
        List<LdapFieldMappingEntity> all = mappingRepository.findByLdapConfigId(configId);
        List<LdapFieldMappingDto> users = new ArrayList<>();
        List<LdapFieldMappingDto> groups = new ArrayList<>();
        for (LdapFieldMappingEntity e : all) {
            if ("group".equals(e.getEntityType())) {
                groups.add(toMappingDto(e));
            } else {
                users.add(toMappingDto(e));
            }
        }
        return Map.of("user", users, "group", groups);
    }

    @Transactional
    public List<LdapFieldMappingDto> saveMappings(Long configId, SaveLdapMappingsRequestDto dto) {
        String entityType = dto.getEntityType();
        mappingRepository.deleteByLdapConfigIdAndEntityType(configId, entityType);

        List<LdapFieldMappingEntity> toSave = new ArrayList<>();
        if (dto.getMappings() != null) {
            for (LdapFieldMappingDto m : dto.getMappings()) {
                if (m.getSystemFieldKey() == null || m.getSystemFieldKey().isBlank()) {
                    continue; // skip "— skip —" entries
                }
                LdapFieldMappingEntity entity = new LdapFieldMappingEntity();
                entity.setLdapConfigId(configId);
                entity.setEntityType(entityType);
                entity.setLdapAttribute(m.getLdapAttribute());
                entity.setSystemFieldKey(m.getSystemFieldKey());
                toSave.add(entity);
            }
        }

        return mappingRepository.saveAll(toSave).stream().map(this::toMappingDto).toList();
    }

    private LdapFieldMappingDto toMappingDto(LdapFieldMappingEntity entity) {
        LdapFieldMappingDto dto = new LdapFieldMappingDto();
        dto.setId(entity.getId());
        dto.setLdapConfigId(entity.getLdapConfigId());
        dto.setEntityType(entity.getEntityType());
        dto.setLdapAttribute(entity.getLdapAttribute());
        dto.setSystemFieldKey(entity.getSystemFieldKey());
        return dto;
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Verify a user's credentials by binding directly with their LDAP DN and password.
     * Used for synced users (source_type=1) whose DN is stored in ldap_external_id.
     */
    public boolean authenticateUserByDn(String dn, String password, LdapConfigEntity config) {
        try {
            LdapContextSource ctx = buildContextSource(
                    config.getHost(), config.getPort(), config.isUseSsl(), dn, password);
            ctx.getContext(dn, password).close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Search for a user entry in LDAP by username (for JIT provisioning).
     * Returns the full attribute map including "__dn__" if a match is found.
     */
    public Optional<Map<String, String>> findUserInLdap(String username, LdapConfigEntity config) {
        try {
            String svcPwd = aes.decrypt(config.getBindPassword());
            LdapContextSource ctx = buildContextSource(
                    config.getHost(), config.getPort(), config.isUseSsl(), config.getBindDn(), svcPwd);
            LdapTemplate ldap = new LdapTemplate(ctx);
            ldap.setIgnorePartialResultException(true);

            // Determine which LDAP attribute maps to the system "username" field
            List<LdapFieldMappingEntity> mappings =
                    mappingRepository.findByLdapConfigIdAndEntityType(config.getId(), "user");
            String usernameAttr = mappings.stream()
                    .filter(m -> "username".equals(m.getSystemFieldKey()))
                    .map(LdapFieldMappingEntity::getLdapAttribute)
                    .findFirst().orElse(null);

            String esc = escapeLdapFilterValue(username);
            String uf = usernameAttr != null
                    ? "(" + usernameAttr + "=" + esc + ")"
                    : "(|(sAMAccountName=" + esc + ")(uid=" + esc + ")(cn=" + esc + "))";
            String filter = "(&" + config.getUserFilter() + uf + ")";

            List<Map<String, String>> results = ldap.search(
                    LdapQueryBuilder.query().base(config.getBaseDnUsers()).filter(filter),
                    new AbstractContextMapper<Map<String, String>>() {
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
                                    StringBuilder sb = new StringBuilder();
                                    NamingEnumeration<?> vals = attr.getAll();
                                    while (vals.hasMore()) {
                                        Object v = vals.next();
                                        if (v instanceof byte[]) continue;
                                        if (sb.length() > 0) sb.append('\n');
                                        sb.append(v);
                                    }
                                    if (sb.length() > 0) entry.put(id, sb.toString());
                                }
                            } catch (NamingException ignored) {}
                            return entry;
                        }
                    });

            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String escapeLdapFilterValue(String value) {
        return value.replace("\\", "\\5c").replace("*", "\\2a")
                    .replace("(", "\\28").replace(")", "\\29").replace("\0", "\\00");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LdapContextSource buildContextSource(String host, int port, boolean ssl, String bindDn, String password) {
        LdapContextSource ctx = new LdapContextSource();
        ctx.setUrl((ssl ? "ldaps://" : "ldap://") + host.trim() + ":" + port);
        ctx.setBase("");
        ctx.setUserDn(bindDn);
        ctx.setPassword(password);
        ctx.afterPropertiesSet();
        return ctx;
    }

    private LdapConfigEntity findOrThrow(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("LDAP config not found: " + id));
    }

    private void applyDto(LdapConfigDto dto, LdapConfigEntity entity, boolean encryptPassword) {
        if (dto.getDisplayName() != null) entity.setDisplayName(dto.getDisplayName().trim());
        if (dto.getHost() != null) entity.setHost(dto.getHost().trim());
        if (dto.getPort() > 0) entity.setPort(dto.getPort());
        entity.setUseSsl(dto.isUseSsl());
        if (dto.getBindDn() != null && !dto.getBindDn().isBlank()) {
            entity.setBindDn(dto.getBindDn());
        }
        if (encryptPassword && dto.getBindPassword() != null && !dto.getBindPassword().isBlank()) {
            entity.setBindPassword(aes.encrypt(dto.getBindPassword()));
        }
        if (dto.getBaseDnUsers() != null) entity.setBaseDnUsers(dto.getBaseDnUsers());
        if (dto.getBaseDnGroups() != null) entity.setBaseDnGroups(dto.getBaseDnGroups());
        if (dto.getUserFilter() != null) entity.setUserFilter(dto.getUserFilter());
        if (dto.getGroupFilter() != null) entity.setGroupFilter(dto.getGroupFilter());
    }

    private LdapConfigDto toDto(LdapConfigEntity entity) {
        LdapConfigDto dto = new LdapConfigDto();
        dto.setId(entity.getId());
        dto.setDisplayName(entity.getDisplayName());
        dto.setHost(entity.getHost());
        dto.setPort(entity.getPort());
        dto.setUseSsl(entity.isUseSsl());
        dto.setBindDn(entity.getBindDn());
        // bindPassword intentionally omitted from GET responses
        dto.setBaseDnUsers(entity.getBaseDnUsers());
        dto.setBaseDnGroups(entity.getBaseDnGroups());
        dto.setUserFilter(entity.getUserFilter());
        dto.setGroupFilter(entity.getGroupFilter());
        dto.setLastSyncedAt(entity.getLastSyncedAt());
        dto.setActive(entity.isActive());
        return dto;
    }
}
