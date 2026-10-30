package com.example.devforge.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "topic", nullable = false)
  private String topic;

  @Column(name = "message_key", nullable = false)
  private String messageKey;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Column(name = "job_id")
  private UUID jobId;

  @Column(name = "attempts", nullable = false)
  @Builder.Default
  private int attempts = 0;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "sent_at")
  private Instant sentAt;
}
