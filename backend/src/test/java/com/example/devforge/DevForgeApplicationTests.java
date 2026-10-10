package com.example.devforge;

import com.example.devforge.config.keycloak.KeycloakAdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class DevForgeApplicationTests {

  @MockitoBean KeycloakAdminClient keycloakAdminClient;

  @Test
  void contextLoads() {}
}
