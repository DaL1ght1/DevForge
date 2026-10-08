package com.example.devforge.dto;

import com.example.devforge.entity.AppService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
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
    @NotBlank @Length(max = 20) String databaseType)
    implements Serializable {}
