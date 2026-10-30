package com.example.devforge.controller;

import com.example.devforge.dto.ApplicationCreationDto;
import com.example.devforge.dto.ApplicationResponse;
import com.example.devforge.dto.PreviewFileResponse;
import com.example.devforge.dto.ServicePreviewResponse;
import com.example.devforge.service.ApplicationService;
import com.example.devforge.service.ServicePreviewService;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/applications")
public class ApplicationController {

  private final ApplicationService applicationService;
  private final ServicePreviewService previewService;

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ApplicationResponse createApplication(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid ApplicationCreationDto dto) {
    return applicationService.createApplication(keycloakId(jwt), dto);
  }

  @PostMapping("/preview")
  public ServicePreviewResponse previewApplication(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid ApplicationCreationDto dto) {
    return previewService.previewApplication(keycloakId(jwt), dto);
  }

  @GetMapping("/preview/{previewId}/file")
  public PreviewFileResponse previewFile(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String previewId, @RequestParam String path) {
    return previewService.file(keycloakId(jwt), previewId, path);
  }

  @GetMapping
  public Page<ApplicationResponse> listApplications(
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication,
      @RequestParam(required = false) UUID workspaceId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return applicationService.listApplications(
        keycloakId(jwt), isAdmin(authentication), workspaceId, page, size);
  }

  @GetMapping("/{id}")
  public ApplicationResponse getApplication(
      @AuthenticationPrincipal Jwt jwt, Authentication authentication, @PathVariable UUID id) {
    return applicationService.getApplication(id, keycloakId(jwt), isAdmin(authentication));
  }

  private static UUID keycloakId(Jwt jwt) {
    return UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
  }

  private static boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> Objects.equals(authority.getAuthority(), "ROLE_ADMIN"));
  }
}
