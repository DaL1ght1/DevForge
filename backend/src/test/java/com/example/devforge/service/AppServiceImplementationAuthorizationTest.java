package com.example.devforge.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.devforge.client.ServiceBlueprintResolver;
import com.example.devforge.client.TemplateContextResolver;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.AppTemplate;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.entity.User;
import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceMember;
import com.example.devforge.entity.WorkspaceRole;
import com.example.devforge.entity.WorkspaceType;
import com.example.devforge.exception.AppServiceNotFoundException;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.UnauthorizedAccessException;
import com.example.devforge.exception.WorkspaceNotFoundException;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.implementation.AppServiceImplementation;
import com.example.devforge.template.TemplateManifestCache;
import com.example.devforge.template.TemplateManifestParser;
import com.example.devforge.template.TemplateSelectionResolver;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

class AppServiceImplementationAuthorizationTest {

  private static final String TEST_MANIFEST =
      """
      name: spring
      version: 1.0.0
      language: JAVA
      framework: SPRING_BOOT
      buildTool: MAVEN
      """;

  private final AppServiceRepository repository = mock(AppServiceRepository.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final TemplateVersionRepository templateVersionRepository =
      mock(TemplateVersionRepository.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final WorkspaceService workspaceService = mock(WorkspaceService.class);
  private final AppServiceImplementation service =
      new AppServiceImplementation(
          repository,
          userRepository,
          null,
          eventPublisher,
          mock(ProvisioningJobRepository.class),
          workspaceService,
          mock(GithubInstallationRepository.class),
          new ServiceBlueprintResolver(
              templateVersionRepository,
              new TemplateManifestCache(new TemplateManifestParser()),
              new TemplateSelectionResolver(),
              new TemplateContextResolver()));

  private final UUID userA = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final Workspace org =
      Workspace.builder().id(orgId).type(WorkspaceType.ORGANIZATION).name("Acme").build();

  @Test
  void userCannotReadServiceOutsideTheirWorkspaces() {
    UUID serviceId = UUID.randomUUID();
    when(repository.findByIdVisibleToMember(serviceId, userA)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getService(serviceId, userA, false))
        .isInstanceOf(AppServiceNotFoundException.class);
    verify(repository).findByIdVisibleToMember(serviceId, userA);
    verify(repository, never()).findById(serviceId);
  }

  @Test
  void nonMemberCannotDeleteServiceAndLearnsNothing() {
    UUID serviceId = UUID.randomUUID();
    when(repository.findById(serviceId))
        .thenReturn(Optional.of(AppService.builder().id(serviceId).workspace(org).build()));
    when(workspaceService.requireRole(orgId, userA, WorkspaceRole.ADMIN))
        .thenThrow(new WorkspaceNotFoundException(orgId));

    assertThatThrownBy(() -> service.deleteService(userA, serviceId))
        .isInstanceOf(AppServiceNotFoundException.class);
    verify(repository, never()).delete(any(AppService.class));
  }

  @Test
  void plainMemberCannotDeleteOrganizationService() {
    UUID serviceId = UUID.randomUUID();
    when(repository.findById(serviceId))
        .thenReturn(Optional.of(AppService.builder().id(serviceId).workspace(org).build()));
    when(workspaceService.requireRole(orgId, userA, WorkspaceRole.ADMIN))
        .thenThrow(new UnauthorizedAccessException("requires ADMIN"));

    assertThatThrownBy(() -> service.deleteService(userA, serviceId))
        .isInstanceOf(UnauthorizedAccessException.class);
    verify(repository, never()).delete(any(AppService.class));
  }

  @Test
  void plainMemberCannotCreateInOrganization() {
    stubTemplateAndUser();
    when(workspaceService.requireRole(orgId, userA, WorkspaceRole.ADMIN))
        .thenThrow(new UnauthorizedAccessException("requires ADMIN"));

    assertThatThrownBy(() -> service.createService(userA, orgDto("billing")))
        .isInstanceOf(UnauthorizedAccessException.class);
    verify(repository, never()).save(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void nonMemberCannotCreateInOrganization() {
    stubTemplateAndUser();
    when(workspaceService.requireRole(orgId, userA, WorkspaceRole.ADMIN))
        .thenThrow(new WorkspaceNotFoundException(orgId));

    assertThatThrownBy(() -> service.createService(userA, orgDto("billing")))
        .isInstanceOf(WorkspaceNotFoundException.class);
    verify(repository, never()).save(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void duplicateNameInSameWorkspaceIsRejected() {
    stubTemplateAndUser();
    when(workspaceService.requireRole(orgId, userA, WorkspaceRole.ADMIN))
        .thenReturn(WorkspaceMember.builder().workspace(org).role(WorkspaceRole.ADMIN).build());
    when(repository.existsByWorkspaceIdAndName(orgId, "billing")).thenReturn(true);

    assertThatThrownBy(() -> service.createService(userA, orgDto("billing")))
        .isInstanceOf(ConflictException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void listingAnotherWorkspaceRequiresMembership() {
    when(workspaceService.requireRole(orgId, userA, WorkspaceRole.MEMBER))
        .thenThrow(new WorkspaceNotFoundException(orgId));

    assertThatThrownBy(() -> service.listServices(userA, false, orgId, 0, 10))
        .isInstanceOf(WorkspaceNotFoundException.class);
    verify(repository, never()).findAllByWorkspaceId(any(), any(Pageable.class));
  }

  private AppServiceCreationDto orgDto(String name) {
    return new AppServiceCreationDto(name, null, UUID.randomUUID(), orgId);
  }

  private void stubTemplateAndUser() {
    TemplateVersion version =
        TemplateVersion.builder()
            .template(AppTemplate.builder().name("Spring").stableKey("spring").build())
            .version("1.0.0")
            .manifest(TEST_MANIFEST)
            .build();
    when(templateVersionRepository.findById(any())).thenReturn(Optional.of(version));
    when(userRepository.findByKeycloakId(userA))
        .thenReturn(Optional.of(User.builder().id(UUID.randomUUID()).keycloakId(userA).build()));
  }
}
