package com.example.devforge.controller;

import com.example.devforge.dto.InternalUserTokenResponse;
import com.example.devforge.service.ProvisioningCredentialService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Called only by the Go provisioner, authenticated with the shared X-Internal-Token header. */
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/provisioning")
public class InternalProvisioningController {

  private final ProvisioningCredentialService credentialService;

  @PostMapping("/jobs/{jobId}/user-token")
  public InternalUserTokenResponse userToken(
      @RequestHeader(name = "X-Internal-Token", required = false) String internalToken,
      @PathVariable UUID jobId) {
    return new InternalUserTokenResponse(credentialService.userTokenForJob(internalToken, jobId));
  }
}
