package com.example.devforge.repository;

import com.example.devforge.entity.AppService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppServiceRepository extends JpaRepository<AppService, UUID> {

  @Override
  @EntityGraph(attributePaths = {"templateVersion"})
  Optional<AppService> findById(UUID id);

  @Override
  @EntityGraph(attributePaths = {"templateVersion"})
  Page<AppService> findAll(Pageable pageable);

  @EntityGraph(attributePaths = {"templateVersion"})
  Page<AppService> findAllByOwnerKeycloakId(UUID keycloakId, Pageable pageable);

  @EntityGraph(attributePaths = {"templateVersion"})
  Optional<AppService> findByIdAndOwnerKeycloakId(UUID id, UUID keycloakId);
}
