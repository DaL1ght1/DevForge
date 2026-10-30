package com.example.devforge.service.implementation;

import com.example.devforge.dto.TemplateData;
import com.example.devforge.dto.TemplateSynchronizationResult;
import com.example.devforge.entity.*;
import com.example.devforge.repository.AppTemplateRepository;
import com.example.devforge.repository.TemplateArtifactRepository;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.service.TemplateSynchronizer;
import com.example.devforge.template.TemplateArchiver;
import com.example.devforge.template.TemplateManifest;
import com.example.devforge.template.TemplateManifestParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateSynchronizerImplementation implements TemplateSynchronizer {
  private final AppTemplateRepository templates;
  private final TemplateVersionRepository versions;
  private final TransactionTemplate transactionTemplate;
  private final TemplateManifestParser manifestParser;
  private final TemplateArchiver archiver;
  private final TemplateArtifactRepository artifacts;
  private final DataSource dataSource;

  private static final long SYNC_LOCK_KEY = 0x5D3F0E7A11L;

  @Value("${devforge.templates.root}")
  private String configuredRoot;

  @Override
  public TemplateSynchronizationResult synchronize() {
    try (Connection lockConnection = dataSource.getConnection()) {
      if (!advisoryLock(lockConnection, "pg_try_advisory_lock")) {
        log.info("Template synchronization already running on another instance");
        return failure();
      }
      try {
        return synchronizeLocked();
      } finally {
        advisoryLock(lockConnection, "pg_advisory_unlock");
      }
    } catch (SQLException e) {
      log.error("Unable to acquire the template synchronization lock", e);
      return failure();
    }
  }

  private static boolean advisoryLock(Connection connection, String function) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("SELECT " + function + "(?)")) {
      statement.setLong(1, SYNC_LOCK_KEY);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() && result.getBoolean(1);
      }
    }
  }

  private static TemplateSynchronizationResult failure() {
    return TemplateSynchronizationResult.builder()
        .scanned(0)
        .synchronizedTemplates(0)
        .unchanged(0)
        .failed(1)
        .build();
  }

  private TemplateSynchronizationResult synchronizeLocked() {
    Path root = Paths.get(configuredRoot).toAbsolutePath().normalize();
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
      log.warn("Template synchronization root is unavailable or unsafe: {}", root);
      return failure();
    }
    AtomicInteger scanned = new AtomicInteger(),
        synced = new AtomicInteger(),
        unchanged = new AtomicInteger(),
        failed = new AtomicInteger();
    try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root)) {
      for (Path dir : dirs) {
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) continue;
        scanned.incrementAndGet();
        try {
          TemplateData data = read(dir, root);
          Boolean changed = transactionTemplate.execute(_ -> upsert(data));
          if (Boolean.TRUE.equals(changed)) synced.incrementAndGet();
          else unchanged.incrementAndGet();
        } catch (VersionNotBumpedException e) {
          log.warn("Skipping template {}: {}", dir.getFileName(), e.getMessage());
          failed.incrementAndGet();
        } catch (Exception e) {
          log.warn("Skipping invalid template directory {}", dir, e);
          failed.incrementAndGet();
        }
      }
    } catch (IOException e) {
      log.error("Unable to scan template root {}", root, e);
      failed.incrementAndGet();
    }
    return TemplateSynchronizationResult.builder()
        .scanned(scanned.get())
        .synchronizedTemplates(synced.get())
        .unchanged(unchanged.get())
        .failed(failed.get())
        .build();
  }

  private Boolean upsert(TemplateData d) {
    AppTemplate app =
        templates
            .findByStableKey(d.stableKey())
            .orElseGet(
                () ->
                    AppTemplate.builder()
                        .stableKey(d.stableKey())
                        .name(d.name())
                        .language(d.language())
                        .framework(d.framework())
                        .buildTool(d.buildTool())
                        .build());
    app.setName(d.name());
    app.setLanguage(d.language());
    app.setFramework(d.framework());
    app.setBuildTool(d.buildTool());
    app.setDatabaseType(null);
    app = templates.save(app);
    Optional<TemplateVersion> existing =
        versions.findByTemplateIdAndContentHash(app.getId(), d.hash());
    if (existing.isPresent()) {
      if (existing.get().getArtifactDigest() != null) return false;
      saveArtifact(d);
      existing.get().setArtifactDigest(d.artifactDigest());
      versions.save(existing.get());
      return true;
    }
    Optional<TemplateVersion> sameVersion =
        versions.findByTemplateIdAndVersion(app.getId(), d.version());
    if (sameVersion.isPresent()) {
      TemplateVersion row = sameVersion.get();
      if (row.getArtifactDigest() != null) throw new VersionNotBumpedException(d.version());
      saveArtifact(d);
      row.setContentHash(d.hash());
      row.setManifest(d.manifest());
      row.setArtifactDigest(d.artifactDigest());
      versions.save(row);
      return true;
    }
    saveArtifact(d);
    TemplateVersion prior = versions.findFirstByTemplateIdAndActiveTrue(app.getId()).orElse(null);
    TemplateVersion current =
        TemplateVersion.builder()
            .template(app)
            .version(d.version())
            .sourcePath(d.relativePath())
            .contentHash(d.hash())
            .manifest(d.manifest())
            .artifactReference(d.relativePath())
            .artifactDigest(d.artifactDigest())
            .active(true)
            .build();
    versions.saveAndFlush(current);
    if (prior != null) {
      prior.setActive(false);
      versions.save(prior);
    }
    return true;
  }

  static final class VersionNotBumpedException extends RuntimeException {
    VersionNotBumpedException(String version) {
      super(
          "content changed but version "
              + version
              + " is already synchronized; bump `version` in template.yaml");
    }
  }

  private void saveArtifact(TemplateData d) {
    if (artifacts.existsById(d.artifactDigest())) return;
    artifacts.saveAndFlush(
        TemplateArtifact.builder()
            .digest(d.artifactDigest())
            .sizeBytes(d.artifactData().length)
            .data(d.artifactData())
            .build());
  }

  private TemplateData read(Path dir, Path root) throws IOException {
    Path realRoot = root.toRealPath();
    Path realDir = dir.toRealPath();
    if (!realDir.startsWith(realRoot) || Files.isSymbolicLink(dir))
      throw new IOException("unsafe template path");
    Path manifestPath = dir.resolve("template.yaml");
    if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(manifestPath)) throw new IOException("missing manifest");
    String manifestText = Files.readString(manifestPath);
    TemplateManifest manifest;
    try {
      manifest = manifestParser.parse(manifestText);
    } catch (IllegalArgumentException e) {
      throw new IOException(e.getMessage(), e);
    }
    String stableKey = dir.getFileName().toString();
    if (manifest.name().length() > 20
        || stableKey.length() > 100
        || manifest.version().length() > 30) throw new IOException("manifest value is too long");
    if (manifest.schemaVersion() >= 2) validateLayout(realDir, manifest);
    String hash = hash(realDir, realRoot);
    TemplateArchiver.Archive archive = archiver.archive(realDir);
    return TemplateData.builder()
        .artifactDigest(archive.digest())
        .artifactData(archive.data())
        .name(manifest.name())
        .stableKey(stableKey)
        .version(manifest.version())
        .language(manifest.language())
        .framework(manifest.framework())
        .buildTool(manifest.buildTool())
        .manifest(manifestText)
        .hash(hash)
        .relativePath(root.relativize(dir).toString())
        .build();
  }

  private static void validateLayout(Path dir, TemplateManifest manifest) throws IOException {
    if (!Files.isDirectory(dir.resolve("base"), LinkOption.NOFOLLOW_LINKS))
      throw new IOException("schemaVersion 2 templates need a base/ directory");
    Path features = dir.resolve("features");
    if (!Files.isDirectory(features, LinkOption.NOFOLLOW_LINKS)) return;
    Set<String> optionIds = new HashSet<>();
    manifest.features().forEach(g -> g.options().forEach(o -> optionIds.add(o.id())));
    try (DirectoryStream<Path> overlays = Files.newDirectoryStream(features)) {
      for (Path overlay : overlays) {
        String name = overlay.getFileName().toString();
        if (!Files.isDirectory(overlay, LinkOption.NOFOLLOW_LINKS) || !optionIds.contains(name))
          throw new IOException("features/" + name + " does not match a feature option");
      }
    }
  }

  private String hash(Path dir, Path root) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      List<Path> files;
      try (var stream = Files.walk(dir)) {
        List<Path> all = stream.sorted().toList();
        if (all.stream().anyMatch(Files::isSymbolicLink))
          throw new IOException("symlink in template");
        files = all.stream().filter(Files::isRegularFile).toList();
      }
      for (Path file : files) {
        Path real = file.toRealPath();
        if (!real.startsWith(root) || !real.startsWith(dir.toRealPath()))
          throw new IOException("unsafe file");
        digest.update(
            root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Files.readAllBytes(file));
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception e) {
      if (e instanceof IOException io) throw io;
      throw new IOException(e);
    }
  }
}
