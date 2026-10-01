package com.example.devforge.service;

import com.example.devforge.entity.AppDeployment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AppDeploymentService {

    Page<AppDeployment> getDeploymentsByService(UUID serviceId, Pageable pageable);
}
