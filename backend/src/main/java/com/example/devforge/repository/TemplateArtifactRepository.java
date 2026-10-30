package com.example.devforge.repository;

import com.example.devforge.entity.TemplateArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateArtifactRepository extends JpaRepository<TemplateArtifact, String> {}
