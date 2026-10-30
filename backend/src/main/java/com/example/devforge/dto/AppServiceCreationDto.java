package com.example.devforge.dto;

import com.example.devforge.entity.AppService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.validator.constraints.Length;

/** DTO for {@link AppService} */
public record AppServiceCreationDto(
    @Pattern(
            regexp = "^[a-z0-9-]{3,20}$",
            message =
                "Service name must be 3-20 characters long and contain only lowercase letters, numbers, and hyphens")
        @NotBlank
        @Length(max = 20)
        String name,
    @Size(max = 500) String description,
    @NotNull UUID templateVersionId,
    UUID workspaceId,
    @Size(max = 30) Map<@Size(max = 50) String, @Size(max = 200) String> variables,
    @Size(max = 30)
        Map<@Size(max = 50) String, @Size(max = 30) List<@Size(max = 50) String>> features)
    implements Serializable {

  public AppServiceCreationDto(String name, String description, UUID templateVersionId) {
    this(name, description, templateVersionId, null, null, null);
  }

  public AppServiceCreationDto(
      String name, String description, UUID templateVersionId, UUID workspaceId) {
    this(name, description, templateVersionId, workspaceId, null, null);
  }
}
