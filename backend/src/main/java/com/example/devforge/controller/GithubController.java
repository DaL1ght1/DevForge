package com.example.devforge.controller;

import com.example.devforge.dto.GithubAccountResponse;
import com.example.devforge.dto.GithubInstallationCandidateResponse;
import com.example.devforge.dto.GithubInstallationLinkDto;
import com.example.devforge.dto.GithubInstallationResponse;
import com.example.devforge.dto.GithubRedirectResponse;
import com.example.devforge.service.GithubConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/github")
public class GithubController {

  private final GithubConnectionService githubConnectionService;

  @Operation(summary = "URL to authorize the DevForge GitHub App for a workspace")
  @GetMapping("/authorize")
  public GithubRedirectResponse authorize(
      @AuthenticationPrincipal Jwt jwt, @RequestParam UUID workspaceId) {
    return new GithubRedirectResponse(
        githubConnectionService.authorizationUrl(keycloakId(jwt), workspaceId));
  }

  @Operation(summary = "URL to install the DevForge GitHub App for a workspace")
  @GetMapping("/install")
  public GithubRedirectResponse install(
      @AuthenticationPrincipal Jwt jwt, @RequestParam UUID workspaceId) {
    return new GithubRedirectResponse(
        githubConnectionService.installationUrl(keycloakId(jwt), workspaceId));
  }

  @SecurityRequirements
  @Operation(summary = "GitHub authorization/installation callback (called by GitHub)")
  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(name = "installation_id", required = false) Long installationId,
      @RequestParam(required = false) String error) {
    String redirect = githubConnectionService.handleCallback(code, state, installationId, error);
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirect)).build();
  }

  @GetMapping("/account")
  public GithubAccountResponse account(@AuthenticationPrincipal Jwt jwt) {
    return githubConnectionService.getAccount(keycloakId(jwt));
  }

  @GetMapping("/installations")
  public List<GithubInstallationResponse> listLinked(
      @AuthenticationPrincipal Jwt jwt, @RequestParam UUID workspaceId) {
    return githubConnectionService.listLinked(keycloakId(jwt), workspaceId);
  }

  @GetMapping("/installations/available")
  public List<GithubInstallationCandidateResponse> listCandidates(
      @AuthenticationPrincipal Jwt jwt, @RequestParam UUID workspaceId) {
    return githubConnectionService.listCandidates(keycloakId(jwt), workspaceId);
  }

  @PostMapping("/installations")
  @ResponseStatus(HttpStatus.CREATED)
  public GithubInstallationResponse link(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid GithubInstallationLinkDto dto) {
    return githubConnectionService.link(keycloakId(jwt), dto);
  }

  @DeleteMapping("/installations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    githubConnectionService.unlink(keycloakId(jwt), id);
  }

  private static UUID keycloakId(Jwt jwt) {
    return UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
  }
}
