package com.example.devforge.controller;

import com.example.devforge.dto.InternalJobClaimResponse;
import com.example.devforge.dto.InternalRepositoryRequest;
import com.example.devforge.dto.InternalUserTokenResponse;
import com.example.devforge.service.InternalProvisioningService;
import com.example.devforge.service.ProvisioningCredentialService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Called only by the Go provisioner, authenticated with the shared X-Internal-Token header. */
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalProvisioningController {

  private static final String TOKEN_HEADER = "X-Internal-Token";

  private final ProvisioningCredentialService credentialService;
  private final InternalProvisioningService provisioningService;

  @PostMapping("/provisioning/jobs/{jobId}/user-token")
  public InternalUserTokenResponse userToken(
      @RequestHeader(name = TOKEN_HEADER, required = false) String internalToken,
      @PathVariable UUID jobId) {
    return new InternalUserTokenResponse(credentialService.userTokenForJob(internalToken, jobId));
  }

  @PostMapping("/provisioning/jobs/{jobId}/claim")
  public InternalJobClaimResponse claim(
      @RequestHeader(name = TOKEN_HEADER, required = false) String internalToken,
      @PathVariable UUID jobId) {
    return provisioningService.claim(internalToken, jobId);
  }

  @PutMapping("/provisioning/jobs/{jobId}/repository")
  public ResponseEntity<Void> recordRepository(
      @RequestHeader(name = TOKEN_HEADER, required = false) String internalToken,
      @PathVariable UUID jobId,
      @Valid @RequestBody InternalRepositoryRequest request) {
    provisioningService.recordRepository(internalToken, jobId, request.repositoryId());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/templates/artifacts/{digest}")
  public ResponseEntity<byte[]> templateArtifact(
      @RequestHeader(name = TOKEN_HEADER, required = false) String internalToken,
      @PathVariable String digest) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/zip"))
        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
        .eTag('"' + digest + '"')
        .body(provisioningService.templateArtifact(internalToken, digest));
  }
}
