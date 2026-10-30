package com.example.devforge.service.implementation;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.client.ServiceBlueprintResolver;
import com.example.devforge.client.ServiceBlueprintResolver.Blueprint;
import com.example.devforge.client.model.TemplateContext;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.dto.ProvisionRequestedEvent;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.entity.GithubInstallation;
import com.example.devforge.entity.GithubInstallationStatus;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.entity.User;
import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceRole;
import com.example.devforge.exception.AppServiceNotFoundException;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.GithubNotConnectedException;
import com.example.devforge.exception.UserNotFoundException;
import com.example.devforge.exception.WorkspaceNotFoundException;
import com.example.devforge.mapper.AppServiceMapper;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.AppServiceService;
import com.example.devforge.service.WorkspaceService;
import com.example.devforge.template.TemplateSelectionResolver.ResolvedSelection;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class AppServiceImplementation implements AppServiceService {

  private final AppServiceRepository appServiceRepository;
  private final UserRepository userRepository;
  private final AppServiceMapper appServiceMapper;
  private final ApplicationEventPublisher eventPublisher;
  private final ProvisioningJobRepository provisioningJobRepository;
  private final WorkspaceService workspaceService;
  private final GithubInstallationRepository githubInstallationRepository;
  private final ServiceBlueprintResolver blueprints;

  @Override
  @Transactional
  public AppService createService(UUID userKeycloakId, AppServiceCreationDto appServiceDto) {
    Blueprint blueprint = blueprints.resolve(appServiceDto);
    TemplateVersion templateVersion = blueprint.templateVersion();
    ResolvedSelection selection = blueprint.selection();
    log.info("Provisioning service with template: {}", templateVersion.getTemplate().getName());
    User user =
        userRepository
            .findByKeycloakId(userKeycloakId)
            .orElseThrow(
                () ->
                    new UserNotFoundException("User not found with KeycloakId " + userKeycloakId));
    Workspace workspace =
        appServiceDto.workspaceId() == null
            ? workspaceService.getOrCreatePersonal(user)
            : workspaceService
                .requireRole(appServiceDto.workspaceId(), userKeycloakId, WorkspaceRole.ADMIN)
                .getWorkspace();
    if (appServiceRepository.existsByWorkspaceIdAndName(workspace.getId(), appServiceDto.name())) {
      throw new ConflictException(
          "A service named " + appServiceDto.name() + " already exists in this workspace");
    }
    // The destination comes only from a verified, active installation linked to the workspace.
    GithubInstallation installation =
        githubInstallationRepository
            .findFirstByWorkspaceIdAndStatusOrderByCreatedAtAsc(
                workspace.getId(), GithubInstallationStatus.ACTIVE)
            .orElseThrow(
                () ->
                    new GithubNotConnectedException(
                        "Connect a GitHub account to this workspace before creating services"));

    AppService service = appServiceMapper.toEntity(appServiceDto);
    service.setOwner(user);
    service.setWorkspace(workspace);
    service.setTemplateVersion(templateVersion);
    service.setStatus(ServiceStatus.CREATING);
    service.setRepositoryOwner(installation.getAccountLogin());
    AppService saved = appServiceRepository.save(service);
    ProvisioningJob job =
        provisioningJobRepository.save(
            ProvisioningJob.builder()
                .service(saved)
                .status(ProvisioningStatus.PENDING)
                .installationId(installation.getInstallationId())
                .build());
    TemplateContext ctx = blueprint.context();

    ProvisionerClient.ProvisionRequest provRequest =
        new ProvisionerClient.ProvisionRequest(
            ProvisionerClient.CONTRACT_VERSION,
            job.getId(),
            saved.getId(),
            null,
            ctx.serviceName(),
            templateVersion.getTemplate().getStableKey(),
            templateVersion.getVersion(),
            templateVersion.getArtifactDigest(),
            ctx.packageName(),
            ctx.className(),
            ctx.description(),
            selection.variables(),
            selection.features(),
            null,
            installation.getInstallationId(),
            installation.getAccountLogin(),
            installation.getAccountType() == GithubAccountType.ORGANIZATION
                ? "Organization"
                : "User",
            "private");
    eventPublisher.publishEvent(new ProvisionRequestedEvent(provRequest));
    log.info(
        "Service {} saved with id {}, provisioning requested", ctx.serviceName(), saved.getId());
    return saved;
  }

  @Override
  public Page<AppService> listServices(
      UUID keycloakId, boolean admin, UUID workspaceId, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    if (workspaceId != null) {
      if (!admin) {
        workspaceService.requireRole(workspaceId, keycloakId, WorkspaceRole.MEMBER);
      }
      return appServiceRepository.findAllByWorkspaceId(workspaceId, pageable);
    }
    return admin
        ? appServiceRepository.findAll(pageable)
        : appServiceRepository.findAllVisibleToMember(keycloakId, pageable);
  }

  @Override
  public AppService getService(UUID id, UUID keycloakId, boolean admin) {
    return (admin
            ? appServiceRepository.findById(id)
            : appServiceRepository.findByIdVisibleToMember(id, keycloakId))
        .orElseThrow(() -> new AppServiceNotFoundException(id));
  }

  @Override
  @Transactional
  public void deleteService(UUID userKeycloakId, UUID serviceId) {
    AppService service =
        appServiceRepository
            .findById(serviceId)
            .orElseThrow(() -> new AppServiceNotFoundException(serviceId));
    try {
      workspaceService.requireRole(
          service.getWorkspace().getId(), userKeycloakId, WorkspaceRole.ADMIN);
    } catch (WorkspaceNotFoundException e) {
      throw new AppServiceNotFoundException(serviceId);
    }
    appServiceRepository.delete(service);
  }
}
