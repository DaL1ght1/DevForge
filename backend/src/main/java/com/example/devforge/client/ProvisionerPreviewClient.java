package com.example.devforge.client;

import com.example.devforge.config.preview.PreviewProperties;
import com.example.devforge.exception.InvalidTemplateSelectionException;
import com.example.devforge.exception.PreviewUnavailableException;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ProvisionerPreviewClient {

  private final RestClient restClient;
  private final String internalToken;

  @Autowired
  public ProvisionerPreviewClient(
      PreviewProperties props, @Value("${devforge.internal.api-token:}") String internalToken) {
    this(RestClient.builder().requestFactory(requestFactory(props)), props, internalToken);
  }

  ProvisionerPreviewClient(
      RestClient.Builder builder, PreviewProperties props, String internalToken) {
    this.restClient = builder.baseUrl(props.provisionerUrl()).build();
    this.internalToken = internalToken;
  }

  private static JdkClientHttpRequestFactory requestFactory(PreviewProperties props) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(props.timeout()).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(props.timeout());
    return factory;
  }

  public ProvisionerClient.RenderResponse render(ProvisionerClient.RenderRequest request) {
    if (internalToken == null || internalToken.isBlank()) {
      throw new PreviewUnavailableException("Preview is not configured (INTERNAL_API_TOKEN)");
    }
    try {
      ProvisionerClient.RenderResponse response =
          restClient
              .post()
              .uri("/internal/render")
              .header("X-Internal-Token", internalToken)
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(ProvisionerClient.RenderResponse.class);
      if (response == null || response.files() == null) {
        throw new PreviewUnavailableException("Provisioner returned an empty preview");
      }
      return response;
    } catch (HttpStatusCodeException e) {
      if (e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_ENTITY)) {
        throw new InvalidTemplateSelectionException(
            "Template could not be rendered: " + renderError(e));
      }
      log.warn("Provisioner preview failed with {}", e.getStatusCode());
      throw new PreviewUnavailableException("Preview service is unavailable", e);
    } catch (RestClientException e) {
      log.warn("Provisioner preview unreachable: {}", e.getMessage());
      throw new PreviewUnavailableException("Preview service is unavailable", e);
    }
  }

  private static String renderError(HttpStatusCodeException e) {
    try {
      RenderError error = e.getResponseBodyAs(RenderError.class);
      if (error != null && error.message() != null) return error.message();
    } catch (RuntimeException ignored) {
    }
    return "invalid template output";
  }

  private record RenderError(String message) {}
}
