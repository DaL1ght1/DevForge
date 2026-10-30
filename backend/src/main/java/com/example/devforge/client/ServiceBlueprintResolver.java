package com.example.devforge.client;

import com.example.devforge.client.model.TemplateContext;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.exception.AppTemplateVersionNotFoundException;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.template.TemplateManifestCache;
import com.example.devforge.template.TemplateSelectionResolver;
import com.example.devforge.template.TemplateSelectionResolver.ResolvedSelection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServiceBlueprintResolver {

  private final TemplateVersionRepository templateVersionRepository;
  private final TemplateManifestCache manifests;
  private final TemplateSelectionResolver selectionResolver;
  private final TemplateContextResolver contextResolver;

  public record Blueprint(
      TemplateVersion templateVersion, ResolvedSelection selection, TemplateContext context) {

    public ProvisionerClient.RenderRequest renderRequest() {
      return new ProvisionerClient.RenderRequest(
          context.serviceName(),
          templateVersion.getTemplate().getStableKey(),
          templateVersion.getVersion(),
          templateVersion.getArtifactDigest(),
          context.packageName(),
          context.className(),
          context.description(),
          selection.variables(),
          selection.features(),
          null);
    }

    public ProvisionerClient.ApplicationServiceSpec applicationSpec(UUID serviceId, int port) {
      return new ProvisionerClient.ApplicationServiceSpec(
          serviceId,
          context.serviceName(),
          templateVersion.getTemplate().getStableKey(),
          templateVersion.getVersion(),
          templateVersion.getArtifactDigest(),
          context.packageName(),
          context.className(),
          context.description(),
          selection.variables(),
          selection.features(),
          port);
    }
  }

  public Blueprint resolve(AppServiceCreationDto dto) {
    validateDescription(dto.description());
    TemplateVersion templateVersion = findVersion(dto.templateVersionId());
    ResolvedSelection selection =
        selectionResolver.resolve(manifests.get(templateVersion), dto.variables(), dto.features());
    return new Blueprint(templateVersion, selection, contextResolver.resolve(dto));
  }

  private TemplateVersion findVersion(UUID id) {
    return templateVersionRepository
        .findById(id)
        .or(() -> templateVersionRepository.findFirstByTemplateIdAndActiveTrue(id))
        .or(() -> templateVersionRepository.findFirstByTemplateId(id))
        .orElseThrow(() -> new AppTemplateVersionNotFoundException(id));
  }

  private static void validateDescription(String description) {
    if (description != null
        && (description.length() > 500
            || description
                .codePoints()
                .anyMatch(
                    codePoint ->
                        Character.isISOControl(codePoint)
                            && codePoint != '\n'
                            && codePoint != '\r'
                            && codePoint != '\t'))) {
      throw new IllegalArgumentException(
          "Description contains unsupported characters or is too long");
    }
  }
}
