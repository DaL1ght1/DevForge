package com.example.devforge.service;

import com.example.devforge.entity.AppDeployment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AppDeploymentService {

  Page<AppDeployment> getDeploymentsByService(UUID serviceId, Pageable pageable);
}
