package com.example.devforge.repository;

import com.example.devforge.entity.AppDeployment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppDeploymentRepository extends JpaRepository<AppDeployment, UUID> {

  Page<AppDeployment> findByService_Id(UUID serviceIdId, Pageable pageable);
}
