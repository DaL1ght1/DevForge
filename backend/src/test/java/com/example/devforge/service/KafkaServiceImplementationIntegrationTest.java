package com.example.devforge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.devforge.TestcontainersConfiguration;
import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.config.kafka.KafkaProperties;
import com.example.devforge.config.keycloak.KeycloakAdminClient;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.AppTemplate;
import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.entity.User;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.AppTemplateRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.repository.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(
    properties = {
      "KEYCLOAK_SERVER_URL=http://localhost",
      "KEYCLOAK_INTERNAL_SERVER_URL=http://localhost",
      "KEYCLOAK_TEST_REALM=DevForge-test",
      "KEYCLOAK_ADMIN_CLIENT_ID=devforge-admin-test",
      "KEYCLOAK_ADMIN_CLIENT_SECRET=test-secret",
      "KEYCLOAK_TEST_ADMIN_USERNAME=test-admin",
      "KEYCLOAK_TEST_ADMIN_EMAIL=test-admin@example.com",
      "KEYCLOAK_TEST_ADMIN_PASSWORD=TestAdminPassword123!",
      "KEYCLOAK_TEST_USER_USERNAME=test-user",
      "KEYCLOAK_TEST_USER_EMAIL=test-user@example.com",
      "KEYCLOAK_TEST_USER_PASSWORD=TestUserPassword123!",
      "DEVFORGE_ADMIN_USERNAME=admin",
      "DEVFORGE_ADMIN_EMAIL=admin@example.com",
      "DEVFORGE_ADMIN_PASSWORD=password",
      "DEVFORGE_ADMIN_FIRST_NAME=Admin",
      "DEVFORGE_ADMIN_LAST_NAME=User",
      "CORS_ALLOWED_ORIGINS=http://localhost",
      "REQUEST_TOPIC=integration-requests",
      "RESPONSE_TOPIC=integration-responses",
      "GROUP_ID=integration",
      "devforge.kafka.topics.partitions=1",
      "devforge.kafka.topics.replicas=1",
      "spring.kafka.consumer.auto-offset-reset=earliest"
    })
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class KafkaServiceImplementationIntegrationTest {

  @MockitoBean KeycloakAdminClient keycloakAdminClient;

  @Autowired private KafkaTemplate<String, ProvisionerClient.ProvisionResponse> kafkaTemplate;
  @Autowired private KafkaProperties kafkaProperties;
  @Autowired private KafkaContainer kafkaContainer;
  @Autowired private AppServiceService appServiceService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private AppServiceRepository appServiceRepository;
  @Autowired private ProvisioningJobRepository provisioningJobRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private AppTemplateRepository appTemplateRepository;
  @Autowired private TemplateVersionRepository templateVersionRepository;

  @BeforeEach
  void cleanDatabase() {
    provisioningJobRepository.deleteAll();
    appServiceRepository.deleteAll();
    templateVersionRepository.deleteAll();
    appTemplateRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void completedResponseMovesServiceToPushed() {
    AppService service = saveService();

    send(completed(service, "https://github.com/example/demo"));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              AppService persisted = appServiceRepository.findById(service.getId()).orElseThrow();
              assertThat(persisted.getStatus()).isEqualTo(ServiceStatus.PUSHED);
              assertThat(persisted.getRepositoryUrl()).isEqualTo("https://github.com/example/demo");
            });
  }

  @Test
  void failedResponseStoresFailureReason() {
    AppService service = saveService();

    send(
        new ProvisionerClient.ProvisionResponse(
            service.getId(), service.getName(), null, null, "FAILED", "GitHub unavailable"));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              AppService persisted = appServiceRepository.findById(service.getId()).orElseThrow();
              assertThat(persisted.getStatus()).isEqualTo(ServiceStatus.FAILED);
              assertThat(persisted.getFailureReason()).isEqualTo("GitHub unavailable");
            });
  }

  @Test
  void duplicateCompletedResponseIsHarmless() {
    AppService service = saveService();
    ProvisionerClient.ProvisionResponse response =
        completed(service, "https://github.com/example/demo");
    send(response);
    send(response);

    await()
        .atMost(Duration.ofSeconds(20))
        .during(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              AppService persisted = appServiceRepository.findById(service.getId()).orElseThrow();
              assertThat(persisted.getStatus()).isEqualTo(ServiceStatus.PUSHED);
              assertThat(persisted.getRepositoryUrl()).isEqualTo("https://github.com/example/demo");
            });
  }

  @Test
  void provisionRequestIsPublishedOnlyAfterCommit() {
    User user = saveUser();
    TemplateVersion version = saveTemplateVersion();
    AppServiceCreationDto dto =
        new AppServiceCreationDto(
            "ord-" + UUID.randomUUID().toString().substring(0, 8),
            "ordering test",
            version.getId(),
            "NONE");
    AtomicReference<UUID> serviceId = new AtomicReference<>();

    try (KafkaConsumer<String, String> consumer = requestConsumer()) {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                AppService created = appServiceService.createService(user.getKeycloakId(), dto);
                serviceId.set(created.getId());
                assertThat(pollFor(consumer, created.getId(), Duration.ofSeconds(3))).isEmpty();
              });
      assertThat(pollFor(consumer, serviceId.get(), Duration.ofSeconds(15))).hasSize(1);
    }
    assertThat(appServiceRepository.findById(serviceId.get())).isPresent();
  }

  private void send(ProvisionerClient.ProvisionResponse response) {
    kafkaTemplate
        .send(kafkaProperties.responseTopic(), response.serviceId().toString(), response)
        .join();
  }

  private ProvisionerClient.ProvisionResponse completed(AppService service, String repositoryUrl) {
    return new ProvisionerClient.ProvisionResponse(
        service.getId(), service.getName(), null, repositoryUrl, "COMPLETED", null);
  }

  private KafkaConsumer<String, String> requestConsumer() {
    Map<String, Object> config =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config);
    List<TopicPartition> partitions =
        consumer.partitionsFor(kafkaProperties.requestTopic()).stream()
            .map(info -> new TopicPartition(info.topic(), info.partition()))
            .toList();
    consumer.assign(partitions);
    consumer.seekToBeginning(partitions);
    return consumer;
  }

  private List<ConsumerRecord<String, String>> pollFor(
      KafkaConsumer<String, String> consumer, UUID serviceId, Duration timeout) {
    List<ConsumerRecord<String, String>> matches = new ArrayList<>();
    long deadline = System.nanoTime() + timeout.toNanos();
    do {
      for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
        if (serviceId.toString().equals(record.key())) {
          matches.add(record);
        }
      }
    } while (matches.isEmpty() && System.nanoTime() < deadline);
    return matches;
  }

  private User saveUser() {
    return userRepository.save(
        User.builder()
            .keycloakId(UUID.randomUUID())
            .username("user-" + UUID.randomUUID())
            .email(UUID.randomUUID() + "@example.com")
            .firstName("Test")
            .lastName("User")
            .build());
  }

  private TemplateVersion saveTemplateVersion() {
    AppTemplate template =
        appTemplateRepository.save(
            AppTemplate.builder()
                .name("s-" + UUID.randomUUID().toString().substring(0, 8))
                .stableKey("spring-" + UUID.randomUUID())
                .language(TemplateLanguage.JAVA)
                .framework(TemplateFramework.SPRING_BOOT)
                .buildTool(BuildTool.MAVEN)
                .build());
    return templateVersionRepository.save(
        TemplateVersion.builder()
            .template(template)
            .version("1.0.0")
            .sourcePath("templates/spring")
            .contentHash(UUID.randomUUID().toString())
            .manifest("variables: []")
            .artifactReference("test")
            .build());
  }

  private AppService saveService() {
    return appServiceRepository.save(
        AppService.builder()
            .name("service-" + UUID.randomUUID().toString().substring(0, 8))
            .owner(saveUser())
            .templateVersion(saveTemplateVersion())
            .status(ServiceStatus.CREATING)
            .build());
  }
}
