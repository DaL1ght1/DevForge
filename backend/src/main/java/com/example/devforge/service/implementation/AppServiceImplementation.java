package com.example.devforge.service.implementation;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.entity.User;
import com.example.devforge.exception.AppServiceNotFoundException;
import com.example.devforge.exception.AppTemplateVersionNotFoundException;
import com.example.devforge.exception.UnauthorizedAccessException;
import com.example.devforge.exception.UserNotFoundException;
import com.example.devforge.generator.TemplateContextResolver;
import com.example.devforge.generator.model.TemplateContext;
import com.example.devforge.mapper.AppServiceMapper;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.AppServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AppServiceImplementation implements AppServiceService {

    private final AppServiceRepository appServiceRepository;
    private final UserRepository userRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final AppServiceMapper appServiceMapper;
    private final ProvisionerClient provisionerClient;
    private final TemplateContextResolver contextResolver;

    @Override
    @Transactional
    public AppService createService(UUID userKeycloakId, AppServiceCreationDto appServiceDto) {
        TemplateVersion templateVersion = templateVersionRepository.findById(appServiceDto.templateVersionId())
                .orElseThrow(() -> new AppTemplateVersionNotFoundException(appServiceDto.templateVersionId()));
        log.info("Provisioning service with template: {}", templateVersion.getTemplate().getName());
        User user = userRepository.findByKeycloakId(userKeycloakId)
                .orElseThrow(() -> new UserNotFoundException("User not found with KeycloakId " + userKeycloakId));

        AppService service = appServiceMapper.toEntity(appServiceDto);
        service.setOwner(user);
        service.setTemplateVersion(templateVersion);
        service.setStatus(ServiceStatus.CREATING);

        AppService saved = appServiceRepository.save(service);

        TemplateContext ctx = contextResolver.resolve(appServiceDto);

        ProvisionerClient.ProvisionRequest provRequest = new ProvisionerClient.ProvisionRequest(
                ctx.serviceName(),
                templateVersion.getTemplate().getName(),
                ctx.packageName(),
                ctx.className(),
                ctx.description(),
                ctx.databaseType()
        );

        ProvisionerClient.ProvisionResponse response = provisionerClient.provision(provRequest);

        log.info("Provisioned service at repository: {}", response.repositoryUrl());

        saved.setRepositoryUrl(response.repositoryUrl());
        saved.setStatus(ServiceStatus.PENDING);

        return appServiceRepository.save(saved);
    }

    @Override
    public Page<AppService> listServices(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return appServiceRepository.findAll(pageable);
    }

    @Override
    public AppService getService(UUID id) {
        return appServiceRepository.findById(id).orElseThrow(() -> new AppServiceNotFoundException(id));
    }

    @Override
    @Transactional
    public void deleteService(UUID userKeycloakId, UUID serviceId) {
        AppService service = appServiceRepository.findById(serviceId).orElseThrow(() -> new AppServiceNotFoundException(serviceId));
        if (!service.getOwner().getKeycloakId().equals(userKeycloakId)) {
            throw new UnauthorizedAccessException("User not authorized to delete this service");
        }
        appServiceRepository.delete(service);
    }


}
