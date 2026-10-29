package com.example.devforge.repository;

import com.example.devforge.entity.WorkspaceMember;
import com.example.devforge.entity.WorkspaceRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

  @EntityGraph(attributePaths = {"workspace"})
  Optional<WorkspaceMember> findByWorkspaceIdAndUserKeycloakId(UUID workspaceId, UUID keycloakId);

  Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

  @EntityGraph(attributePaths = {"workspace"})
  List<WorkspaceMember> findAllByUserKeycloakIdOrderByCreatedAtAsc(UUID keycloakId);

  @EntityGraph(attributePaths = {"user"})
  List<WorkspaceMember> findAllByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

  boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

  long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);
}
