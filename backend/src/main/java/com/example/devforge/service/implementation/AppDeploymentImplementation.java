package com.example.devforge.service.implementation;

import com.example.devforge.entity.AppDeployment;
import com.example.devforge.repository.AppDeploymentRepository;
import com.example.devforge.service.AppDeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppDeploymentImplementation implements AppDeploymentService {

    private final AppDeploymentRepository appDeploymentRepository;

    @Override
    public Page<AppDeployment> getDeploymentsByService(UUID serviceId,Pageable pageable) {
        return appDeploymentRepository.findByService_Id(serviceId, pageable);

    }
}
