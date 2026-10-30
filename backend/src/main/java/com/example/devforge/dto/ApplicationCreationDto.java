package com.example.devforge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApplicationCreationDto(
    @NotBlank
        @Pattern(
            regexp = "^[a-z0-9][a-z0-9-]{2,39}$",
            message =
                "Application name must be 3-40 characters of lowercase letters, numbers and hyphens")
        String name,
    @Size(max = 500) String description,
    UUID workspaceId,
    @NotNull @Size(min = 1, max = 10) List<@Valid @NotNull Service> services) {

  public record Service(
      @NotBlank
          @Pattern(
              regexp = "^[a-z0-9-]{3,20}$",
              message =
                  "Service name must be 3-20 characters of lowercase letters, numbers and hyphens")
          String name,
      @Size(max = 500) String description,
      @NotNull UUID templateVersionId,
      @Size(max = 30) Map<@Size(max = 50) String, @Size(max = 200) String> variables,
      @Size(max = 30)
          Map<@Size(max = 50) String, @Size(max = 30) List<@Size(max = 50) String>> features) {

    public AppServiceCreationDto asServiceDto(UUID workspaceId) {
      return new AppServiceCreationDto(
          name, description, templateVersionId, workspaceId, variables, features);
    }
  }
}
