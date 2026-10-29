package com.example.devforge.mapper;

import com.example.devforge.dto.WorkspaceMemberResponse;
import com.example.devforge.dto.WorkspaceResponse;
import com.example.devforge.entity.WorkspaceMember;
import org.mapstruct.*;

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING)
public interface WorkspaceMapper {

  @Mapping(target = "id", source = "workspace.id")
  @Mapping(target = "name", source = "workspace.name")
  @Mapping(target = "slug", source = "workspace.slug")
  @Mapping(target = "type", source = "workspace.type")
  @Mapping(target = "createdAt", source = "workspace.createdAt")
  WorkspaceResponse toResponse(WorkspaceMember membership);

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "username", source = "user.username")
  @Mapping(target = "email", source = "user.email")
  @Mapping(target = "joinedAt", source = "createdAt")
  WorkspaceMemberResponse toMemberResponse(WorkspaceMember membership);
}
