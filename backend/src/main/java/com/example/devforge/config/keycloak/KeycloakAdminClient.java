package com.example.devforge.config.keycloak;

import com.example.devforge.dto.UserCreationDto;
import com.example.devforge.exception.IdentityProviderException;
import com.example.devforge.exception.RegistrationRejectedException;
import com.example.devforge.exception.UserAlreadyExistsException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient keycloakRestClient;
  private final KeycloakProperties props;

  private String cachedToken;
  private Instant tokenExpiry = Instant.EPOCH;

  public UUID createUser(UserCreationDto dto) {
    Map<String, Object> body = getBody(dto);
    try {
      ResponseEntity<Void> res =
          keycloakRestClient
              .post()
              .uri("/admin/realms/{realm}/users", props.realm())
              .headers(bearer())
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .toBodilessEntity();

      URI location = res.getHeaders().getLocation();
      if (location == null) {
        throw new IdentityProviderException(
            "Keycloak did not return the new user's location", null);
      }

      String path = location.getPath();
      return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    } catch (HttpClientErrorException.Conflict e) {
      throw new UserAlreadyExistsException("Username or email already registered");
    } catch (HttpClientErrorException.BadRequest e) {
      throw new RegistrationRejectedException(extractMessage(e));
    } catch (RestClientException e) {
      throw new IdentityProviderException("Keycloak user creation failed", e);
    }
  }

  public UUID findUserId(String username) {
    try {
      List<Map<String, Object>> users =
          keycloakRestClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/admin/realms/{realm}/users")
                          .queryParam("username", username)
                          .queryParam("exact", true)
                          .build(props.realm()))
              .headers(bearer())
              .retrieve()
              .body(new ParameterizedTypeReference<>() {});
      if (users == null || users.isEmpty() || users.getFirst().get("id") == null) {
        return null;
      }
      return UUID.fromString(users.getFirst().get("id").toString());
    } catch (RestClientException e) {
      throw new IdentityProviderException("Could not find Keycloak user", e);
    }
  }

  public void assignRealmRole(UUID userId, String roleName) {
    try {
      Map<String, Object> role =
          keycloakRestClient
              .get()
              .uri("/admin/realms/{realm}/roles/{role}", props.realm(), roleName)
              .headers(bearer())
              .retrieve()
              .body(MAP_TYPE);
      if (role == null) {
        throw new IdentityProviderException("Realm role not found: " + roleName, null);
      }

      keycloakRestClient
          .post()
          .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", props.realm(), userId)
          .headers(bearer())
          .contentType(MediaType.APPLICATION_JSON)
          .body(List.of(Map.of("id", role.get("id"), "name", role.get("name"))))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new IdentityProviderException("Could not assign role " + roleName, e);
    }
  }

  public void deleteUser(UUID userId) {
    try {
      keycloakRestClient
          .delete()
          .uri("/admin/realms/{realm}/users/{id}", props.realm(), userId)
          .headers(bearer())
          .retrieve()
          .toBodilessEntity();
    } catch (RuntimeException e) {
      log.error("Could not roll back Keycloak user {} - delete it manually", userId, e);
    }
  }

  public void updateUser(
      UUID userId, String username, String email, String firstName, String lastName) {
    Map<String, Object> body =
        Map.of(
            "username", username,
            "email", email,
            "firstName", firstName,
            "lastName", lastName);
    try {
      keycloakRestClient
          .put()
          .uri("/admin/realms/{realm}/users/{id}", props.realm(), userId)
          .headers(bearer())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException.Conflict e) {
      throw new UserAlreadyExistsException("Username or email already registered");
    } catch (HttpClientErrorException.NotFound e) {
      throw new IdentityProviderException("Keycloak user not found: " + userId, e);
    } catch (HttpClientErrorException.BadRequest e) {
      throw new RegistrationRejectedException(extractMessage(e));
    } catch (RestClientException e) {
      throw new IdentityProviderException("Keycloak user update failed", e);
    }
  }

  public void resetPassword(UUID userId, String newPassword) {
    Map<String, Object> body = Map.of("type", "password", "value", newPassword, "temporary", false);
    try {
      keycloakRestClient
          .put()
          .uri("/admin/realms/{realm}/users/{id}/reset-password", props.realm(), userId)
          .headers(bearer())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException.BadRequest e) {
      throw new RegistrationRejectedException(extractMessage(e));
    } catch (RestClientException e) {
      throw new IdentityProviderException("Keycloak password reset failed", e);
    }
  }

  public void logoutUser(UUID userId) {
    try {
      keycloakRestClient
          .post()
          .uri("/admin/realms/{realm}/users/{id}/logout", props.realm(), userId)
          .headers(bearer())
          .retrieve()
          .toBodilessEntity();
      log.info("Successfully terminated Keycloak sessions for user {}", userId);
    } catch (RestClientException e) {
      log.warn("Keycloak admin logout failed for user {}: {}", userId, e.getMessage());
    }
  }

  // ---------------------------------------------------------------- internals
  private static @NonNull Map<String, Object> getBody(UserCreationDto dto) {
    String username = dto.username().trim().toLowerCase(Locale.ROOT);
    String email = dto.email().trim().toLowerCase(Locale.ROOT);
    String firstName = dto.firstName().trim();
    String lastName = dto.lastName().trim();

    return Map.of(
        "username", username,
        "email", email,
        "firstName", firstName,
        "lastName", lastName,
        "enabled", true,
        "emailVerified", true,
        "credentials",
            List.of(Map.of("type", "password", "value", dto.password(), "temporary", false)));
  }

  private Consumer<HttpHeaders> bearer() {
    return headers -> headers.setBearerAuth(adminToken());
  }

  private synchronized String adminToken() {
    if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
      return cachedToken;
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", props.adminClientId());
    form.add("client_secret", props.adminClientSecret());
    try {
      Map<String, Object> res =
          keycloakRestClient
              .post()
              .uri("/realms/{realm}/protocol/openid-connect/token", props.realm())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(MAP_TYPE);
      if (res == null || res.get("access_token") == null) {
        throw new IdentityProviderException("Keycloak returned no admin token", null);
      }
      cachedToken = (String) res.get("access_token");
      long expiresIn = ((Number) res.get("expires_in")).longValue();
      tokenExpiry = Instant.now().plusSeconds(Math.max(expiresIn - 30, 1));
      return cachedToken;
    } catch (RestClientException e) {
      throw new IdentityProviderException("Could not obtain Keycloak admin token", e);
    }
  }

  private String extractMessage(HttpClientErrorException e) {
    try {
      Map<?, ?> body = e.getResponseBodyAs(Map.class);
      Object msg = body == null ? null : body.get("errorMessage");
      if (msg != null) {
        return msg.toString();
      }
    } catch (RuntimeException ignored) {

    }
    return "Registration was rejected";
  }
}
