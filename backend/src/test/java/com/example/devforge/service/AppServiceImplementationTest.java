package com.example.devforge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.client.TemplateContextResolver;
import com.example.devforge.client.model.TemplateContext;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.AppTemplate;
import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.entity.User;
import com.example.devforge.exception.AppTemplateVersionNotFoundException;
import com.example.devforge.exception.UserNotFoundException;
import com.example.devforge.mapper.AppServiceMapper;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.implementation.AppServiceImplementation;
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
  private final AppServiceImplementation service =
      new AppServiceImplementation(
          appServiceRepository,
          userRepository,
          templateVersionRepository,
          appServiceMapper,
          contextResolver,
          eventPublisher,
          provisioningJobRepository);

  private final UUID userKeycloakId = UUID.randomUUID();
  private final UUID templateVersionId = UUID.randomUUID();
  private final AppServiceCreationDto dto =
      new AppServiceCreationDto("demo-service", "A demo", templateVersionId, "POSTGRESQL");
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
            .manifest("variables:\n  - name: DATABASE_TYPE\n    options: [POSTGRESQL]")
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
            dto.name(),
            "DemoService",
            "com.devforge.demo",
            "com/devforge/demo",
            dto.description(),
            dto.databaseType());

    when(templateVersionRepository.findById(templateVersionId))
        .thenReturn(Optional.of(templateVersion));
    when(userRepository.findByKeycloakId(userKeycloakId)).thenReturn(Optional.of(user));
    when(appServiceMapper.toEntity(dto)).thenReturn(mappedService);
    when(appServiceRepository.save(mappedService)).thenReturn(mappedService);
    when(contextResolver.resolve(dto)).thenReturn(context);

    AppService result = service.createService(userKeycloakId, dto);

    assertThat(result).isSameAs(mappedService);
    assertThat(mappedService.getOwner()).isSameAs(user);
    assertThat(mappedService.getTemplateVersion()).isSameAs(templateVersion);
    verify(eventPublisher)
        .publishEvent(
            new ProvisionRequestedEvent(
                new ProvisionerClient.ProvisionRequest(
                    serviceId,
                    "demo-service",
                    "spring",
                    "1.0.0",
                    "com.devforge.demo",
                    "DemoService",
                    "A demo",
                    "POSTGRESQL")));
    InOrder order = inOrder(appServiceRepository, provisioningJobRepository, eventPublisher);
    order.verify(appServiceRepository).save(mappedService);
    order.verify(provisioningJobRepository).save(any());
    order.verify(eventPublisher).publishEvent(any(ProvisionRequestedEvent.class));
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
  void createServiceRejectsDatabaseTypeNotSupportedByTemplate() {
    AppServiceCreationDto unsupported =
        new AppServiceCreationDto("demo-service", "A demo", templateVersionId, "MYSQL");
    when(templateVersionRepository.findById(templateVersionId))
        .thenReturn(Optional.of(templateVersion));

    assertThatThrownBy(() -> service.createService(userKeycloakId, unsupported))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(userRepository, appServiceRepository, eventPublisher);
  }
}
