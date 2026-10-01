package com.example.devforge.config;

import com.example.devforge.config.keycloak.KeycloakAdminClient;
import com.example.devforge.dto.UserCreationDto;
import com.example.devforge.entity.User;
import com.example.devforge.entity.UserRole;
import com.example.devforge.exception.UserAlreadyExistsException;
import com.example.devforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    @Value("${devforge.admin.username}")
    private String adminUsername;

    @Value("${devforge.admin.email}")
    private String adminEmail;

    @Value("${devforge.admin.password}")
    private String adminPassword;

    @Value("${devforge.admin.first-name}")
    private String adminFirstName;

    @Value("${devforge.admin.last-name}")
    private String adminLastName;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        String username = adminUsername.trim().toLowerCase(Locale.ROOT);
        String email = adminEmail.trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            log.info("Admin user [{}] already exists in the database. Skipping creation.", username);
            return;
        }

        log.info("Bootstrapping default admin user [{}] in Keycloak and Database...", username);

        UserCreationDto dto = new UserCreationDto(
                username,
                email,
                adminPassword,
                adminFirstName,
                adminLastName
        );

        UUID keycloakId;
        try {
            keycloakId = keycloakAdminClient.createUser(dto);
            keycloakAdminClient.assignRealmRole(keycloakId, UserRole.ADMIN.name());
            log.info("Keycloak user created with ID [{}] and role [ADMIN].", keycloakId);

        } catch (UserAlreadyExistsException e) {
            log.warn("Admin user [{}] already exists in Keycloak.", username);
            return;
        } catch (Exception e) {
            log.error("Failed to create admin user in Keycloak: {}", e.getMessage());
            return;
        }
        User adminUser = User.builder()
                .keycloakId(keycloakId)
                .username(username)
                .email(email)
                .firstName(adminFirstName)
                .lastName(adminLastName)
                .role(UserRole.ADMIN)
                .build();

        userRepository.save(adminUser);
        log.info("Admin user [{}] successfully saved to database with role [ADMIN].", username);
    }
}