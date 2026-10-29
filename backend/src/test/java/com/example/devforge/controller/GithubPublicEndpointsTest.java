package com.example.devforge.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.devforge.TestcontainersConfiguration;
import com.example.devforge.config.keycloak.KeycloakAdminClient;
import com.example.devforge.security.AuthCookies;
import jakarta.servlet.http.Cookie;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "devforge.github.webhook-secret=" + GithubPublicEndpointsTest.SECRET,
      "devforge.frontend-url=http://front"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class GithubPublicEndpointsTest {

  static final String SECRET = "test-webhook-secret";
  private static final String PING = "{\"zen\":\"Keep it logically awesome.\",\"hook_id\":1}";

  @MockitoBean KeycloakAdminClient keycloakAdminClient;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void signedWebhookIsAcceptedWithoutAuthenticationAndRecordedOnce() throws Exception {
    String delivery = UUID.randomUUID().toString();

    for (int i = 0; i < 2; i++) {
      mockMvc
          .perform(webhook(delivery, sign(PING)).content(PING))
          .andExpect(status().isNoContent());
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM github_webhook_delivery WHERE delivery_id = ?",
                Integer.class,
                delivery))
        .isEqualTo(1);
  }

  @Test
  void staleSessionCookieDoesNotBreakWebhook() throws Exception {
    mockMvc
        .perform(
            webhook(UUID.randomUUID().toString(), sign(PING))
                .content(PING)
                .cookie(new Cookie(AuthCookies.ACCESS, "expired.jwt.value")))
        .andExpect(status().isNoContent());
  }

  @Test
  void unsignedWebhookIsRejected() throws Exception {
    mockMvc
        .perform(webhook(UUID.randomUUID().toString(), "sha256=00").content(PING))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("webhook signature")));
  }

  @Test
  void callbackWithForgedStateRedirectsToFrontendWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/github/callback").param("code", "x").param("state", "forged"))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string("Location", "http://front/workspaces?github=error&reason=invalid_state"));
  }

  @Test
  void githubManagementEndpointsStillRequireAuthentication() throws Exception {
    mockMvc
        .perform(get("/github/installations").param("workspaceId", UUID.randomUUID().toString()))
        .andExpect(status().isUnauthorized());
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhook(
      String delivery, String signature) {
    return post("/github/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-GitHub-Event", "ping")
        .header("X-GitHub-Delivery", delivery)
        .header("X-Hub-Signature-256", signature);
  }

  private static String sign(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(SECRET.getBytes(UTF_8), "HmacSHA256"));
      return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
