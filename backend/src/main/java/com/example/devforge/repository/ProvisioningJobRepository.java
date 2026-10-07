package com.example.devforge.repository;

import com.example.devforge.entity.ProvisioningJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, UUID> {}
