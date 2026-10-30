package com.example.devforge.repository;

import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, UUID> {

  Optional<ProvisioningJob> findTopByServiceIdOrderByCreatedAtDesc(UUID serviceId);

  @EntityGraph(attributePaths = {"service", "service.workspace", "service.owner"})
  Optional<ProvisioningJob> findWithServiceById(UUID id);

  @Modifying(flushAutomatically = true)
  @Query(
      "update ProvisioningJob j set j.status = :to, j.startedAt = :now"
          + " where j.id = :id and j.status = :from")
  int transition(
      @Param("id") UUID id,
      @Param("from") ProvisioningStatus from,
      @Param("to") ProvisioningStatus to,
      @Param("now") Instant now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update ProvisioningJob j set j.status = :generating, j.attempt = j.attempt + 1,"
          + " j.startedAt = :now where j.id = :id and j.status in :claimable")
  int claim(
      @Param("id") UUID id,
      @Param("claimable") Collection<ProvisioningStatus> claimable,
      @Param("generating") ProvisioningStatus generating,
      @Param("now") Instant now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update ProvisioningJob j set j.repositoryId = :repositoryId"
          + " where j.id = :id and j.status = :generating")
  int recordRepository(
      @Param("id") UUID id,
      @Param("repositoryId") long repositoryId,
      @Param("generating") ProvisioningStatus generating);
}
