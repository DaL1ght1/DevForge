package com.example.devforge.repository;

import com.example.devforge.entity.GithubAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GithubAccountRepository extends JpaRepository<GithubAccount, UUID> {

  Optional<GithubAccount> findByUserId(UUID userId);

  Optional<GithubAccount> findByGithubUserId(long githubUserId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from GithubAccount a where a.user.id = :userId")
  Optional<GithubAccount> findByUserIdForUpdate(@Param("userId") UUID userId);
}
