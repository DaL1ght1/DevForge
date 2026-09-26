package com.example.devforge.service.implementation;

import com.example.devforge.entity.AppTemplate;
import com.example.devforge.exception.AppTemplateNotFoundException;
import com.example.devforge.repository.AppTemplateRepository;
import com.example.devforge.service.AppTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppTemplateImplementation implements AppTemplateService {

    private final AppTemplateRepository appTemplateRepository;

    @Override
    public Page<AppTemplate> listTemplates(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return appTemplateRepository.findAll(pageable);
    }

    @Override
    public AppTemplate getTemplate(UUID id) {
        return appTemplateRepository.findById(id)
                .orElseThrow(() -> new AppTemplateNotFoundException("Template not found with id: " + id));
    }
}
