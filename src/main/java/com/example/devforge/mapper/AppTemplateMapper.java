package com.example.devforge.mapper;

import com.example.devforge.dto.AppTemplateResponse;
import com.example.devforge.entity.AppTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING)
public interface AppTemplateMapper {

    AppTemplateResponse toResponse(AppTemplate appTemplate);
}