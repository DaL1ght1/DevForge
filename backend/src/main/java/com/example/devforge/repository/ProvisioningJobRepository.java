package com.example.devforge.repository;

import com.example.devforge.entity.ProvisioningJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, UUID> {

  Optional<ProvisioningJob> findTopByServiceIdOrderByCreatedAtDesc(UUID serviceId);

  @EntityGraph(attributePaths = {"service", "service.workspace", "service.owner"})
  Optional<ProvisioningJob> findWithServiceById(UUID id);
}
