package com.example.devforge.repository;

import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

  Optional<Workspace> findByTypeAndPersonalOwnerId(WorkspaceType type, UUID userId);

  boolean existsBySlug(String slug);
}
