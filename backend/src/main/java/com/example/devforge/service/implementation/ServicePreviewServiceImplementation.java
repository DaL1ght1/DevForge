package com.example.devforge.service.implementation;

import com.example.devforge.client.ApplicationBlueprintResolver;
import com.example.devforge.client.ApplicationBlueprintResolver.ApplicationBlueprint;
import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.client.ProvisionerPreviewClient;
import com.example.devforge.client.ServiceBlueprintResolver;
import com.example.devforge.client.ServiceBlueprintResolver.Blueprint;
import com.example.devforge.config.preview.PreviewProperties;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.dto.ApplicationCreationDto;
import com.example.devforge.dto.PreviewFileResponse;
import com.example.devforge.dto.ServicePreviewResponse;
import com.example.devforge.dto.ServicePreviewResponse.PreviewFileEntry;
import com.example.devforge.exception.PreviewUnavailableException;
import com.example.devforge.exception.ResourceNotFoundException;
import com.example.devforge.preview.ExpiringCache;
import com.example.devforge.service.ServicePreviewService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ServicePreviewServiceImplementation implements ServicePreviewService {

  record CachedPreview(
      UUID owner,
      String templateName,
      String templateVersion,
      ProvisionerClient.RenderResponse rendered) {}

  private final ServiceBlueprintResolver blueprints;
  private final ApplicationBlueprintResolver applicationBlueprints;
  private final ProvisionerPreviewClient provisioner;
  private final JsonMapper jsonMapper;
  private final PreviewProperties props;
  private final ExpiringCache<String, CachedPreview> cache;
  private final Semaphore renderSlots;

  @Autowired
  public ServicePreviewServiceImplementation(
      ServiceBlueprintResolver blueprints,
      ApplicationBlueprintResolver applicationBlueprints,
      ProvisionerPreviewClient provisioner,
      JsonMapper jsonMapper,
      PreviewProperties props) {
    this(blueprints, applicationBlueprints, provisioner, jsonMapper, props, Clock.systemUTC());
  }

  ServicePreviewServiceImplementation(
      ServiceBlueprintResolver blueprints,
      ApplicationBlueprintResolver applicationBlueprints,
      ProvisionerPreviewClient provisioner,
      JsonMapper jsonMapper,
      PreviewProperties props,
      Clock clock) {
    this.blueprints = blueprints;
    this.applicationBlueprints = applicationBlueprints;
    this.provisioner = provisioner;
    this.jsonMapper = jsonMapper;
    this.props = props;
    this.cache = new ExpiringCache<>(props.ttl(), props.maxEntries(), clock);
    this.renderSlots = new Semaphore(props.maxConcurrent());
  }

  @Override
  @Transactional(readOnly = true)
  public ServicePreviewResponse preview(UUID keycloakId, AppServiceCreationDto dto) {
    Blueprint blueprint = blueprints.resolve(dto);
    return cachedPreview(
        keycloakId,
        blueprint.templateVersion().getTemplate().getName(),
        blueprint.templateVersion().getVersion(),
        blueprint.renderRequest());
  }

  @Override
  @Transactional(readOnly = true)
  public ServicePreviewResponse previewApplication(UUID keycloakId, ApplicationCreationDto dto) {
    ApplicationBlueprint blueprint = applicationBlueprints.resolve(dto);
    return cachedPreview(
        keycloakId,
        blueprint.name(),
        blueprint.services().size() + " services",
        blueprint.renderRequest());
  }

  private ServicePreviewResponse cachedPreview(
      UUID keycloakId,
      String templateName,
      String templateVersion,
      ProvisionerClient.RenderRequest request) {
    String previewId = previewId(keycloakId, request);
    CachedPreview preview = cache.get(previewId).orElse(null);
    if (preview == null) {
      preview = new CachedPreview(keycloakId, templateName, templateVersion, render(request));
      cache.put(previewId, preview);
    }
    return toResponse(previewId, preview);
  }

  @Override
  public PreviewFileResponse file(UUID keycloakId, String previewId, String path) {
    CachedPreview preview =
        cache
            .get(previewId)
            .filter(p -> p.owner().equals(keycloakId))
            .orElseThrow(() -> new ResourceNotFoundException("Preview expired, generate it again"));
    return preview.rendered().files().stream()
        .filter(f -> f.path().equals(path))
        .findFirst()
        .map(
            f ->
                new PreviewFileResponse(
                    f.path(), f.origin(), f.size(), f.binary(), f.omitted(), f.content()))
        .orElseThrow(() -> new ResourceNotFoundException("File not found in preview: " + path));
  }

  private ProvisionerClient.RenderResponse render(ProvisionerClient.RenderRequest request) {
    boolean acquired;
    try {
      acquired = renderSlots.tryAcquire(props.queueTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PreviewUnavailableException("Interrupted while waiting for a preview slot", e);
    }
    if (!acquired) {
      throw new PreviewUnavailableException("Too many previews in progress, try again shortly");
    }
    try {
      return provisioner.render(request);
    } finally {
      renderSlots.release();
    }
  }

  private String previewId(UUID keycloakId, ProvisionerClient.RenderRequest request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(keycloakId.toString().getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update(jsonMapper.writeValueAsBytes(request));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ServicePreviewResponse toResponse(String previewId, CachedPreview preview) {
    ProvisionerClient.RenderResponse rendered = preview.rendered();
    return new ServicePreviewResponse(
        previewId,
        preview.templateName(),
        preview.templateVersion(),
        rendered.files().size(),
        rendered.totalBytes(),
        rendered.files().stream()
            .map(f -> new PreviewFileEntry(f.path(), f.origin(), f.size(), f.binary(), f.omitted()))
            .toList());
  }
}
