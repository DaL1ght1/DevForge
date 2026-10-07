package com.example.devforge.controller;

import com.example.devforge.dto.TemplateSynchronizationResult;
import com.example.devforge.service.TemplateSynchronizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/templates")
public class TemplateAdminController {

  private final TemplateSynchronizer synchronizer;

  @PostMapping("/sync")
  @PreAuthorize("hasRole('ADMIN')")
  public TemplateSynchronizationResult synchronize() {
    return synchronizer.synchronize();
  }
}
