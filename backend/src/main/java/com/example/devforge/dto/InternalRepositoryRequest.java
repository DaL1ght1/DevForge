package com.example.devforge.dto;

import jakarta.validation.constraints.Positive;

public record InternalRepositoryRequest(@Positive long repositoryId) {}
