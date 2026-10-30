package com.example.devforge.repository;

import com.example.devforge.entity.AppService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppServiceRepository extends JpaRepository<AppService, UUID> {

  @Override
  @NonNull
  @EntityGraph(attributePaths = {"templateVersion", "workspace"})
  Optional<AppService> findById(@NonNull UUID id);

  @Override
  @NonNull
  @EntityGraph(attributePaths = {"templateVersion", "workspace"})
  Page<AppService> findAll(@NonNull Pageable pageable);

  @EntityGraph(attributePaths = {"templateVersion", "workspace"})
  Page<AppService> findAllByWorkspaceId(UUID workspaceId, Pageable pageable);

  @EntityGraph(attributePaths = {"templateVersion", "workspace"})
  @Query(
      value =
          """
          select s from AppService s
          where s.workspace.id in (
            select m.workspace.id from WorkspaceMember m where m.user.keycloakId = :keycloakId)
          """,
      countQuery =
          """
          select count(s) from AppService s
          where s.workspace.id in (
            select m.workspace.id from WorkspaceMember m where m.user.keycloakId = :keycloakId)
          """)
  Page<AppService> findAllVisibleToMember(@Param("keycloakId") UUID keycloakId, Pageable pageable);

  @EntityGraph(attributePaths = {"templateVersion", "workspace"})
  @Query(
      """
      select s from AppService s
      where s.id = :id and s.workspace.id in (
        select m.workspace.id from WorkspaceMember m where m.user.keycloakId = :keycloakId)
      """)
  Optional<AppService> findByIdVisibleToMember(
      @Param("id") UUID id, @Param("keycloakId") UUID keycloakId);

  boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);

  List<AppService> findAllByApplicationIdOrderByPort(UUID applicationId);
}
