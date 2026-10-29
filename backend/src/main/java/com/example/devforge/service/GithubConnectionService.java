package com.example.devforge.service;

import com.example.devforge.dto.GithubAccountResponse;
import com.example.devforge.dto.GithubInstallationCandidateResponse;
import com.example.devforge.dto.GithubInstallationLinkDto;
import com.example.devforge.dto.GithubInstallationResponse;
import java.util.List;
import java.util.UUID;

public interface GithubConnectionService {

  String authorizationUrl(UUID keycloakId, UUID workspaceId);

  String installationUrl(UUID keycloakId, UUID workspaceId);

  String handleCallback(String code, String state, Long installationId, String error);

  GithubAccountResponse getAccount(UUID keycloakId);

  List<GithubInstallationResponse> listLinked(UUID keycloakId, UUID workspaceId);

  List<GithubInstallationCandidateResponse> listCandidates(UUID keycloakId, UUID workspaceId);

  GithubInstallationResponse link(UUID keycloakId, GithubInstallationLinkDto dto);

  void unlink(UUID keycloakId, UUID id);
}
