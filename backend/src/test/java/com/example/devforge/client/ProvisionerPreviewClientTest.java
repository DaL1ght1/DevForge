package com.example.devforge.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.devforge.config.preview.PreviewProperties;
import com.example.devforge.exception.InvalidTemplateSelectionException;
import com.example.devforge.exception.PreviewUnavailableException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProvisionerPreviewClientTest {

  private final PreviewProperties props =
      new PreviewProperties("http://provisioner.test", null, null, 0, 0, null);
  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final ProvisionerPreviewClient client =
      new ProvisionerPreviewClient(builder, props, "secret");

  private final ProvisionerClient.RenderRequest request =
      new ProvisionerClient.RenderRequest(
          "orders",
          "spring",
          "2.0.0",
          "d".repeat(64),
          "com.devforge.orders",
          "Orders",
          null,
          Map.of(),
          Map.of("database", List.of("postgresql")),
          null);

  @Test
  void postsRenderRequestWithInternalToken() {
    server
        .expect(requestTo("http://provisioner.test/internal/render"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Internal-Token", "secret"))
        .andExpect(jsonPath("$.artifactDigest").value("d".repeat(64)))
        .andExpect(jsonPath("$.features.database[0]").value("postgresql"))
        .andRespond(
            withSuccess(
                """
                {"files":[{"path":"pom.xml","origin":"base","size":10,"binary":false,"omitted":false,"content":"<p/>"}],
                 "totalBytes":10}
                """,
                MediaType.APPLICATION_JSON));

    ProvisionerClient.RenderResponse response = client.render(request);

    assertThat(response.files())
        .singleElement()
        .satisfies(f -> assertThat(f.content()).isEqualTo("<p/>"));
    server.verify();
  }

  @Test
  void renderErrorsBecomeSelectionErrorsWithTheProvisionerMessage() {
    server
        .expect(requestTo("http://provisioner.test/internal/render"))
        .andRespond(
            withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"rendering pom.xml.tmpl: missing JAVA_VERSION\"}"));

    assertThatThrownBy(() -> client.render(request))
        .isInstanceOf(InvalidTemplateSelectionException.class)
        .hasMessageContaining("missing JAVA_VERSION");
  }

  @Test
  void provisionerOutagesAreReportedAsUnavailable() {
    server
        .expect(requestTo("http://provisioner.test/internal/render"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.render(request))
        .isInstanceOf(PreviewUnavailableException.class);
  }

  @Test
  void missingTokenFailsWithoutCallingTheProvisioner() {
    ProvisionerPreviewClient unconfigured = new ProvisionerPreviewClient(builder, props, "");

    assertThatThrownBy(() -> unconfigured.render(request))
        .isInstanceOf(PreviewUnavailableException.class)
        .hasMessageContaining("INTERNAL_API_TOKEN");
    server.verify();
  }
}
