package com.example.devforge.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Table(
    name = "template_versions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_template_version",
          columnNames = {"template_id", "version"}),
      @UniqueConstraint(
          name = "uk_template_content_hash",
          columnNames = {"template_id", "content_hash"})
    })
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "template_id", nullable = false)
  private AppTemplate template;

  @Column(name = "version", nullable = false, length = 30)
  private String version;

  @Column(name = "source_path", nullable = false, length = 500)
  private String sourcePath;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  @Column(name = "manifest", nullable = false, columnDefinition = "TEXT")
  private String manifest;

  @Column(name = "artifact_reference", nullable = false, length = 500)
  private String artifactReference;

  @Column(name = "active", nullable = false)
  @Builder.Default
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "templateVersion", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<AppService> services = new ArrayList<>();
}
