package com.example.devforge.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "application",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_application_workspace_name",
          columnNames = {"workspace_id", "name"})
    })
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Application {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 40)
  private String name;

  @Column(name = "description")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @Builder.Default
  private ServiceStatus status = ServiceStatus.CREATING;

  @Column(name = "repository_owner", length = 100)
  private String repositoryOwner;

  @Column(name = "repository_url")
  private String repositoryUrl;

  @Column(name = "repository_id")
  private Long repositoryId;

  @Column(name = "failure_reason")
  private String failureReason;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "workspace_id", nullable = false)
  private Workspace workspace;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id")
  private User owner;

  @OneToMany(mappedBy = "application")
  @OrderBy("port")
  @Builder.Default
  private List<AppService> services = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
