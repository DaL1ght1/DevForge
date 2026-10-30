package com.example.devforge.repository;

import com.example.devforge.entity.Application;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

  boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);

  @EntityGraph(attributePaths = {"workspace"})
  Page<Application> findAllByWorkspaceId(UUID workspaceId, Pageable pageable);

  @EntityGraph(attributePaths = {"workspace"})
  @Query(
      value =
          """
          select a from Application a
          where a.workspace.id in (
            select m.workspace.id from WorkspaceMember m where m.user.keycloakId = :keycloakId)
          """,
      countQuery =
          """
          select count(a) from Application a
          where a.workspace.id in (
            select m.workspace.id from WorkspaceMember m where m.user.keycloakId = :keycloakId)
          """)
  Page<Application> findAllVisibleToMember(@Param("keycloakId") UUID keycloakId, Pageable pageable);

  @EntityGraph(attributePaths = {"workspace"})
  @Query(
      """
      select a from Application a
      where a.id = :id and a.workspace.id in (
        select m.workspace.id from WorkspaceMember m where m.user.keycloakId = :keycloakId)
      """)
  Optional<Application> findByIdVisibleToMember(
      @Param("id") UUID id, @Param("keycloakId") UUID keycloakId);
}
