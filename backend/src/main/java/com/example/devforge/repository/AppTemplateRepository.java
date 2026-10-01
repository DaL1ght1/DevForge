package com.example.devforge.repository;

import com.example.devforge.entity.AppTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface AppTemplateRepository extends JpaRepository<AppTemplate, UUID> {
}