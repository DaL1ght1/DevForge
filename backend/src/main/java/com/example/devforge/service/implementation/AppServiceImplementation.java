package com.example.devforge.service.implementation;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.client.TemplateContextResolver;
import com.example.devforge.client.model.TemplateContext;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.entity.User;
import com.example.devforge.exception.AppServiceNotFoundException;
import com.example.devforge.exception.AppTemplateVersionNotFoundException;
import com.example.devforge.exception.UnauthorizedAccessException;
import com.example.devforge.exception.UserNotFoundException;
import com.example.devforge.mapper.AppServiceMapper;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.AppServiceService;
import com.example.devforge.service.ProvisionRequestedEvent;
import java.util.List;
import java.util.Map;
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
import org.yaml.snakeyaml.Yaml;

@Service
@Slf4j
@RequiredArgsConstructor
public class AppServiceImplementation implements AppServiceService {

  private final AppServiceRepository appServiceRepository;
  private final UserRepository userRepository;
  private final TemplateVersionRepository templateVersionRepository;
  private final AppServiceMapper appServiceMapper;
  private final TemplateContextResolver contextResolver;
  private final ApplicationEventPublisher eventPublisher;
  private final ProvisioningJobRepository provisioningJobRepository;
  private final Yaml yaml = new Yaml();

  @Override
  @Transactional
  public AppService createService(UUID userKeycloakId, AppServiceCreationDto appServiceDto) {
    validateDescription(appServiceDto.description());
    TemplateVersion templateVersion =
        templateVersionRepository
            .findById(appServiceDto.templateVersionId())
            .or(
                () ->
                    templateVersionRepository.findFirstByTemplateIdAndActiveTrue(
                        appServiceDto.templateVersionId()))
            .or(
                () ->
                    templateVersionRepository.findFirstByTemplateId(
                        appServiceDto.templateVersionId()))
            .orElseThrow(
                () -> new AppTemplateVersionNotFoundException(appServiceDto.templateVersionId()));
    validateDatabaseType(templateVersion, appServiceDto.databaseType());
    log.info("Provisioning service with template: {}", templateVersion.getTemplate().getName());
    User user =
        userRepository
            .findByKeycloakId(userKeycloakId)
            .orElseThrow(
                () ->
                    new UserNotFoundException("User not found with KeycloakId " + userKeycloakId));

    AppService service = appServiceMapper.toEntity(appServiceDto);
    service.setOwner(user);
    service.setTemplateVersion(templateVersion);
    service.setStatus(ServiceStatus.CREATING);
    AppService saved = appServiceRepository.save(service);
    provisioningJobRepository.save(
        ProvisioningJob.builder().service(saved).status(ProvisioningStatus.PENDING).build());
    TemplateContext ctx = contextResolver.resolve(appServiceDto);

    ProvisionerClient.ProvisionRequest provRequest =
        new ProvisionerClient.ProvisionRequest(
            saved.getId(),
            ctx.serviceName(),
            templateVersion.getTemplate().getStableKey(),
            templateVersion.getVersion(),
            ctx.packageName(),
            ctx.className(),
            ctx.description(),
            ctx.databaseType());
    eventPublisher.publishEvent(new ProvisionRequestedEvent(provRequest));
    log.info(
        "Service {} saved with id {}, provisioning requested", ctx.serviceName(), saved.getId());
    return saved;
  }

  @Override
  public Page<AppService> listServices(UUID keycloakId, boolean admin, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    return admin
        ? appServiceRepository.findAll(pageable)
        : appServiceRepository.findAllByOwnerKeycloakId(keycloakId, pageable);
  }

  @Override
  public AppService getService(UUID id, UUID keycloakId, boolean admin) {
    return (admin
            ? appServiceRepository.findById(id)
            : appServiceRepository.findByIdAndOwnerKeycloakId(id, keycloakId))
        .orElseThrow(() -> new AppServiceNotFoundException(id));
  }

  @Override
  @Transactional
  public void deleteService(UUID userKeycloakId, UUID serviceId) {
    AppService service =
        appServiceRepository
            .findById(serviceId)
            .orElseThrow(() -> new AppServiceNotFoundException(serviceId));
    if (!service.getOwner().getKeycloakId().equals(userKeycloakId)) {
      throw new UnauthorizedAccessException("User not authorized to delete this service");
    }
    appServiceRepository.delete(service);
  }

  private void validateDescription(String description) {
    if (description != null
        && (description.length() > 500
            || description
                .codePoints()
                .anyMatch(
                    codePoint ->
                        Character.isISOControl(codePoint)
                            && codePoint != '\n'
                            && codePoint != '\r'
                            && codePoint != '\t'))) {
      throw new IllegalArgumentException(
          "Description contains unsupported characters or is too long");
    }
  }

  private void validateDatabaseType(TemplateVersion templateVersion, String databaseType) {
    if (databaseType == null || databaseType.isBlank()) {
      throw new IllegalArgumentException("databaseType must not be blank");
    }
    Object manifest = yaml.load(templateVersion.getManifest());
    if (!(manifest instanceof Map<?, ?> manifestMap)) {
      throw new IllegalArgumentException("Template manifest is invalid");
    }
    Object variables = manifestMap.get("variables");
    if (!(variables instanceof List<?> variableList)) {
      return;
    }
    for (Object variable : variableList) {
      if (variable instanceof Map<?, ?> variableMap
          && "DATABASE_TYPE".equals(String.valueOf(variableMap.get("name")))) {
        Object options = variableMap.get("options");
        if (options instanceof List<?> optionList
            && optionList.stream().map(String::valueOf).noneMatch(databaseType::equals)) {
          throw new IllegalArgumentException(
              "databaseType is not supported by the selected template");
        }
        return;
      }
    }
  }
}
