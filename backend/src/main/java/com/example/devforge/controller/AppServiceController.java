package com.example.devforge.controller;

import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.dto.AppServiceResponse;
import com.example.devforge.mapper.AppServiceMapper;
import com.example.devforge.service.AppServiceService;
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
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    UUID keycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    return appServiceService
        .listServices(keycloakId, isAdmin(authentication), page, size)
        .map(appServiceMapper::toResponse);
  }

  @PostMapping
  public AppServiceResponse createAppService(
      @AuthenticationPrincipal Jwt jwt, @RequestBody @Valid AppServiceCreationDto appService) {
    UUID KeycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    return appServiceMapper.toResponse(appServiceService.createService(KeycloakId, appService));
  }

  @DeleteMapping("/{id}")
  public void deleteAppService(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    UUID keycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    appServiceService.deleteService(keycloakId, id);
  }

  private boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
  }
}
