package com.example.devforge.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "provisioning_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisioningJob {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "service_id", nullable = false)
  private AppService service;

  @Enumerated(EnumType.STRING)
  @Column(name = "provisioning_status", nullable = false, length = 50)
  private ProvisioningStatus status;

  @Column(name = "attempt", nullable = false)
  @Builder.Default
  private int attempt = 0;

  @Column(name = "error_message", length = 2000)
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;
}
