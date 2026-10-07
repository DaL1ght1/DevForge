package com.example.devforge.dto;

import com.example.devforge.entity.UserRole;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link com.example.devforge.entity.User} */
public record UserResponseDto(
    UUID id,
    String username,
    String email,
    String firstName,
    String lastName,
    UserRole role,
    Instant createdAt)
    implements Serializable {}
