package com.example.devforge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.client.ServiceBlueprintResolver;
import com.example.devforge.client.TemplateContextResolver;
import com.example.devforge.client.model.TemplateContext;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.dto.ProvisionRequestedEvent;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.AppTemplate;
import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.entity.GithubInstallation;
import com.example.devforge.entity.GithubInstallationStatus;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.entity.User;
import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceType;
import com.example.devforge.exception.AppTemplateVersionNotFoundException;
import com.example.devforge.exception.GithubNotConnectedException;
import com.example.devforge.exception.UserNotFoundException;
import com.example.devforge.mapper.AppServiceMapper;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.implementation.AppServiceImplementation;
import com.example.devforge.template.TemplateManifestCache;
import com.example.devforge.template.TemplateManifestParser;
import com.example.devforge.template.TemplateSelectionResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class AppServiceImplementationTest {

  private final AppServiceRepository appServiceRepository = mock(AppServiceRepository.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final TemplateVersionRepository templateVersionRepository =
      mock(TemplateVersionRepository.class);
  private final AppServiceMapper appServiceMapper = mock(AppServiceMapper.class);
  private final TemplateContextResolver contextResolver = mock(TemplateContextResolver.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final ProvisioningJobRepository provisioningJobRepository =
      mock(ProvisioningJobRepository.class);
  private final WorkspaceService workspaceService = mock(WorkspaceService.class);
  private final GithubInstallationRepository githubInstallationRepository =
      mock(GithubInstallationRepository.class);
  private final AppServiceImplementation service =
      new AppServiceImplementation(
          appServiceRepository,
          userRepository,
          appServiceMapper,
          eventPublisher,
          provisioningJobRepository,
          workspaceService,
          githubInstallationRepository,
          new ServiceBlueprintResolver(
              templateVersionRepository,
              new TemplateManifestCache(new TemplateManifestParser()),
              new TemplateSelectionResolver(),
              contextResolver));

  private final UUID userKeycloakId = UUID.randomUUID();
  private final UUID templateVersionId = UUID.randomUUID();
  private final AppServiceCreationDto dto =
      new AppServiceCreationDto(
          "demo-service",
          "A demo",
          templateVersionId,
          null,
          null,
          Map.of("database", List.of("postgresql")));
  private TemplateVersion templateVersion;
  private User user;

  @BeforeEach
  void setUp() {
    AppTemplate template =
        AppTemplate.builder()
            .name("Spring")
            .stableKey("spring")
            .language(TemplateLanguage.JAVA)
            .framework(TemplateFramework.SPRING_BOOT)
            .buildTool(BuildTool.MAVEN)
            .build();
    templateVersion =
        TemplateVersion.builder()
            .id(templateVersionId)
            .template(template)
            .version("1.0.0")
            .artifactDigest("a".repeat(64))
            .manifest(
                """
                schemaVersion: 2
                name: spring
                version: 1.0.0
                language: JAVA
                framework: SPRING_BOOT
                buildTool: MAVEN
                variables:
                  - name: JAVA_VERSION
                    options: ["21", "25"]
                    default: "21"
                features:
                  - id: database
                    kind: choice
                    options:
                      - id: postgresql
                      - id: mysql
                  - id: extras
                    options:
                      - id: testcontainers
                        requires: [database]
                """)
            .build();
    user = User.builder().keycloakId(userKeycloakId).build();
  }

  @Test
  void createServiceSavesServiceAndRequestsProvisioningAfterSave() {
    AppService mappedService = AppService.builder().name(dto.name()).build();
    UUID serviceId = UUID.randomUUID();
    mappedService.setId(serviceId);
    TemplateContext context =
        new TemplateContext(
            dto.name(), "DemoService", "com.devforge.demo", "com/devforge/demo", dto.description());

    when(templateVersionRepository.findById(templateVersionId))
        .thenReturn(Optional.of(templateVersion));
    Workspace personal =
        Workspace.builder().id(UUID.randomUUID()).type(WorkspaceType.PERSONAL).build();
    when(userRepository.findByKeycloakId(userKeycloakId)).thenReturn(Optional.of(user));
    when(workspaceService.getOrCreatePersonal(user)).thenReturn(personal);
    when(appServiceMapper.toEntity(dto)).thenReturn(mappedService);
    when(appServiceRepository.save(mappedService)).thenReturn(mappedService);
    when(contextResolver.resolve(dto)).thenReturn(context);
    when(githubInstallationRepository.findFirstByWorkspaceIdAndStatusOrderByCreatedAtAsc(
            personal.getId(), GithubInstallationStatus.ACTIVE))
        .thenReturn(
            Optional.of(
                GithubInstallation.builder()
                    .installationId(77L)
                    .accountLogin("alice")
                    .accountType(GithubAccountType.USER)
                    .build()));
    UUID jobId = UUID.randomUUID();
    when(provisioningJobRepository.save(any()))
        .thenAnswer(
            inv -> {
              ProvisioningJob job = inv.getArgument(0);
              job.setId(jobId);
              return job;
            });

    AppService result = service.createService(userKeycloakId, dto);

    assertThat(result).isSameAs(mappedService);
    assertThat(mappedService.getOwner()).isSameAs(user);
    assertThat(mappedService.getWorkspace()).isSameAs(personal);
    assertThat(mappedService.getTemplateVersion()).isSameAs(templateVersion);
    assertThat(mappedService.getRepositoryOwner()).isEqualTo("alice");
    verify(eventPublisher)
        .publishEvent(
            new ProvisionRequestedEvent(
                new ProvisionerClient.ProvisionRequest(
                    ProvisionerClient.CONTRACT_VERSION,
                    jobId,
                    serviceId,
                    null,
                    "demo-service",
                    "spring",
                    "1.0.0",
                    "a".repeat(64),
                    "com.devforge.demo",
                    "DemoService",
                    "A demo",
                    Map.of("JAVA_VERSION", "21"),
                    Map.of("database", List.of("postgresql")),
                    null,
                    77L,
                    "alice",
                    "User",
                    "private")));
    InOrder order = inOrder(appServiceRepository, provisioningJobRepository, eventPublisher);
    order.verify(appServiceRepository).save(mappedService);
    order.verify(provisioningJobRepository).save(any());
    order.verify(eventPublisher).publishEvent(any(ProvisionRequestedEvent.class));
  }

  @Test
  void createServiceRequiresConnectedGithub() {
    Workspace personal =
        Workspace.builder().id(UUID.randomUUID()).type(WorkspaceType.PERSONAL).build();
    when(templateVersionRepository.findById(templateVersionId))
        .thenReturn(Optional.of(templateVersion));
    when(userRepository.findByKeycloakId(userKeycloakId)).thenReturn(Optional.of(user));
    when(workspaceService.getOrCreatePersonal(user)).thenReturn(personal);
    when(githubInstallationRepository.findFirstByWorkspaceIdAndStatusOrderByCreatedAtAsc(
            personal.getId(), GithubInstallationStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createService(userKeycloakId, dto))
        .isInstanceOf(GithubNotConnectedException.class);
    verify(appServiceRepository, never()).save(any());
    verifyNoInteractions(provisioningJobRepository, eventPublisher);
  }

  @Test
  void createServiceRejectsUnknownTemplateVersion() {
    when(templateVersionRepository.findById(templateVersionId)).thenReturn(Optional.empty());
    when(templateVersionRepository.findFirstByTemplateIdAndActiveTrue(templateVersionId))
        .thenReturn(Optional.empty());
    when(templateVersionRepository.findFirstByTemplateId(templateVersionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createService(userKeycloakId, dto))
        .isInstanceOf(AppTemplateVersionNotFoundException.class);
    verifyNoInteractions(userRepository, appServiceRepository, eventPublisher);
  }

  @Test
  void createServiceRejectsUnknownUser() {
    when(templateVersionRepository.findById(templateVersionId))
        .thenReturn(Optional.of(templateVersion));
    when(userRepository.findByKeycloakId(userKeycloakId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createService(userKeycloakId, dto))
        .isInstanceOf(UserNotFoundException.class);
    verifyNoInteractions(appServiceRepository, provisioningJobRepository, eventPublisher);
  }

  @Test
  void createServiceRejectsOptionNotOfferedByTemplate() {
    assertRejected(Map.of(), Map.of("database", List.of("mongodb")), "Unknown option mongodb");
  }

  @Test
  void createServiceRejectsTwoOptionsInAChoiceGroup() {
    assertRejected(Map.of(), Map.of("database", List.of("postgresql", "mysql")), "single option");
  }

  @Test
  void createServiceRejectsUnmetFeatureRequirement() {
    assertRejected(
        Map.of(), Map.of("extras", List.of("testcontainers")), "testcontainers requires database");
  }

  @Test
  void createServiceRejectsVariableOutsideOptions() {
    assertRejected(Map.of("JAVA_VERSION", "8"), Map.of(), "JAVA_VERSION must be one of");
  }

  @Test
  void createServiceRejectsReservedVariable() {
    assertRejected(Map.of("SERVICE_NAME", "x"), Map.of(), "SERVICE_NAME is set by DevForge");
  }

  private void assertRejected(
      Map<String, String> variables, Map<String, List<String>> features, String message) {
    AppServiceCreationDto invalid =
        new AppServiceCreationDto(
            "demo-service", "A demo", templateVersionId, null, variables, features);
    when(templateVersionRepository.findById(templateVersionId))
        .thenReturn(Optional.of(templateVersion));

    assertThatThrownBy(() -> service.createService(userKeycloakId, invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(message);
    verifyNoInteractions(userRepository, appServiceRepository, eventPublisher);
  }
}
