package com.example.devforge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.NonNull;

/** DTO for {@link com.example.devforge.entity.User} */
public record UserCreationDto(
    @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(
            regexp = "^[a-zA-Z0-9._-]+$",
            message = "may only contain letters, digits, '.', '_' and '-'")
        String username,
    @NotBlank @Email @Size(max = 100) String email,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank @Size(max = 50) String firstName,
    @NotBlank @Size(max = 50) String lastName)
    implements Serializable {
  @Override
  @NonNull
  public String toString() {
    return "UserCreationDto[username=" + username + ", email=" + email + "]";
  }
}
