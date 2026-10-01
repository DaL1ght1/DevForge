package com.example.devforge.controller;

import com.example.devforge.dto.AppTemplateResponse;
import com.example.devforge.mapper.AppTemplateMapper;
import com.example.devforge.service.AppTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/templates")
public class AppTemplateController {

    private final AppTemplateService appTemplateService;
    private final AppTemplateMapper appTemplateMapper;


    @GetMapping("/{id}")
    public AppTemplateResponse getTemplateById(@PathVariable UUID id) {
        return appTemplateMapper.toResponse(appTemplateService.getTemplate(id));
    }

    @GetMapping
    public Page<AppTemplateResponse> getAllTemplates(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return appTemplateService.listTemplates(page, size)
                .map(appTemplateMapper::toResponse);
    }

}
