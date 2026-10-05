package com.example.devforge.controller;

import com.example.devforge.dto.AppTemplateResponse;
import com.example.devforge.dto.TemplateFilter;
import com.example.devforge.entity.BuildTool;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import com.example.devforge.mapper.AppTemplateMapper;
import com.example.devforge.service.AppTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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


    @GetMapping("/by-languages/{language}")
    public Page<AppTemplateResponse> getTemplatesByLanguages(@PathVariable TemplateLanguage language, @PageableDefault Pageable pageable) {
        return appTemplateService.getTemplatesByLanguages(language, pageable)
                .map(appTemplateMapper::toResponse);
    }

    @GetMapping("/by-framework/{framework}")
    public Page<AppTemplateResponse> getTemplatesByFramework(@PathVariable TemplateFramework framework, @PageableDefault Pageable pageable) {
        return appTemplateService.getTemplatesByFramework(framework, pageable)
                .map(appTemplateMapper::toResponse);
    }

    @RequestMapping(value = "/filter", method = {RequestMethod.GET, RequestMethod.POST})
    public Page<AppTemplateResponse> getTemplatesByFilter(
            @RequestBody(required = false) TemplateFilter templateFilter,
            @RequestParam(required = false) TemplateFramework framework,
            @RequestParam(required = false) BuildTool buildTool,
            @PageableDefault Pageable pageable) {
        TemplateFilter effectiveFilter = templateFilter != null
                ? templateFilter : new TemplateFilter(framework, buildTool);
        return appTemplateService.getTemplatesByFilter(effectiveFilter, pageable)
                .map(appTemplateMapper::toResponse);
    }
}
