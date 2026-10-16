package com.example.devforge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

public record CompleteSignupRequest(
    @NotBlank String signupToken,
    @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(
            regexp = "^[a-zA-Z0-9._-]+$",
            message = "may only contain letters, digits, '.', '_' and '-'")
        String username,
    @NotBlank @Size(max = 50) String firstName,
    @NotBlank @Size(max = 50) String lastName,
    @NotBlank @Size(min = 8, max = 128) String password)
    implements Serializable {
  @Override
  public @NotNull String toString() {
    return "CompleteSignupRequest[username=" + username + "]";
  }
}
