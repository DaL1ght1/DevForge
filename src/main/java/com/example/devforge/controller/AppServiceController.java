package com.example.devforge.controller;

import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.dto.AppServiceResponse;
import com.example.devforge.mapper.AppServiceMapper;
import com.example.devforge.service.AppServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/services")
public class AppServiceController {

    private final AppServiceService appServiceService;
    private final AppServiceMapper appServiceMapper;

    @GetMapping("/{id}")
    public AppServiceResponse getAppService(@PathVariable UUID id) {
        return appServiceMapper.toResponse(appServiceService.getService(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AppServiceResponse> getAllAppServices(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return appServiceService.listServices(page, size)
                .map(appServiceMapper::toResponse);
    }

    @PostMapping
    public AppServiceResponse createAppService(/*@AuthenticationPrincipal Jwt jwt,*/ @RequestBody @Valid AppServiceCreationDto appService) {
       // UUID KeycloakId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        UUID KeycloakId = UUID.fromString("5edf9415-1b39-4a0d-a051-aa84ee2cb739");
        return appServiceMapper.toResponse(appServiceService.createService(KeycloakId, appService));

    }

    @DeleteMapping("/{id}")
    public void deleteAppService(@AuthenticationPrincipal Jwt jwt ,@PathVariable UUID id) {
        UUID keycloakId  = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        appServiceService.deleteService(keycloakId, id);
    }

}
