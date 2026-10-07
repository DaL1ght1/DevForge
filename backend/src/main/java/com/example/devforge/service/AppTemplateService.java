package com.example.devforge.service;

import com.example.devforge.dto.TemplateFilter;
import com.example.devforge.entity.AppTemplate;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AppTemplateService {

  Page<AppTemplate> listTemplates(int page, int size);

  AppTemplate getTemplate(UUID id);

  Page<AppTemplate> getTemplatesByLanguages(TemplateLanguage language, Pageable pageable);

  Page<AppTemplate> getTemplatesByFramework(TemplateFramework framework, Pageable pageable);

  Page<AppTemplate> getTemplatesByFilter(TemplateFilter templateFilter, Pageable pageable);
}
