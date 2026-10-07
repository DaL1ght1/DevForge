package com.example.devforge.repository;

import com.example.devforge.entity.TemplateVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {
  Optional<TemplateVersion> findFirstByTemplateIdAndActiveTrue(UUID templateId);

  Optional<TemplateVersion> findFirstByTemplateId(UUID templateId);

  Optional<TemplateVersion> findByTemplateIdAndContentHash(UUID templateId, String contentHash);
}
