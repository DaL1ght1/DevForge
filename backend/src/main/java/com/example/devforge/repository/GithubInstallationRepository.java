package com.example.devforge.repository;

import com.example.devforge.entity.GithubInstallation;
import com.example.devforge.entity.GithubInstallationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GithubInstallationRepository extends JpaRepository<GithubInstallation, UUID> {

  @EntityGraph(attributePaths = {"workspace"})
  Optional<GithubInstallation> findByInstallationId(long installationId);

  List<GithubInstallation> findAllByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

  Optional<GithubInstallation> findFirstByWorkspaceIdAndStatusOrderByCreatedAtAsc(
      UUID workspaceId, GithubInstallationStatus status);
}
