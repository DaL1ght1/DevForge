package com.example.devforge.service.implementation;

import com.example.devforge.config.keycloak.KeycloakProperties;
import com.example.devforge.dto.AuthResponse;
import com.example.devforge.dto.LoginRequest;
import com.example.devforge.dto.RefreshTokenRequest;
import com.example.devforge.dto.UserResponseDto;
import com.example.devforge.entity.User;
import com.example.devforge.exception.IdentityProviderException;
import com.example.devforge.exception.InvalidCredentialsException;
import com.example.devforge.mapper.UserMapper;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImplementation implements AuthService {

    private static final String CLIENT_ID = "devforge-dev";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient keycloakRestClient;
    private final KeycloakProperties props;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

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
            Map<String, Object> res = keycloakRestClient.post()
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
            throw new IdentityProviderException("Authentication failed: identity provider unavailable", e);
        }
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", CLIENT_ID);
        form.add("refresh_token", request.refreshToken());

        try {
            Map<String, Object> res = keycloakRestClient.post()
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
}
