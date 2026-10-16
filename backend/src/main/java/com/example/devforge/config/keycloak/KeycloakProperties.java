package com.example.devforge.config.keycloak;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "devforge.keycloak")
public record KeycloakProperties(
    @NotBlank String serverUrl,
    String publicServerUrl,
    @NotBlank String realm,
    String clientId,
    @NotBlank String adminClientId,
    @NotBlank String adminClientSecret) {

  public String effectivePublicServerUrl() {
    return (publicServerUrl != null && !publicServerUrl.isBlank()) ? publicServerUrl : serverUrl;
  }

  public String effectiveClientId() {
    return (clientId != null && !clientId.isBlank()) ? clientId : "devforge-dev";
  }
}
