package com.example.devforge.service;

import com.example.devforge.dto.WorkspaceCreationDto;
import com.example.devforge.dto.WorkspaceMemberAddDto;
import com.example.devforge.dto.WorkspaceMemberResponse;
import com.example.devforge.dto.WorkspaceResponse;
import com.example.devforge.entity.User;
import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceMember;
import com.example.devforge.entity.WorkspaceRole;
import java.util.List;
import java.util.UUID;

public interface WorkspaceService {

  Workspace getOrCreatePersonal(User user);

  WorkspaceMember requireRole(UUID workspaceId, UUID keycloakId, WorkspaceRole minRole);

  WorkspaceResponse createOrganization(UUID keycloakId, WorkspaceCreationDto dto);

  List<WorkspaceResponse> listForUser(UUID keycloakId);

  WorkspaceResponse getWorkspace(UUID workspaceId, UUID keycloakId);

  List<WorkspaceMemberResponse> listMembers(UUID workspaceId, UUID keycloakId);

  WorkspaceMemberResponse addMember(UUID workspaceId, UUID keycloakId, WorkspaceMemberAddDto dto);

  void removeMember(UUID workspaceId, UUID keycloakId, UUID userId);
}
