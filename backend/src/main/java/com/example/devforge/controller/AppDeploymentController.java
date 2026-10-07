package com.example.devforge.controller;

import com.example.devforge.dto.AppDeploymentResponse;
import com.example.devforge.mapper.AppDeploymentMapper;
import com.example.devforge.service.AppDeploymentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/deployments")
public class AppDeploymentController {

  private final AppDeploymentService appDeploymentService;
  private final AppDeploymentMapper appDeploymentMapper;

  @GetMapping()
  public Page<AppDeploymentResponse> getDeployments(
      @RequestParam UUID serviceId,
      @PageableDefault(sort = "deployedAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return appDeploymentService
        .getDeploymentsByService(serviceId, pageable)
        .map(appDeploymentMapper::toResponse);
  }
}
