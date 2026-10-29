package com.example.devforge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

public record WorkspaceCreationDto(@NotBlank @Size(min = 2, max = 100) String name)
    implements Serializable {}
