package com.example.devforge.repository;

import com.example.devforge.entity.AppTemplate;
import com.example.devforge.entity.TemplateFramework;
import com.example.devforge.entity.TemplateLanguage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface AppTemplateRepository extends JpaRepository<AppTemplate, UUID>, JpaSpecificationExecutor<AppTemplate> {
    Page<AppTemplate> findByLanguage(TemplateLanguage language, Pageable pageable);

    Page<AppTemplate> findByFramework(TemplateFramework framework, Pageable pageable);

    Optional<AppTemplate> findByStableKey(String stableKey);
}