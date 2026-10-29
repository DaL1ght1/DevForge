package com.example.devforge.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.example.devforge.config.github.GithubAppProperties;
import com.example.devforge.entity.GithubInstallation;
import com.example.devforge.entity.GithubInstallationStatus;
import com.example.devforge.exception.InvalidWebhookSignatureException;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.GithubWebhookDeliveryRepository;
import com.example.devforge.service.implementation.GithubUserTokens;
import com.example.devforge.service.implementation.GithubWebhookServiceImplementation;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GithubWebhookServiceImplementationTest {

  private static final String SECRET = "webhook-secret";

  private final GithubWebhookDeliveryRepository deliveryRepository =
      mock(GithubWebhookDeliveryRepository.class);
  private final GithubInstallationRepository installationRepository =
      mock(GithubInstallationRepository.class);
  private final GithubUserTokens userTokens = mock(GithubUserTokens.class);
  private final GithubWebhookServiceImplementation service =
      new GithubWebhookServiceImplementation(
          new GithubAppProperties(null, null, null, SECRET, null, null, null),
          deliveryRepository,
          installationRepository,
          userTokens,
          JsonMapper.builder().build());

  @Test
  void badSignatureIsRejectedBeforeAnything() {
    byte[] body = "{\"action\":\"deleted\",\"installation\":{\"id\":1}}".getBytes(UTF_8);

    assertThatThrownBy(() -> service.handle("installation", "d1", "sha256=00ff", body))
        .isInstanceOf(InvalidWebhookSignatureException.class);
    assertThatThrownBy(() -> service.handle("installation", "d1", null, body))
        .isInstanceOf(InvalidWebhookSignatureException.class);
    verifyNoInteractions(deliveryRepository, installationRepository);
  }

  @Test
  void deletedInstallationIsMarkedRemoved() {
    GithubInstallation installation =
        GithubInstallation.builder()
            .installationId(1)
            .status(GithubInstallationStatus.ACTIVE)
            .build();
    when(deliveryRepository.markReceived("d1", "installation")).thenReturn(true);
    when(installationRepository.findByInstallationId(1)).thenReturn(Optional.of(installation));

    send("installation", "d1", "{\"action\":\"deleted\",\"installation\":{\"id\":1}}");

    assertThat(installation.getStatus()).isEqualTo(GithubInstallationStatus.REMOVED);
  }

  @Test
  void suspendThenUnsuspendRestoresActive() {
    GithubInstallation installation = GithubInstallation.builder().installationId(1).build();
    when(deliveryRepository.markReceived(anyString(), eq("installation"))).thenReturn(true);
    when(installationRepository.findByInstallationId(1)).thenReturn(Optional.of(installation));

    send("installation", "d1", "{\"action\":\"suspend\",\"installation\":{\"id\":1}}");
    assertThat(installation.getStatus()).isEqualTo(GithubInstallationStatus.SUSPENDED);
    send("installation", "d2", "{\"action\":\"unsuspend\",\"installation\":{\"id\":1}}");
    assertThat(installation.getStatus()).isEqualTo(GithubInstallationStatus.ACTIVE);
  }

  @Test
  void redeliveryHasNoEffect() {
    when(deliveryRepository.markReceived("d1", "installation")).thenReturn(false);

    send("installation", "d1", "{\"action\":\"deleted\",\"installation\":{\"id\":1}}");

    verify(installationRepository, never()).findByInstallationId(anyLong());
  }

  @Test
  void revokedAuthorizationClearsUserTokens() {
    when(deliveryRepository.markReceived("d1", "github_app_authorization")).thenReturn(true);

    send("github_app_authorization", "d1", "{\"action\":\"revoked\",\"sender\":{\"id\":42}}");

    verify(userTokens).revoke(42L);
  }

  private void send(String event, String deliveryId, String json) {
    byte[] body = json.getBytes(UTF_8);
    service.handle(event, deliveryId, sign(body), body);
  }

  private static String sign(byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(SECRET.getBytes(UTF_8), "HmacSHA256"));
      return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
