package com.example.devforge.config.github;

import com.example.devforge.exception.GithubIntegrationException;
import java.io.Serializable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "devforge.github")
public record GithubAppProperties(
    String appSlug,
    String clientId,
    String clientSecret,
    String webhookSecret,
    String tokenEncryptionKey,
    String apiUrl,
    String webUrl)
    implements Serializable {

  public GithubAppProperties {
    apiUrl = StringUtils.hasText(apiUrl) ? apiUrl : "https://api.github.com";
    webUrl = StringUtils.hasText(webUrl) ? webUrl : "https://github.com";
  }

  public void requireConfigured() {
    if (!StringUtils.hasText(appSlug)
        || !StringUtils.hasText(clientId)
        || !StringUtils.hasText(clientSecret)) {
      throw new GithubIntegrationException(
          "GitHub App is not configured (GITHUB_APP_SLUG, GITHUB_APP_CLIENT_ID, "
              + "GITHUB_APP_CLIENT_SECRET)");
    }
  }
}
