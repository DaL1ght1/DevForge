package com.example.devforge.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Table(
    name = "app_service",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_app_service_name",
          columnNames = {"name"}),
      @UniqueConstraint(
          name = "uk_app_service_repository_url",
          columnNames = {"repository_url"})
    })
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppService {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 20, unique = true)
  @Pattern(
      regexp = "^[a-z0-9-]{3,20}$",
      message =
          "Service name must be 3-20 characters long and contain only lowercase letters, numbers, and hyphens")
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "repository_url", unique = true)
  private String repositoryUrl;

  @Column(name = "status", nullable = false)
  @Builder.Default
  @Enumerated(EnumType.STRING)
  private ServiceStatus status = ServiceStatus.CREATING;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;

  @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<AppDeployment> deployments = new ArrayList<>();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "template_version_id", nullable = false)
  private TemplateVersion templateVersion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;
}
