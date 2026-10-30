package com.example.devforge.dto;

import java.io.Serializable;

public record InternalJobClaimResponse(boolean proceed, int attempt, Long repositoryId)
    implements Serializable {}
