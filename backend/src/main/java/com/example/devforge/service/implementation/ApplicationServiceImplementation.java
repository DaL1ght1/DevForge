package com.example.devforge.service.implementation;

import com.example.devforge.client.ApplicationBlueprintResolver;
import com.example.devforge.client.ApplicationBlueprintResolver.ApplicationBlueprint;
import com.example.devforge.client.ApplicationBlueprintResolver.ServiceEntry;
import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.dto.ApplicationCreationDto;
import com.example.devforge.dto.ApplicationResponse;
import com.example.devforge.dto.ProvisionRequestedEvent;
import com.example.devforge.dto.WorkspaceSummaryResponse;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.Application;
import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.entity.GithubInstallation;
import com.example.devforge.entity.GithubInstallationStatus;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.entity.User;
import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceRole;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.GithubNotConnectedException;
import com.example.devforge.exception.ResourceNotFoundException;
import com.example.devforge.exception.UserNotFoundException;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.ApplicationRepository;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.ApplicationService;
import com.example.devforge.service.WorkspaceService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImplementation implements ApplicationService {

  private final ApplicationRepository applications;
  private final AppServiceRepository services;
  private final UserRepository users;
  private final ProvisioningJobRepository jobs;
  private final GithubInstallationRepository installations;
  private final WorkspaceService workspaceService;
  private final ApplicationBlueprintResolver blueprints;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public ApplicationResponse createApplication(UUID keycloakId, ApplicationCreationDto dto) {
    ApplicationBlueprint blueprint = blueprints.resolve(dto);
    User user =
        users
            .findByKeycloakId(keycloakId)
            .orElseThrow(
                () -> new UserNotFoundException("User not found with KeycloakId " + keycloakId));
    Workspace workspace =
        dto.workspaceId() == null
            ? workspaceService.getOrCreatePersonal(user)
            : workspaceService
                .requireRole(dto.workspaceId(), keycloakId, WorkspaceRole.ADMIN)
                .getWorkspace();
    if (applications.existsByWorkspaceIdAndName(workspace.getId(), blueprint.name())) {
      throw new ConflictException(
          "An application named " + blueprint.name() + " already exists in this workspace");
    }
    for (ServiceEntry entry : blueprint.services()) {
      if (services.existsByWorkspaceIdAndName(workspace.getId(), entry.name())) {
        throw new ConflictException(
            "A service named " + entry.name() + " already exists in this workspace");
      }
    }
    GithubInstallation installation =
        installations
            .findFirstByWorkspaceIdAndStatusOrderByCreatedAtAsc(
                workspace.getId(), GithubInstallationStatus.ACTIVE)
            .orElseThrow(
                () ->
                    new GithubNotConnectedException(
                        "Connect a GitHub account to this workspace before creating applications"));

    Application application =
        applications.save(
            Application.builder()
                .name(blueprint.name())
                .description(blueprint.description())
                .workspace(workspace)
                .owner(user)
                .status(ServiceStatus.CREATING)
                .repositoryOwner(installation.getAccountLogin())
                .build());
    List<AppService> saved = new ArrayList<>();
    for (ServiceEntry entry : blueprint.services()) {
      saved.add(
          services.save(
              AppService.builder()
                  .name(entry.name())
                  .description(entry.blueprint().context().description())
                  .templateVersion(entry.blueprint().templateVersion())
                  .owner(user)
                  .workspace(workspace)
                  .application(application)
                  .port(entry.port())
                  .status(ServiceStatus.CREATING)
                  .repositoryOwner(installation.getAccountLogin())
                  .build()));
    }
    application.getServices().addAll(saved);
    ProvisioningJob job =
        jobs.save(
            ProvisioningJob.builder()
                .service(saved.getFirst())
                .application(application)
                .status(ProvisioningStatus.PENDING)
                .installationId(installation.getInstallationId())
                .build());

    eventPublisher.publishEvent(
        new ProvisionRequestedEvent(
            new ProvisionerClient.ProvisionRequest(
                ProvisionerClient.CONTRACT_VERSION,
                job.getId(),
                saved.getFirst().getId(),
                application.getId(),
                application.getName(),
                null,
                null,
                null,
                null,
                null,
                application.getDescription(),
                null,
                null,
                blueprint.specs(i -> saved.get(i).getId()),
                installation.getInstallationId(),
                installation.getAccountLogin(),
                installation.getAccountType() == GithubAccountType.ORGANIZATION
                    ? "Organization"
                    : "User",
                "private")));
    log.info(
        "Application {} saved with {} services, provisioning requested",
        application.getName(),
        saved.size());
    return toResponse(application, saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ApplicationResponse> listApplications(
      UUID keycloakId, boolean admin, UUID workspaceId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Application> found;
    if (workspaceId != null) {
      if (!admin) workspaceService.requireRole(workspaceId, keycloakId, WorkspaceRole.MEMBER);
      found = applications.findAllByWorkspaceId(workspaceId, pageable);
    } else {
      found =
          admin
              ? applications.findAll(pageable)
              : applications.findAllVisibleToMember(keycloakId, pageable);
    }
    return found.map(a -> toResponse(a, services.findAllByApplicationIdOrderByPort(a.getId())));
  }

  @Override
  @Transactional(readOnly = true)
  public ApplicationResponse getApplication(UUID id, UUID keycloakId, boolean admin) {
    Application application =
        (admin ? applications.findById(id) : applications.findByIdVisibleToMember(id, keycloakId))
            .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    return toResponse(application, services.findAllByApplicationIdOrderByPort(id));
  }

  static ApplicationResponse toResponse(Application application, List<AppService> appServices) {
    Workspace workspace = application.getWorkspace();
    return new ApplicationResponse(
        application.getId(),
        application.getName(),
        application.getDescription(),
        application.getStatus(),
        application.getRepositoryUrl(),
        application.getRepositoryOwner(),
        application.getFailureReason(),
        new WorkspaceSummaryResponse(
            workspace.getId(), workspace.getName(), workspace.getSlug(), workspace.getType()),
        application.getCreatedAt(),
        application.getUpdatedAt(),
        appServices.stream()
            .map(
                s ->
                    new ApplicationResponse.ServiceSummary(
                        s.getId(),
                        s.getName(),
                        "services/" + s.getName(),
                        s.getPort(),
                        s.getStatus(),
                        s.getTemplateVersion().getTemplate().getName(),
                        s.getTemplateVersion().getVersion(),
                        s.getRepositoryUrl()))
            .toList());
  }
}
