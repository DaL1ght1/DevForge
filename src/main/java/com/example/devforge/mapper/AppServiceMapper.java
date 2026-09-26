package com.example.devforge.mapper;

import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.dto.AppServiceResponse;
import com.example.devforge.entity.AppService;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING)
public interface AppServiceMapper {

    AppService toEntity(AppServiceCreationDto appServiceCreationDto);

    AppServiceResponse toResponse(AppService appService);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    AppService partialUpdate(AppServiceCreationDto appServiceCreationDto, @MappingTarget AppService appService);
}