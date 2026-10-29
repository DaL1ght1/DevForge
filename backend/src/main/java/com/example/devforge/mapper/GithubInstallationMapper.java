package com.example.devforge.mapper;

import com.example.devforge.dto.GithubInstallationResponse;
import com.example.devforge.entity.GithubInstallation;
import org.mapstruct.*;

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING)
public interface GithubInstallationMapper {

  @Mapping(target = "workspaceId", source = "workspace.id")
  GithubInstallationResponse toResponse(GithubInstallation installation);
}
