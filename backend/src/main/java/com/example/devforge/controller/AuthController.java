package com.example.devforge.controller;

import com.example.devforge.dto.AuthResponse;
import com.example.devforge.dto.LoginRequest;
import com.example.devforge.dto.RefreshTokenRequest;
import com.example.devforge.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user login and token refresh")
public class AuthController {

    private final AuthService authService;

    @SecurityRequirements
    @Operation(summary = "Authenticate user", description = "Authenticates user against Keycloak and returns JWT tokens")
    @PostMapping("/login")
    public AuthResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @SecurityRequirements
    @Operation(summary = "Refresh access token", description = "Exchanges a valid refresh token for a new access token")
    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return authService.refresh(request);
    }
    @SecurityRequirements
    @Operation(summary = "Logout user", description = "Logs out the user and invalidates the refresh token")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String refreshToken = request != null ? request.refreshToken() : null;
        authService.logout(refreshToken, jwt);
    }
}
