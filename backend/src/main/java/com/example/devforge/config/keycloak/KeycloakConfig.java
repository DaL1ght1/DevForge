package com.example.devforge.config.keycloak;

import com.example.devforge.config.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({KeycloakProperties.class, KafkaProperties.class})
public class KeycloakConfig {

  @Bean
  RestClient keycloakRestClient(KeycloakProperties props) {
    return RestClient.builder().baseUrl(props.serverUrl()).build();
  }
}
