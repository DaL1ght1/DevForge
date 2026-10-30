package com.example.devforge.service;

import com.example.devforge.dto.ApplicationCreationDto;
import com.example.devforge.dto.ApplicationResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface ApplicationService {

  ApplicationResponse createApplication(UUID keycloakId, ApplicationCreationDto dto);

  Page<ApplicationResponse> listApplications(
      UUID keycloakId, boolean admin, UUID workspaceId, int page, int size);

  ApplicationResponse getApplication(UUID id, UUID keycloakId, boolean admin);
}
