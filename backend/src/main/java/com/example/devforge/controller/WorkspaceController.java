package com.example.devforge.controller;

import com.example.devforge.dto.WorkspaceCreationDto;
import com.example.devforge.dto.WorkspaceMemberAddDto;
import com.example.devforge.dto.WorkspaceMemberResponse;
import com.example.devforge.dto.WorkspaceResponse;
import com.example.devforge.service.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/workspaces")
public class WorkspaceController {

  private final WorkspaceService workspaceService;

  @GetMapping
  public List<WorkspaceResponse> listWorkspaces(@AuthenticationPrincipal Jwt jwt) {
    return workspaceService.listForUser(keycloakId(jwt));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceResponse createOrganization(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid WorkspaceCreationDto dto) {
    return workspaceService.createOrganization(keycloakId(jwt), dto);
  }

  @GetMapping("/{id}")
  public WorkspaceResponse getWorkspace(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return workspaceService.getWorkspace(id, keycloakId(jwt));
  }

  @GetMapping("/{id}/members")
  public List<WorkspaceMemberResponse> listMembers(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return workspaceService.listMembers(id, keycloakId(jwt));
  }

  @PostMapping("/{id}/members")
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceMemberResponse addMember(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestBody @Valid WorkspaceMemberAddDto dto) {
    return workspaceService.addMember(id, keycloakId(jwt), dto);
  }

  @DeleteMapping("/{id}/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeMember(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
    workspaceService.removeMember(id, keycloakId(jwt), userId);
  }

  private static UUID keycloakId(Jwt jwt) {
    return UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
  }
}
