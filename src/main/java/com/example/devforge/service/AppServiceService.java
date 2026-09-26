package com.example.devforge.service;

import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.entity.AppService;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface AppServiceService {

    AppService createService(UUID userId, AppServiceCreationDto appService);
    Page<AppService> listServices(int page, int size);
    AppService getService(UUID id);
    void deleteService(UUID id);
}
