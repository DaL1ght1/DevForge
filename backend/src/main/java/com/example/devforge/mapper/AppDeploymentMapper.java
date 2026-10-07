package com.example.devforge.mapper;

import com.example.devforge.dto.AppDeploymentResponse;
import com.example.devforge.entity.AppDeployment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING)
public interface AppDeploymentMapper {

  @Mapping(source = "service.id", target = "serviceId")
  AppDeploymentResponse toResponse(AppDeployment appDeployment);
}
