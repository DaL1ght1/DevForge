package com.example.devforge.service.implementation;

import com.example.devforge.dto.TemplateData;
import com.example.devforge.dto.TemplateSynchronizationResult;
import com.example.devforge.entity.*;
import com.example.devforge.repository.AppTemplateRepository;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.service.TemplateSynchronizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateSynchronizerImplementation implements TemplateSynchronizer {
  private final AppTemplateRepository templates;
  private final TemplateVersionRepository versions;
  private final TransactionTemplate transactionTemplate;
  private final ReentrantLock lock = new ReentrantLock();
  private final Yaml yaml = new Yaml();

  @Value("${devforge.templates.root}")
  private String configuredRoot;

  @Override
  public TemplateSynchronizationResult synchronize() {
    if (!lock.tryLock())
      return TemplateSynchronizationResult.builder()
          .scanned(0)
          .synchronizedTemplates(0)
          .unchanged(0)
          .failed(1)
          .build();
    try {
      Path root = Paths.get(configuredRoot).toAbsolutePath().normalize();
      if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
        log.warn("Template synchronization root is unavailable or unsafe: {}", root);
        return TemplateSynchronizationResult.builder()
            .scanned(0)
            .synchronizedTemplates(0)
            .unchanged(0)
            .failed(1)
            .build();
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
    } finally {
      lock.unlock();
    }
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
                        .databaseType(d.databaseType())
                        .build());
    app.setName(d.name());
    app.setLanguage(d.language());
    app.setFramework(d.framework());
    app.setBuildTool(d.buildTool());
    app.setDatabaseType(d.databaseType());
    app = templates.save(app);
    if (versions.findByTemplateIdAndContentHash(app.getId(), d.hash()).isPresent()) return false;
    TemplateVersion prior = versions.findFirstByTemplateIdAndActiveTrue(app.getId()).orElse(null);
    TemplateVersion current =
        TemplateVersion.builder()
            .template(app)
            .version(d.version())
            .sourcePath(d.relativePath())
            .contentHash(d.hash())
            .manifest(d.manifest())
            .artifactReference(d.relativePath())
            .active(true)
            .build();
    versions.saveAndFlush(current);
    if (prior != null) {
      prior.setActive(false);
      versions.save(prior);
    }
    return true;
  }

  private TemplateData read(Path dir, Path root) throws IOException {
    Path realRoot = root.toRealPath();
    Path realDir = dir.toRealPath();
    if (!realDir.startsWith(realRoot) || Files.isSymbolicLink(dir))
      throw new IOException("unsafe template path");
    Path manifestPath = dir.resolve("template.yaml");
    if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(manifestPath)) throw new IOException("missing manifest");
    String manifest = Files.readString(manifestPath);
    Object loaded = yaml.load(manifest);
    if (!(loaded instanceof Map<?, ?> raw)) throw new IOException("manifest must be a map");
    String name = required(raw, "name");
    String stableKey = dir.getFileName().toString();
    String version = required(raw, "version");
    TemplateLanguage language = enumValue(TemplateLanguage.class, required(raw, "language"));
    TemplateFramework framework = enumValue(TemplateFramework.class, required(raw, "framework"));
    BuildTool buildTool = enumValue(BuildTool.class, required(raw, "buildTool", "build_tool"));
    if (name.length() > 20 || stableKey.length() > 100 || version.length() > 30)
      throw new IOException("manifest value is too long");
    String hash = hash(realDir, realRoot);
    return TemplateData.builder()
        .name(name)
        .stableKey(stableKey)
        .version(version)
        .language(language)
        .framework(framework)
        .buildTool(buildTool)
        .databaseType(optional(raw, "databaseType", "database_type"))
        .manifest(manifest)
        .hash(hash)
        .relativePath(root.relativize(dir).toString())
        .build();
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

  private static String required(Map<?, ?> map, String... keys) throws IOException {
    Object value = null;
    for (String key : keys)
      if (map.containsKey(key)) {
        value = map.get(key);
        break;
      }
    if (!(value instanceof String s) || s.isBlank()) throw new IOException("missing " + keys[0]);
    return s.trim();
  }

  private static String optional(Map<?, ?> map, String... keys) {
    for (String key : keys) {
      Object value = map.get(key);
      if (value instanceof String s && !s.isBlank()) return s.trim();
    }
    return null;
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, String value) throws IOException {
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (IllegalArgumentException e) {
      throw new IOException("invalid " + type.getSimpleName());
    }
  }
}
