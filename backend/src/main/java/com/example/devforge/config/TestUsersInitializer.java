package com.example.devforge.config;

import com.example.devforge.config.keycloak.KeycloakAdminClient;
import com.example.devforge.dto.UserCreationDto;
import com.example.devforge.entity.User;
import com.example.devforge.entity.UserRole;
import com.example.devforge.repository.UserRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("test")
@RequiredArgsConstructor
public class TestUsersInitializer implements ApplicationRunner {

  private final UserRepository userRepository;
  private final KeycloakAdminClient keycloakAdminClient;

  @Value("${KEYCLOAK_TEST_ADMIN_USERNAME}")
  private String adminUsername;

  @Value("${KEYCLOAK_TEST_ADMIN_EMAIL}")
  private String adminEmail;

  @Value("${KEYCLOAK_TEST_ADMIN_PASSWORD}")
  private String adminPassword;

  @Value("${KEYCLOAK_TEST_USER_USERNAME}")
  private String userUsername;

  @Value("${KEYCLOAK_TEST_USER_EMAIL}")
  private String userEmail;

  @Value("${KEYCLOAK_TEST_USER_PASSWORD}")
  private String userPassword;

  @Override
  public void run(@NonNull ApplicationArguments args) {
    createUser(adminUsername, adminEmail, adminPassword, "Test", "Admin", UserRole.ADMIN, true);
    createUser(userUsername, userEmail, userPassword, "Test", "User", UserRole.DEVELOPER, false);
  }

  private void createUser(
      String username,
      String email,
      String password,
      String firstName,
      String lastName,
      UserRole role,
      boolean admin) {
    String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    UUID keycloakId;
    try {
      keycloakId = keycloakAdminClient.findUserId(normalizedUsername);
      if (keycloakId == null) {
        keycloakId =
            keycloakAdminClient.createUser(
                new UserCreationDto(
                    normalizedUsername, normalizedEmail, password, firstName, lastName));
      }
      if (keycloakId == null) {
        log.warn(
            "Keycloak user ID is null for [{}]. Skipping database creation.", normalizedUsername);
        return;
      }
      if (admin) {
        keycloakAdminClient.assignRealmRole(keycloakId, UserRole.ADMIN.name());
      }
    } catch (Exception e) {
      log.error("Failed to ensure Keycloak test user [{}]: {}", normalizedUsername, e.getMessage());
      return;
    }
    if (!userRepository.existsByUsername(normalizedUsername)
        && !userRepository.existsByEmail(normalizedEmail)) {
      userRepository.save(
          User.builder()
              .keycloakId(keycloakId)
              .username(normalizedUsername)
              .email(normalizedEmail)
              .firstName(firstName)
              .lastName(lastName)
              .role(role)
              .build());
    }
    log.info("Test user [{}] is ready in Keycloak and the database.", normalizedUsername);
  }
}
