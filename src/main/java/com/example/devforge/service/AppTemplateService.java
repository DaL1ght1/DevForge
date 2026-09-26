package com.example.devforge.service;

import com.example.devforge.entity.AppTemplate;
import org.springframework.data.domain.Page;

import java.util.UUID;


public interface AppTemplateService {



    Page<AppTemplate> listTemplates(int page, int size);
    AppTemplate getTemplate(UUID id);
}
