package com.example.devforge.dto;

import jakarta.validation.constraints.*;

import java.io.Serializable;

public record UserUpdateDto(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "may only contain letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank @Email @Size(max = 100)
        String email,

        @NotBlank @Size(max = 50)
        String firstName,

        @NotBlank @Size(max = 50)
        String lastName,

        @Size(min = 8, max = 128)
        String password
) implements Serializable {
    @NotNull
    @Override
    public String toString() {
        return "UserUpdateDto[username=" + username + ", email=" + email + "]";
    }
}