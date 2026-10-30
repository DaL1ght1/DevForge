package com.example.devforge.service;

import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.dto.ApplicationCreationDto;
import com.example.devforge.dto.PreviewFileResponse;
import com.example.devforge.dto.ServicePreviewResponse;
import java.util.UUID;

public interface ServicePreviewService {

  ServicePreviewResponse preview(UUID keycloakId, AppServiceCreationDto dto);

  ServicePreviewResponse previewApplication(UUID keycloakId, ApplicationCreationDto dto);

  PreviewFileResponse file(UUID keycloakId, String previewId, String path);
}
