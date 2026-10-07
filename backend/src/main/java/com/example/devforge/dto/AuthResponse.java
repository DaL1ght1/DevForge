package com.example.devforge.dto;

import java.io.Serializable;

public record AuthResponse(
    String accessToken, String refreshToken, long expiresIn, String tokenType, UserResponseDto user)
    implements Serializable {}
