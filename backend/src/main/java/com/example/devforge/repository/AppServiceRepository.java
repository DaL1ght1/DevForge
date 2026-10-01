package com.example.devforge.repository;

import com.example.devforge.entity.AppService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface AppServiceRepository extends JpaRepository<AppService, UUID> {
}