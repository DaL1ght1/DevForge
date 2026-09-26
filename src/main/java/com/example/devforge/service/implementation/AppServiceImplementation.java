package com.example.devforge.service.implementation;

import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.entity.User;
import com.example.devforge.exception.AppServiceNotFoundException;
import com.example.devforge.exception.AppTemplateVersionNotFoundException;
import com.example.devforge.exception.UserNotFoundException;
import com.example.devforge.mapper.AppServiceMapper;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.AppServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppServiceImplementation implements AppServiceService {

    private final AppServiceRepository appServiceRepository;
    private final UserRepository userRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final AppServiceMapper appServiceMapper;

    @Override
    @Transactional
    public AppService createService(UUID userId, AppServiceCreationDto appService) {
        TemplateVersion templateVersion = templateVersionRepository.findById(appService.templateVersionId())
                .orElseThrow(() -> new AppTemplateVersionNotFoundException(appService.templateVersionId()));
        User user = userRepository.findByKeycloakId(userId).orElseThrow(() -> new UserNotFoundException("User not found with id " + userId));
        AppService service = appServiceMapper.toEntity(appService);
        service.setOwner(user);
        service.setTemplateVersion(templateVersion);
        appServiceRepository.save(service);
        return service;
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
    public void deleteService(UUID id) {
        AppService service = appServiceRepository.findById(id).orElseThrow(() -> new AppServiceNotFoundException(id));
        appServiceRepository.delete(service);
    }


}
