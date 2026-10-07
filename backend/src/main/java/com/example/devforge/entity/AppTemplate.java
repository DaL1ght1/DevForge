package com.example.devforge.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Table(
    name = "app_template",
    uniqueConstraints = {@UniqueConstraint(name = "uk_template_name", columnNames = "name")})
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppTemplate {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 20, unique = true)
  private String name;

  @Column(name = "stable_key", nullable = false, length = 100, unique = true)
  private String stableKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "language", nullable = false, length = 30)
  private TemplateLanguage language;

  @Enumerated(EnumType.STRING)
  @Column(name = "framework", nullable = false, length = 50)
  private TemplateFramework framework;

  @Enumerated(EnumType.STRING)
  @Column(name = "build_tool", nullable = false, length = 30)
  private BuildTool buildTool;

  @Column(name = "database_type", length = 20)
  private String databaseType;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<TemplateVersion> versions = new ArrayList<>();
}
