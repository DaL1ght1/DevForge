package com.example.devforge.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "github_installation")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GithubInstallation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "installation_id", nullable = false, unique = true)
  private long installationId;

  @Column(name = "account_id", nullable = false)
  private long accountId;

  @Column(name = "account_login", nullable = false, length = 100)
  private String accountLogin;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false, length = 20)
  private GithubAccountType accountType;

  @Column(name = "repository_selection", length = 20)
  private String repositorySelection;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private GithubInstallationStatus status = GithubInstallationStatus.ACTIVE;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "workspace_id", nullable = false)
  private Workspace workspace;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connected_by_user_id")
  private User connectedBy;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;
}
