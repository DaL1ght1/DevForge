package com.example.devforge.config.preview;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devforge.preview")
public record PreviewProperties(
    String provisionerUrl,
    Duration timeout,
    Duration ttl,
    int maxEntries,
    int maxConcurrent,
    Duration queueTimeout) {

  public PreviewProperties {
    if (provisionerUrl == null || provisionerUrl.isBlank())
      provisionerUrl = "http://localhost:8081";
    if (timeout == null) timeout = Duration.ofSeconds(15);
    if (ttl == null) ttl = Duration.ofMinutes(10);
    if (maxEntries <= 0) maxEntries = 200;
    if (maxConcurrent <= 0) maxConcurrent = 8;
    if (queueTimeout == null) queueTimeout = Duration.ofSeconds(2);
  }
}
