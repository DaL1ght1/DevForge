package com.example.devforge.service.implementation;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.devforge.config.keycloak.KeycloakAdminClient;
import com.example.devforge.config.keycloak.KeycloakProperties;
import com.example.devforge.dto.*;
import com.example.devforge.entity.User;
import com.example.devforge.exception.IdentityProviderException;
import com.example.devforge.exception.InvalidCredentialsException;
import com.example.devforge.exception.RegistrationRejectedException;
import com.example.devforge.exception.UserAlreadyExistsException;
import com.example.devforge.mapper.UserMapper;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.AuthService;
import com.example.devforge.service.UserService;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImplementation implements AuthService {

  private static final String CLIENT_ID = "devforge-dev";
  private static final Set<String> SUPPORTED_PROVIDERS = Set.of("google", "github");
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient keycloakRestClient;
  private final KeycloakProperties props;
  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final KeycloakAdminClient keycloakAdminClient;
  private final OAuth2TicketStore ticketStore;
  private final JwtDecoder jwtDecoder;
  private final UserService userService;

  @Value("${devforge.frontend-url}")
  private String frontendUrl;

  @Value("${devforge.backend-public-url}")
  private String backendPublicUrl;

  // ------------------------------------------------------------------ password login

  @Override
  public AuthResponse login(LoginRequest request) {
    String identifier = request.username().trim();
    String password = request.password();

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", CLIENT_ID);
    form.add("username", identifier);
    form.add("password", password);

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
        throw new IdentityProviderException("Keycloak returned an invalid token response", null);
      }

      String accessToken = (String) res.get("access_token");
      String refreshToken = (String) res.get("refresh_token");
      long expiresIn = ((Number) res.get("expires_in")).longValue();
      String tokenType = (String) res.getOrDefault("token_type", "Bearer");

      String normalized = identifier.toLowerCase(Locale.ROOT);
      Optional<User> userOpt = userRepository.findByUsername(normalized);
      if (userOpt.isEmpty()) {
        userOpt = userRepository.findByEmail(normalized);
      }

      UserResponseDto userDto = userOpt.map(userMapper::toResponseDto).orElse(null);

      log.info("User '{}' authenticated successfully", identifier);
      return new AuthResponse(accessToken, refreshToken, expiresIn, tokenType, userDto);

    } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Unauthorized e) {
      log.warn("Authentication failed for identifier '{}': invalid credentials", identifier);
      throw new InvalidCredentialsException("Invalid username or password");
    } catch (RestClientException e) {
      log.error("Authentication failed for identifier '{}' due to Keycloak error", identifier, e);
      throw new IdentityProviderException(
          "Authentication failed: identity provider unavailable", e);
    }
  }

  @Override
  public AuthResponse refresh(RefreshTokenRequest request) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "refresh_token");
    form.add("client_id", CLIENT_ID);
    form.add("refresh_token", request.refreshToken());

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
        throw new IdentityProviderException("Keycloak returned an invalid refresh response", null);
      }

      String accessToken = (String) res.get("access_token");
      String refreshToken = (String) res.get("refresh_token");
      long expiresIn = ((Number) res.get("expires_in")).longValue();
      String tokenType = (String) res.getOrDefault("token_type", "Bearer");

      return new AuthResponse(accessToken, refreshToken, expiresIn, tokenType, null);

    } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Unauthorized e) {
      log.warn("Token refresh failed: invalid or expired refresh token");
      throw new InvalidCredentialsException("Invalid or expired refresh token");
    } catch (RestClientException e) {
      log.error("Token refresh failed due to Keycloak error", e);
      throw new IdentityProviderException("Token refresh failed: identity provider unavailable", e);
    }
  }

  @Override
  public void logout(String refreshToken, Jwt jwt) {
    if (refreshToken != null && !refreshToken.isBlank()) {
      try {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", CLIENT_ID);
        form.add("refresh_token", refreshToken.trim());

        keycloakRestClient
            .post()
            .uri("/realms/{realm}/protocol/openid-connect/logout", props.realm())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .toBodilessEntity();
        log.info("Successfully revoked Keycloak refresh token");
      } catch (RestClientException e) {
        log.warn(
            "Keycloak OIDC logout with refresh token failed or token already invalid: {}",
            e.getMessage());
      }
    }

    if (jwt != null && jwt.getSubject() != null) {
      try {
        UUID userId = UUID.fromString(jwt.getSubject());
        keycloakAdminClient.logoutUser(userId);
      } catch (Exception e) {
        log.warn(
            "Keycloak admin logout failed for subject {}: {}", jwt.getSubject(), e.getMessage());
      }
    }
  }

  // ------------------------------------------------------------------ social login

  @Override
  public String buildAuthorizationUrl(String provider) {
    String idp = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
    if (!SUPPORTED_PROVIDERS.contains(idp)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unsupported provider: " + provider);
    }

    OAuth2TicketStore.PendingAuth pending = ticketStore.createState();

    return UriComponentsBuilder.fromUriString(props.effectivePublicServerUrl())
        .path("/realms/{realm}/protocol/openid-connect/auth")
        .queryParam("client_id", "{clientId}")
        .queryParam("response_type", "code")
        .queryParam("scope", "openid profile email")
        .queryParam("redirect_uri", "{redirectUri}")
        .queryParam("state", "{state}")
        .queryParam("kc_idp_hint", "{idp}")
        .queryParam("code_challenge", "{challenge}")
        .queryParam("code_challenge_method", "S256")
        .encode()
        .buildAndExpand(
            Map.of(
                "realm", props.realm(),
                "clientId", props.effectiveClientId(),
                "redirectUri", callbackUri(),
                "state", pending.state(),
                "idp", idp,
                "challenge", pkceChallenge(pending.codeVerifier())))
        .toUriString();
  }

  @Override
  public String handleOAuth2Callback(
      String code, String state, String error, String errorDescription) {
    Optional<String> verifier = ticketStore.consumeState(state);
    if (verifier.isEmpty()) {
      log.warn("OAuth2 callback with invalid or expired state");
      return frontendError("invalid_state");
    }
    if (error != null) {
      log.warn("OAuth2 provider returned error: {} ({})", error, errorDescription);
      return frontendError(error);
    }
    if (code == null || code.isBlank()) {
      return frontendError("missing_code");
    }

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", props.effectiveClientId());
    form.add("code", code);
    form.add("redirect_uri", callbackUri());
    form.add("code_verifier", verifier.get());

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
        return frontendError("token_exchange_failed");
      }

      String accessToken = (String) res.get("access_token");
      String refreshToken = (String) res.get("refresh_token");
      long expiresIn = ((Number) res.get("expires_in")).longValue();
      String tokenType = (String) res.getOrDefault("token_type", "Bearer");

      Jwt jwt = jwtDecoder.decode(accessToken);

      String email = jwt.getClaimAsString("email");
      if (!StringUtils.hasText(email)) {
        return frontendError("email_required");
      }
      if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
        return frontendError("email_not_verified");
      }

      UUID keycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
      Optional<User> local = userRepository.findByKeycloakId(keycloakId);

      boolean complete =
          local.isPresent()
              && StringUtils.hasText(local.get().getFirstName())
              && StringUtils.hasText(local.get().getLastName())
              && keycloakAdminClient.hasPassword(keycloakId);

      if (!complete) {
        AuthResponse tokens =
            new AuthResponse(accessToken, refreshToken, expiresIn, tokenType, null);
        String signupToken =
            ticketStore.storePendingSignup(buildPending(keycloakId, email, jwt, local, tokens));
        log.info("User '{}' needs to complete sign-up", keycloakId);
        return frontendUrl + "/oauth2/callback?signup=" + URLEncoder.encode(signupToken, UTF_8);
      }

      AuthResponse auth =
          new AuthResponse(
              accessToken,
              refreshToken,
              expiresIn,
              tokenType,
              userMapper.toResponseDto(local.get()));
      String ticket = ticketStore.storeTicket(auth);
      log.info("User '{}' authenticated via OAuth2", keycloakId);
      return frontendUrl + "/oauth2/callback?ticket=" + URLEncoder.encode(ticket, UTF_8);

    } catch (HttpClientErrorException e) {
      log.warn("OAuth2 code exchange rejected: {}", e.getStatusCode());
      return frontendError("token_exchange_failed");
    } catch (UserAlreadyExistsException e) {
      return frontendError("account_exists");
    } catch (RegistrationRejectedException e) {
      return frontendError("email_required");
    } catch (RestClientException | JwtException | IdentityProviderException e) {
      log.error("OAuth2 callback failed", e);
      return frontendError("server_error");
    }
  }

  @Override
  public AuthResponse exchangeTicket(OAuth2TicketRequest request) {
    return ticketStore
        .consumeTicket(request.ticket())
        .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired ticket"));
  }

  @Override
  public SignupPendingResponse getPendingSignup(OAuth2TicketRequest request) {
    var p =
        ticketStore
            .getPendingSignup(request.ticket())
            .orElseThrow(() -> new InvalidCredentialsException("Sign-up session expired"));
    return new SignupPendingResponse(p.email(), p.username(), p.firstName(), p.lastName());
  }

  @Override
  public AuthResponse completeSignup(CompleteSignupRequest request) {
    var pending =
        ticketStore
            .getPendingSignup(request.signupToken())
            .orElseThrow(() -> new InvalidCredentialsException("Sign-up session expired"));

    UserResponseDto user =
        userService.completeSocialSignup(pending.keycloakId(), pending.email(), request);
    ticketStore.consumePendingSignup(request.signupToken());

    AuthResponse tokens = pending.tokens();
    try {
      tokens = refresh(new RefreshTokenRequest(tokens.refreshToken()));
    } catch (RuntimeException e) {
      log.warn("Token refresh after sign-up failed, using original tokens: {}", e.getMessage());
    }
    return new AuthResponse(
        tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(), tokens.tokenType(), user);
  }

  // ------------------------------------------------------------------ helpers

  private String callbackUri() {
    return backendPublicUrl + "/auth/oauth2/callback";
  }

  private String frontendError(String code) {
    return frontendUrl + "/oauth2/callback?error=" + URLEncoder.encode(code, UTF_8);
  }

  private static String pkceChallenge(String verifier) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private OAuth2TicketStore.PendingSignup buildPending(
      UUID id, String email, Jwt jwt, Optional<User> local, AuthResponse tokens) {
    String first = local.map(User::getFirstName).orElse(jwt.getClaimAsString("given_name"));
    String last = local.map(User::getLastName).orElse(jwt.getClaimAsString("family_name"));
    if (!StringUtils.hasText(first) && !StringUtils.hasText(last)) {
      String full = jwt.getClaimAsString("name");
      if (StringUtils.hasText(full)) {
        String[] parts = full.trim().split("\\s+", 2);
        first = parts[0];
        last = parts.length > 1 ? parts[1] : "";
      }
    }
    String username =
        local.map(User::getUsername).orElse(jwt.getClaimAsString("preferred_username"));
    return new OAuth2TicketStore.PendingSignup(
        id,
        email.trim().toLowerCase(Locale.ROOT),
        suggestUsername(username, email),
        first == null ? "" : first,
        last == null ? "" : last,
        tokens);
  }

  private static String suggestUsername(String preferred, String email) {
    String base = StringUtils.hasText(preferred) ? preferred : email;
    int at = base.indexOf('@');
    if (at > 0) base = base.substring(0, at);
    return base.replaceAll("[^a-zA-Z0-9._-]", "").toLowerCase(Locale.ROOT);
  }
}
