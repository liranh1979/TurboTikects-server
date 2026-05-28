package com.turbotikects.turbotikectsserver.services;

import com.turbotikects.turbotikectsserver.dto.LoginRequest;
import com.turbotikects.turbotikectsserver.dto.UserDto;
import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import com.turbotikects.turbotikectsserver.utils.HashUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired private UserRepository userRepository;
    private final PermissionService permissionService;

    public UserService(UserRepository userRepository, @Lazy PermissionService permissionService) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    public UserDto login(LoginRequest loginRequest) {
        String hashPassword = HashUtils.sha1(loginRequest.getUsername() + "_" + loginRequest.getPassword());
        Optional<UserEntity> user = userRepository.findByUsernameAndPassword(loginRequest.getUsername(), hashPassword);

        if (user.isPresent()) {
            UserDto userDto = new UserDto();
            userDto.setUsername(loginRequest.getUsername());
            userDto.setDisplayName(user.get().getDisplayName());
            userDto.setSuperAdmin(user.get().isSuperAdmin());
            userDto.setMetadata(user.get().getMetadata());

            List<String> effective = permissionService.computeEffectivePermissions(user.get().getRed_id());
            userDto.setEffectivePermissions(effective);

            return userDto;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
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
