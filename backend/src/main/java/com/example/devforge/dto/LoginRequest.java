package com.example.devforge.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

public record LoginRequest(
    @NotBlank(message = "Username or email is required") String username,
    @NotBlank(message = "Password is required") String password)
    implements Serializable {}
