package com.example.devforge.service.implementation;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.devforge.config.github.GithubAppProperties;
import com.example.devforge.entity.GithubInstallationStatus;
import com.example.devforge.exception.InvalidWebhookSignatureException;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.GithubWebhookDeliveryRepository;
import com.example.devforge.service.GithubWebhookService;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubWebhookServiceImplementation implements GithubWebhookService {

  private static final String SIGNATURE_PREFIX = "sha256=";

  private final GithubAppProperties props;
  private final GithubWebhookDeliveryRepository deliveryRepository;
  private final GithubInstallationRepository installationRepository;
  private final GithubUserTokens userTokens;
  private final JsonMapper jsonMapper;

  @Override
  @Transactional
  public void handle(String event, String deliveryId, String signature, byte[] body) {
    verifySignature(signature, body);
    if (!StringUtils.hasText(event) || !StringUtils.hasText(deliveryId)) {
      throw new IllegalArgumentException("Missing X-GitHub-Event or X-GitHub-Delivery header");
    }
    if (!deliveryRepository.markReceived(deliveryId, event)) {
      log.info("Ignoring redelivered GitHub webhook {} ({})", deliveryId, event);
      return;
    }
    JsonNode payload = jsonMapper.readTree(body);
    String action = payload.path("action").asString("");
    switch (event) {
      case "installation" -> onInstallation(payload, action);
      case "installation_repositories" -> onRepositorySelection(payload);
      case "github_app_authorization" -> {
        if ("revoked".equals(action)) {
          userTokens.revoke(payload.path("sender").path("id").asLong());
          log.info("GitHub user revoked the DevForge App authorization");
        }
      }
      default -> log.debug("Ignoring GitHub webhook event {}", event);
    }
  }

  private void onInstallation(JsonNode payload, String action) {
    GithubInstallationStatus status =
        switch (action) {
          case "deleted" -> GithubInstallationStatus.REMOVED;
          case "suspend" -> GithubInstallationStatus.SUSPENDED;
          case "unsuspend" -> GithubInstallationStatus.ACTIVE;
          default -> null;
        };
    if (status == null) {
      return;
    }
    long installationId = payload.path("installation").path("id").asLong();
    installationRepository
        .findByInstallationId(installationId)
        .ifPresent(
            installation -> {
              installation.setStatus(status);
              log.info("GitHub installation {} is now {}", installationId, status);
            });
  }

  private void onRepositorySelection(JsonNode payload) {
    long installationId = payload.path("installation").path("id").asLong();
    String selection = payload.path("repository_selection").asString(null);
    if (selection == null) {
      return;
    }
    installationRepository
        .findByInstallationId(installationId)
        .ifPresent(installation -> installation.setRepositorySelection(selection));
  }

  private void verifySignature(String signature, byte[] body) {
    if (!StringUtils.hasText(props.webhookSecret())) {
      throw new InvalidWebhookSignatureException("GitHub webhook secret is not configured");
    }
    if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
      throw new InvalidWebhookSignatureException("Missing webhook signature");
    }
    byte[] expected;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(props.webhookSecret().getBytes(UTF_8), "HmacSHA256"));
      expected = mac.doFinal(body);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 unavailable", e);
    }
    byte[] provided;
    try {
      provided = HexFormat.of().parseHex(signature.substring(SIGNATURE_PREFIX.length()));
    } catch (IllegalArgumentException e) {
      throw new InvalidWebhookSignatureException("Malformed webhook signature");
    }
    if (!MessageDigest.isEqual(expected, provided)) {
      throw new InvalidWebhookSignatureException("Invalid webhook signature");
    }
  }
}
