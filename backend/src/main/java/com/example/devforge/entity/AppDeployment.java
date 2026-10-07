package com.example.devforge.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Table(name = "app_deployment")
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppDeployment {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "service_id", nullable = false)
  private AppService service;

  @Column(name = "environment", nullable = false, length = 20)
  private String environment;

  @Column(name = "version", length = 20, nullable = false)
  private String version;

  @Column(name = "status", nullable = false)
  @Builder.Default
  @Enumerated(EnumType.STRING)
  private DeploymentStatus status = DeploymentStatus.DEPLOYING;

  @Column(name = "deployed_at", nullable = false)
  @CreationTimestamp
  private Instant deployedAt;
}
