package com.turbotikects.turbotikectsserver.config;

import com.turbotikects.turbotikectsserver.entitys.UserEntity;
import com.turbotikects.turbotikectsserver.repositorys.UserRepository;
import com.turbotikects.turbotikectsserver.utils.HashUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Seeds a custom super-admin from ADMIN_USERNAME / ADMIN_PASSWORD / ADMIN_DISPLAY_NAME
 * environment variables. Runs after Flyway migrations on every startup; if the user
 * already exists the password and display name are updated.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(ApplicationArguments args) {
        String username    = System.getenv("ADMIN_USERNAME");
        String password    = System.getenv("ADMIN_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }

        String displayName = Optional.ofNullable(System.getenv("ADMIN_DISPLAY_NAME"))
                .filter(s -> !s.isBlank())
                .orElse(username);

        String hash = HashUtils.sha1(username + "_" + password);

        userRepository.findByUsername(username).ifPresentOrElse(existing -> {
            existing.setPassword(hash);
            existing.setDisplayName(displayName);
            existing.setSuperAdmin(true);
            userRepository.save(existing);
        }, () -> {
            UserEntity u = new UserEntity();
            u.setUsername(username);
            u.setPassword(hash);
            u.setDisplayName(displayName);
            u.setSuperAdmin(true);
            userRepository.save(u);
        });
    }
}
