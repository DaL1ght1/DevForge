package com.example.devforge.repository;

import com.example.devforge.entity.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {
    Optional<TemplateVersion> findFirstByTemplateIdAndActiveTrue(UUID templateId);
    Optional<TemplateVersion> findFirstByTemplateId(UUID templateId);
}