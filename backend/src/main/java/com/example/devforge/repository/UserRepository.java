package com.example.devforge.repository;

import com.example.devforge.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByKeycloakId(UUID keycloakId);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  Optional<User> findByUsername(String username);

  Optional<User> findByEmail(String email);
}
