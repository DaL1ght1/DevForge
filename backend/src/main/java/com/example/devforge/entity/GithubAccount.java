package com.example.devforge.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "github_account")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GithubAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
  private User user;

  @Column(name = "github_user_id", nullable = false, unique = true)
  private long githubUserId;

  @Column(name = "login", nullable = false, length = 100)
  private String login;

  @Column(name = "access_token_enc")
  private String accessTokenEnc;

  @Column(name = "access_token_expires_at")
  private Instant accessTokenExpiresAt;

  @Column(name = "refresh_token_enc")
  private String refreshTokenEnc;

  @Column(name = "refresh_token_expires_at")
  private Instant refreshTokenExpiresAt;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;

  public void clearTokens() {
    accessTokenEnc = null;
    accessTokenExpiresAt = null;
    refreshTokenEnc = null;
    refreshTokenExpiresAt = null;
  }
}
