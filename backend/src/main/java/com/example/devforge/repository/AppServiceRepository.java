package com.example.devforge.repository;

import com.example.devforge.entity.AppService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface AppServiceRepository extends JpaRepository<AppService, UUID> {

    @Override
    @EntityGraph(attributePaths = {"templateVersion"})
    Optional<AppService> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"templateVersion"})
    Page<AppService> findAll(Pageable pageable);
}