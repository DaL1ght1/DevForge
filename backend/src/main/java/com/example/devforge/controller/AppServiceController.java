package com.example.devforge.controller;

import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.dto.AppServiceResponse;
import com.example.devforge.dto.PreviewFileResponse;
import com.example.devforge.dto.ServicePreviewResponse;
import com.example.devforge.mapper.AppServiceMapper;
import com.example.devforge.service.AppServiceService;
import com.example.devforge.service.ServicePreviewService;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/services")
public class AppServiceController {

  private final AppServiceService appServiceService;
  private final AppServiceMapper appServiceMapper;
  private final ServicePreviewService previewService;

  @GetMapping("/{id}")
  public AppServiceResponse getAppService(
      @AuthenticationPrincipal Jwt jwt, Authentication authentication, @PathVariable UUID id) {
    UUID keycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    return appServiceMapper.toResponse(
        appServiceService.getService(id, keycloakId, isAdmin(authentication)));
  }

  @GetMapping
  public Page<AppServiceResponse> getAllAppServices(
      @AuthenticationPrincipal Jwt jwt,
      Authentication authentication,
      @RequestParam(required = false) UUID workspaceId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    UUID keycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    return appServiceService
        .listServices(keycloakId, isAdmin(authentication), workspaceId, page, size)
        .map(appServiceMapper::toResponse);
  }

  @PostMapping
  public AppServiceResponse createAppService(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid AppServiceCreationDto appService) {
    UUID KeycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    return appServiceMapper.toResponse(appServiceService.createService(KeycloakId, appService));
  }

  @PostMapping("/preview")
  public ServicePreviewResponse previewAppService(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid AppServiceCreationDto appService) {
    UUID keycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    return previewService.preview(keycloakId, appService);
  }

  @GetMapping("/preview/{previewId}/file")
  public PreviewFileResponse previewFile(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String previewId, @RequestParam String path) {
    UUID keycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    return previewService.file(keycloakId, previewId, path);
  }

  @DeleteMapping("/{id}")
  public void deleteAppService(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    UUID keycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    appServiceService.deleteService(keycloakId, id);
  }

  private boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> Objects.equals(authority.getAuthority(), "ROLE_ADMIN"));
  }
}
