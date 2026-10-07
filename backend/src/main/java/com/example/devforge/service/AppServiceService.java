package com.example.devforge.service;

import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.entity.AppService;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface AppServiceService {

  AppService createService(UUID userId, AppServiceCreationDto appService);

  Page<AppService> listServices(int page, int size);

  AppService getService(UUID id);

  void deleteService(UUID userKeycloakId, UUID serviceId);
}
