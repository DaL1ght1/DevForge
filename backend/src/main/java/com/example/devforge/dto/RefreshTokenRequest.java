package com.example.devforge.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) implements Serializable {
}
