package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.LoginRequest;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.entitys.LdapConfigEntity;
import com.turbotikects.turbotikectsserver.entitys.LdapFieldMappingEntity;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.LdapConfigRepository;
import com.turbotikects.turbotikectsserver.repositorys.LdapFieldMappingRepository;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import com.turbotikects.turbotikectsserver.utils.HashUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final LdapConfigRepository ldapConfigRepository;
    private final LdapFieldMappingRepository ldapFieldMappingRepository;
    private final LdapService ldapService;
    private final LdapSyncService ldapSyncService;

    public UserService(UserRepository userRepository,
                       @Lazy PermissionService permissionService,
                       LdapConfigRepository ldapConfigRepository,
                       LdapFieldMappingRepository ldapFieldMappingRepository,
                       LdapService ldapService,
                       LdapSyncService ldapSyncService) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.ldapConfigRepository = ldapConfigRepository;
        this.ldapFieldMappingRepository = ldapFieldMappingRepository;
        this.ldapService = ldapService;
        this.ldapSyncService = ldapSyncService;
    }

    public UserDto login(LoginRequest req) {
        String username = normalizeUsername(req.getUsername());
        String password = req.getPassword();

        // Path 1: user already exists in the local DB
        Optional<UserEntity> found = userRepository.findByUsername(username);
        if (found.isPresent()) {
            UserEntity user = found.get();
            if (user.getSourceType() == 1) {
                // LDAP-provisioned user: authenticate via bind with stored DN
                boolean ok = ldapConfigRepository.findByIsActiveTrue().stream()
                        .anyMatch(cfg -> ldapService.authenticateUserByDn(
                                user.getLdapExternalId(), password, cfg));
                if (!ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            } else {
                // Local user: SHA-1 check
                String hash = HashUtils.sha1(username + "_" + password);
                if (!hash.equals(user.getPassword()))
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            return buildUserDto(user);
        }

        // Path 2: JIT — user not in DB yet; search active LDAP configs
        for (LdapConfigEntity cfg : ldapConfigRepository.findByIsActiveTrue()) {
            Optional<Map<String, String>> ldapAttrs = ldapService.findUserInLdap(username, cfg);
            if (ldapAttrs.isPresent()) {
                String dn = ldapAttrs.get().get("__dn__");
                if (ldapService.authenticateUserByDn(dn, password, cfg)) {
                    List<LdapFieldMappingEntity> mappings =
                            ldapFieldMappingRepository.findByLdapConfigIdAndEntityType(cfg.getId(), "user");
                    UserEntity provisioned = ldapSyncService.createLdapUser(ldapAttrs.get(), mappings, dn);
                    return buildUserDto(provisioned);
                }
            }
        }

        // Path 3: not found anywhere
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    private UserDto buildUserDto(UserEntity user) {
        UserDto dto = new UserDto();
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setSuperAdmin(user.isSuperAdmin());
        dto.setMetadata(user.getMetadata());
        dto.setEffectivePermissions(permissionService.computeEffectivePermissions(user.getRed_id()));
        return dto;
    }

    /**
     * Strips domain prefix/suffix so all three formats resolve to the bare username:
     *   CORP\johndoe  →  johndoe
     *   johndoe@corp.com  →  johndoe
     *   johndoe  →  johndoe
     */
    private static String normalizeUsername(String raw) {
        if (raw == null) return "";
        int backslash = raw.lastIndexOf('\\');
        if (backslash >= 0) return raw.substring(backslash + 1).trim();
        int at = raw.indexOf('@');
        if (at >= 0) return raw.substring(0, at).trim();
        return raw.trim();
    }

    @Cacheable(value = "sessions", key = "#sessionId")
    public UserDto saveSession(String sessionId, UserDto userDto) {
        return userDto;
    }

    @Cacheable(value = "sessions", key = "#sessionId", unless = "true")
    public UserDto getSessionReadOnly(String sessionId) {
        // This code ONLY runs if the sessionId is NOT in the cache.
        // We return null so the application knows the session doesn't exist.
        return null;
    }

    @CacheEvict(value = "sessions", key = "#sessionId")
    public void logoutUser(String sessionId){

    }
}
