package com.example.devforge.dto;

import com.example.devforge.entity.GithubAccountType;
import java.io.Serializable;

public record GithubInstallationCandidateResponse(
    long installationId,
    String accountLogin,
    GithubAccountType accountType,
    String repositorySelection,
    boolean linked,
    boolean available)
    implements Serializable {}
