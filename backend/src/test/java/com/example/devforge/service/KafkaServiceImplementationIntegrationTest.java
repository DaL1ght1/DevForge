package com.example.devforge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.example.devforge.TestcontainersConfiguration;
import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.config.kafka.KafkaProperties;
import com.example.devforge.config.keycloak.KeycloakAdminClient;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.dto.ApplicationCreationDto;
import com.example.devforge.dto.ApplicationResponse;
import com.example.devforge.dto.InternalJobClaimResponse;
import com.example.devforge.dto.TemplateSynchronizationResult;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.AppTemplate;
import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.entity.GithubInstallation;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.entity.TemplateArtifact;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.entity.User;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.InvalidCredentialsException;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.AppTemplateRepository;
import com.example.devforge.repository.ApplicationRepository;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.OutboxEventRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.repository.TemplateArtifactRepository;
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
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "devforge.internal.api-token=" + KafkaServiceImplementationIntegrationTest.INTERNAL_TOKEN
    })
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class KafkaServiceImplementationIntegrationTest {

  static final String INTERNAL_TOKEN = "integration-internal-token";

  private static final String TEST_MANIFEST =
      """
      name: spring
      version: 1.0.0
      language: JAVA
      framework: SPRING_BOOT
      buildTool: MAVEN
      """;

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
  @Autowired private WorkspaceService workspaceService;
  @Autowired private GithubInstallationRepository githubInstallationRepository;
  @Autowired private OutboxEventRepository outboxEventRepository;
  @Autowired private InternalProvisioningService internalProvisioningService;
  @Autowired private ApplicationService applicationService;
  @Autowired private ApplicationRepository applicationRepository;
  @Autowired private TemplateSynchronizer templateSynchronizer;
  @Autowired private TemplateArtifactRepository templateArtifactRepository;

  @BeforeEach
  void cleanDatabase() {
    githubInstallationRepository.deleteAll();
    outboxEventRepository.deleteAll();
    provisioningJobRepository.deleteAll();
    appServiceRepository.deleteAll();
    applicationRepository.deleteAll();
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
            null,
            service.getId(),
            null,
            service.getName(),
            null,
            null,
            null,
            "FAILED",
            "GitHub unavailable"));

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
  void responseForAnotherServicesJobIsIgnored() {
    AppService target = saveService();
    AppService other = saveService();
    ProvisioningJob otherJob = saveJob(other);

    send(
        new ProvisionerClient.ProvisionResponse(
            otherJob.getId(),
            target.getId(),
            null,
            target.getName(),
            null,
            "https://github.com/example/hijack",
            1L,
            "COMPLETED",
            null));
    send(completed(other, otherJob, "https://github.com/example/other"));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(appServiceRepository.findById(other.getId()).orElseThrow().getStatus())
                    .isEqualTo(ServiceStatus.PUSHED));
    AppService untouched = appServiceRepository.findById(target.getId()).orElseThrow();
    assertThat(untouched.getStatus()).isEqualTo(ServiceStatus.CREATING);
    assertThat(untouched.getRepositoryUrl()).isNull();
  }

  @Test
  void lateResponseForFinishedJobDoesNotOverwriteResult() {
    AppService service = saveService();
    ProvisioningJob job = saveJob(service);
    send(completed(service, job, "https://github.com/example/demo"));
    send(
        new ProvisionerClient.ProvisionResponse(
            job.getId(),
            service.getId(),
            null,
            service.getName(),
            null,
            null,
            null,
            "FAILED",
            "late"));

    await()
        .atMost(Duration.ofSeconds(20))
        .during(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              AppService persisted = appServiceRepository.findById(service.getId()).orElseThrow();
              assertThat(persisted.getStatus()).isEqualTo(ServiceStatus.PUSHED);
              assertThat(persisted.getRepositoryId()).isEqualTo(42L);
            });
  }

  @Test
  void provisionRequestIsPublishedOnlyAfterCommit() {
    User user = saveUser();
    githubInstallationRepository.save(
        GithubInstallation.builder()
            .installationId(Math.abs(UUID.randomUUID().getMostSignificantBits()))
            .accountId(1L)
            .accountLogin("example")
            .accountType(GithubAccountType.USER)
            .workspace(workspaceService.getOrCreatePersonal(user))
            .build());
    TemplateVersion version = saveTemplateVersion();
    AppServiceCreationDto dto =
        new AppServiceCreationDto(
            "ord-" + UUID.randomUUID().toString().substring(0, 8),
            "ordering test",
            version.getId());
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

  @Test
  void provisionRequestIsRecordedInOutboxAndMarkedSent() {
    User user = saveUser();
    saveInstallation(user);
    TemplateVersion version = saveTemplateVersion();

    AppService created =
        appServiceService.createService(
            user.getKeycloakId(),
            new AppServiceCreationDto(
                "obx-" + UUID.randomUUID().toString().substring(0, 8), null, version.getId()));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              assertThat(outboxEventRepository.findAll())
                  .singleElement()
                  .satisfies(e -> assertThat(e.getSentAt()).isNotNull());
              ProvisioningJob job =
                  provisioningJobRepository
                      .findTopByServiceIdOrderByCreatedAtDesc(created.getId())
                      .orElseThrow();
              assertThat(job.getStatus()).isEqualTo(ProvisioningStatus.GENERATING);
            });
  }

  @Test
  void claimIsIdempotentAndRefusedOnceTheJobFinished() {
    ProvisioningJob job = saveJob(saveService());

    InternalJobClaimResponse first = internalProvisioningService.claim(INTERNAL_TOKEN, job.getId());
    internalProvisioningService.recordRepository(INTERNAL_TOKEN, job.getId(), 99L);
    InternalJobClaimResponse redelivered =
        internalProvisioningService.claim(INTERNAL_TOKEN, job.getId());

    assertThat(first.proceed()).isTrue();
    assertThat(first.repositoryId()).isNull();
    assertThat(redelivered.proceed()).isTrue();
    assertThat(redelivered.attempt()).isEqualTo(first.attempt() + 1);
    assertThat(redelivered.repositoryId()).isEqualTo(99L);

    ProvisioningJob finished = provisioningJobRepository.findById(job.getId()).orElseThrow();
    finished.setStatus(ProvisioningStatus.COMPLETED);
    provisioningJobRepository.save(finished);

    assertThat(internalProvisioningService.claim(INTERNAL_TOKEN, job.getId()).proceed()).isFalse();
    assertThatThrownBy(
            () -> internalProvisioningService.recordRepository(INTERNAL_TOKEN, job.getId(), 1L))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> internalProvisioningService.claim("wrong", job.getId()))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void applicationIsProvisionedAsOneJobAndCompletesAllItsServices() {
    User user = saveUser();
    saveInstallation(user);
    TemplateVersion version = saveTemplateVersion();
    String suffix = UUID.randomUUID().toString().substring(0, 6);

    ApplicationResponse created =
        applicationService.createApplication(
            user.getKeycloakId(),
            new ApplicationCreationDto(
                "shop-" + suffix,
                "Shop",
                null,
                List.of(
                    new ApplicationCreationDto.Service(
                        "ord-" + suffix, null, version.getId(), null, null),
                    new ApplicationCreationDto.Service(
                        "bil-" + suffix, null, version.getId(), null, null))));

    assertThat(created.services())
        .extracting(ApplicationResponse.ServiceSummary::port)
        .containsExactly(8080, 8081);
    ProvisioningJob job =
        await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () ->
                    provisioningJobRepository
                        .findTopByServiceIdOrderByCreatedAtDesc(created.services().getFirst().id())
                        .filter(j -> j.getStatus() == ProvisioningStatus.GENERATING)
                        .orElse(null),
                java.util.Objects::nonNull);
    assertThat(outboxEventRepository.findAll())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getPayload()).contains("\"applicationId\":\"" + created.id());
              assertThat(e.getPayload()).contains("\"port\":8081");
            });

    send(
        new ProvisionerClient.ProvisionResponse(
            job.getId(),
            created.services().getFirst().id(),
            created.id(),
            created.name(),
            null,
            "https://github.com/example/" + created.name(),
            77L,
            "COMPLETED",
            null));

    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              ApplicationResponse app =
                  applicationService.getApplication(created.id(), user.getKeycloakId(), true);
              assertThat(app.status()).isEqualTo(ServiceStatus.PUSHED);
              assertThat(app.repositoryUrl())
                  .isEqualTo("https://github.com/example/" + created.name());
              assertThat(app.services())
                  .allSatisfy(s -> assertThat(s.status()).isEqualTo(ServiceStatus.PUSHED))
                  .extracting(ApplicationResponse.ServiceSummary::repositoryUrl)
                  .containsExactly(
                      app.repositoryUrl() + "/tree/main/services/ord-" + suffix,
                      app.repositoryUrl() + "/tree/main/services/bil-" + suffix);
            });
  }

  @Test
  void applicationNameMustBeUniqueInTheWorkspace() {
    User user = saveUser();
    saveInstallation(user);
    TemplateVersion version = saveTemplateVersion();
    ApplicationCreationDto dto =
        new ApplicationCreationDto(
            "dup-app",
            null,
            null,
            List.of(
                new ApplicationCreationDto.Service(
                    "dup-svc-1", null, version.getId(), null, null)));
    applicationService.createApplication(user.getKeycloakId(), dto);

    assertThatThrownBy(
            () ->
                applicationService.createApplication(
                    user.getKeycloakId(),
                    new ApplicationCreationDto(
                        "dup-app",
                        null,
                        null,
                        List.of(
                            new ApplicationCreationDto.Service(
                                "dup-svc-2", null, version.getId(), null, null)))))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void syncAdoptsCurrentContentForVersionsWithoutAnArtifact() {
    TemplateVersion legacy = saveLegacyGoVersion(null);

    TemplateSynchronizationResult result = templateSynchronizer.synchronize();

    TemplateVersion adopted = templateVersionRepository.findById(legacy.getId()).orElseThrow();
    assertThat(result.failed()).isZero();
    assertThat(adopted.getArtifactDigest()).hasSize(64);
    assertThat(adopted.getContentHash()).isNotEqualTo(legacy.getContentHash());
    assertThat(adopted.getManifest()).contains("go_Gin_Go_Modules");
    assertThat(templateArtifactRepository.existsById(adopted.getArtifactDigest())).isTrue();
  }

  @Test
  void syncRefusesToChangeAVersionThatHasAnArtifact() {
    templateArtifactRepository.save(
        TemplateArtifact.builder()
            .digest("a".repeat(64))
            .sizeBytes(1)
            .data(new byte[] {1})
            .build());
    TemplateVersion stored = saveLegacyGoVersion("a".repeat(64));

    TemplateSynchronizationResult result = templateSynchronizer.synchronize();

    TemplateVersion unchanged = templateVersionRepository.findById(stored.getId()).orElseThrow();
    assertThat(result.failed()).isEqualTo(1);
    assertThat(unchanged.getContentHash()).isEqualTo(stored.getContentHash());
    assertThat(unchanged.getArtifactDigest()).isEqualTo("a".repeat(64));
  }

  private TemplateVersion saveLegacyGoVersion(String artifactDigest) {
    AppTemplate template =
        appTemplateRepository.save(
            AppTemplate.builder()
                .name("legacy-go")
                .stableKey("go_Gin_Go_Modules")
                .language(TemplateLanguage.JAVA)
                .framework(TemplateFramework.SPRING_BOOT)
                .buildTool(BuildTool.MAVEN)
                .build());
    return templateVersionRepository.save(
        TemplateVersion.builder()
            .template(template)
            .version("1.0.0")
            .sourcePath("go_Gin_Go_Modules")
            .contentHash("0".repeat(64))
            .manifest(TEST_MANIFEST)
            .artifactReference("go_Gin_Go_Modules")
            .artifactDigest(artifactDigest)
            .build());
  }

  private void saveInstallation(User user) {
    githubInstallationRepository.save(
        GithubInstallation.builder()
            .installationId(Math.abs(UUID.randomUUID().getMostSignificantBits()))
            .accountId(1L)
            .accountLogin("example")
            .accountType(GithubAccountType.USER)
            .workspace(workspaceService.getOrCreatePersonal(user))
            .build());
  }

  private void send(ProvisionerClient.ProvisionResponse response) {
    kafkaTemplate
        .send(kafkaProperties.responseTopic(), response.serviceId().toString(), response)
        .join();
  }

  private ProvisionerClient.ProvisionResponse completed(AppService service, String repositoryUrl) {
    return new ProvisionerClient.ProvisionResponse(
        null,
        service.getId(),
        null,
        service.getName(),
        null,
        repositoryUrl,
        null,
        "COMPLETED",
        null);
  }

  private ProvisionerClient.ProvisionResponse completed(
      AppService service, ProvisioningJob job, String repositoryUrl) {
    return new ProvisionerClient.ProvisionResponse(
        job.getId(),
        service.getId(),
        null,
        service.getName(),
        null,
        repositoryUrl,
        42L,
        "COMPLETED",
        null);
  }

  private ProvisioningJob saveJob(AppService service) {
    return provisioningJobRepository.save(
        ProvisioningJob.builder()
            .service(service)
            .status(ProvisioningStatus.GENERATING)
            .installationId(1L)
            .build());
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
            .manifest(TEST_MANIFEST)
            .artifactReference("test")
            .build());
  }

  private AppService saveService() {
    User owner = saveUser();
    return appServiceRepository.save(
        AppService.builder()
            .name("service-" + UUID.randomUUID().toString().substring(0, 8))
            .owner(owner)
            .workspace(workspaceService.getOrCreatePersonal(owner))
            .templateVersion(saveTemplateVersion())
            .status(ServiceStatus.CREATING)
            .build());
  }
}
