package com.example.devforge.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.devforge.client.ApplicationBlueprintResolver;
import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.client.ProvisionerPreviewClient;
import com.example.devforge.client.ServiceBlueprintResolver;
import com.example.devforge.client.TemplateContextResolver;
import com.example.devforge.config.preview.PreviewProperties;
import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.dto.ApplicationCreationDto;
import com.example.devforge.dto.ServicePreviewResponse;
import com.example.devforge.entity.AppTemplate;
import com.example.devforge.entity.TemplateVersion;
import com.example.devforge.exception.InvalidTemplateSelectionException;
import com.example.devforge.exception.PreviewUnavailableException;
import com.example.devforge.exception.ResourceNotFoundException;
import com.example.devforge.repository.TemplateVersionRepository;
import com.example.devforge.template.TemplateManifestCache;
import com.example.devforge.template.TemplateManifestParser;
import com.example.devforge.template.TemplateSelectionResolver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class ServicePreviewServiceImplementationTest {

  private static final String MANIFEST =
      """
      schemaVersion: 2
      name: spring
      version: 2.0.0
      language: JAVA
      framework: SPRING_BOOT
      buildTool: MAVEN
      features:
        - id: database
          kind: choice
          options:
            - id: postgresql
      """;

  private final TemplateVersionRepository versions = mock(TemplateVersionRepository.class);
  private final ProvisionerPreviewClient provisioner = mock(ProvisionerPreviewClient.class);
  private final MutableClock clock = new MutableClock();
  private final PreviewProperties props =
      new PreviewProperties(
          "http://p", Duration.ofSeconds(5), Duration.ofMinutes(10), 10, 1, Duration.ofMillis(50));
  private final ServiceBlueprintResolver serviceBlueprints =
      new ServiceBlueprintResolver(
          versions,
          new TemplateManifestCache(new TemplateManifestParser()),
          new TemplateSelectionResolver(),
          new TemplateContextResolver());
  private final ServicePreviewServiceImplementation service =
      new ServicePreviewServiceImplementation(
          serviceBlueprints,
          new ApplicationBlueprintResolver(serviceBlueprints),
          provisioner,
          JsonMapper.builder().build(),
          props,
          clock);

  private final UUID versionId = UUID.randomUUID();
  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();
  private final AppServiceCreationDto dto =
      new AppServiceCreationDto(
          "orders", "Orders API", versionId, null, null, Map.of("database", List.of("postgresql")));

  @BeforeEach
  void setUp() {
    TemplateVersion version =
        TemplateVersion.builder()
            .id(versionId)
            .template(AppTemplate.builder().name("spring-maven").stableKey("spring").build())
            .version("2.0.0")
            .artifactDigest("d".repeat(64))
            .manifest(MANIFEST)
            .build();
    when(versions.findById(versionId)).thenReturn(Optional.of(version));
    when(provisioner.render(any())).thenReturn(rendered());
  }

  @Test
  void rendersTreeWithoutContentAndSendsTheResolvedSelection() {
    ServicePreviewResponse preview = service.preview(alice, dto);

    assertThat(preview.previewId()).hasSize(64);
    assertThat(preview.templateName()).isEqualTo("spring-maven");
    assertThat(preview.fileCount()).isEqualTo(2);
    assertThat(preview.files())
        .extracting(ServicePreviewResponse.PreviewFileEntry::path)
        .containsExactly("pom.xml", "src/main/resources/db/migration/V1__init.sql");
    ArgumentCaptor<ProvisionerClient.RenderRequest> sent =
        ArgumentCaptor.forClass(ProvisionerClient.RenderRequest.class);
    verify(provisioner).render(sent.capture());
    assertThat(sent.getValue().artifactDigest()).isEqualTo("d".repeat(64));
    assertThat(sent.getValue().templateName()).isEqualTo("spring");
    assertThat(sent.getValue().features()).containsEntry("database", List.of("postgresql"));
    assertThat(sent.getValue().packageName()).isEqualTo("com.devforge.orders");
  }

  @Test
  void identicalSelectionIsServedFromCache() {
    String first = service.preview(alice, dto).previewId();
    String second = service.preview(alice, dto).previewId();

    assertThat(second).isEqualTo(first);
    verify(provisioner, times(1)).render(any());
  }

  @Test
  void filesAreReadableOnlyByTheirOwnerAndUntilExpiry() {
    String previewId = service.preview(alice, dto).previewId();

    assertThat(service.file(alice, previewId, "pom.xml").content()).isEqualTo("<project/>");
    assertThatThrownBy(() -> service.file(bob, previewId, "pom.xml"))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> service.file(alice, previewId, "missing.txt"))
        .isInstanceOf(ResourceNotFoundException.class);

    clock.advance(Duration.ofMinutes(11));
    assertThatThrownBy(() -> service.file(alice, previewId, "pom.xml"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void differentUsersGetDifferentPreviewIds() {
    assertThat(service.preview(alice, dto).previewId())
        .isNotEqualTo(service.preview(bob, dto).previewId());
  }

  @Test
  void invalidSelectionIsRejectedBeforeRendering() {
    AppServiceCreationDto invalid =
        new AppServiceCreationDto(
            "orders", null, versionId, null, null, Map.of("database", List.of("oracle")));

    assertThatThrownBy(() -> service.preview(alice, invalid))
        .isInstanceOf(InvalidTemplateSelectionException.class);
    verifyNoInteractions(provisioner);
  }

  @Test
  void concurrentRendersBeyondTheLimitAreRefused() throws Exception {
    CountDownLatch rendering = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(provisioner.render(any()))
        .thenAnswer(
            inv -> {
              rendering.countDown();
              release.await();
              return rendered();
            });
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread first =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    service.preview(alice, dto);
                  } catch (Throwable t) {
                    failure.set(t);
                  }
                });
    rendering.await();

    assertThatThrownBy(() -> service.preview(bob, dto))
        .isInstanceOf(PreviewUnavailableException.class);
    release.countDown();
    first.join();
    assertThat(failure.get()).isNull();
  }

  @Test
  void applicationPreviewSendsEveryServiceWithAssignedPorts() {
    ApplicationCreationDto app =
        new ApplicationCreationDto(
            "shop",
            "Shop",
            null,
            List.of(
                new ApplicationCreationDto.Service(
                    "orders", null, versionId, null, Map.of("database", List.of("postgresql"))),
                new ApplicationCreationDto.Service("billing", null, versionId, null, null)));

    ServicePreviewResponse preview = service.previewApplication(alice, app);

    ArgumentCaptor<ProvisionerClient.RenderRequest> sent =
        ArgumentCaptor.forClass(ProvisionerClient.RenderRequest.class);
    verify(provisioner).render(sent.capture());
    assertThat(sent.getValue().serviceName()).isEqualTo("shop");
    assertThat(sent.getValue().services())
        .extracting(
            ProvisionerClient.ApplicationServiceSpec::serviceName,
            ProvisionerClient.ApplicationServiceSpec::port)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("orders", 8080),
            org.assertj.core.groups.Tuple.tuple("billing", 8081));
    assertThat(sent.getValue().services().getFirst().artifactDigest()).isEqualTo("d".repeat(64));
    assertThat(preview.templateName()).isEqualTo("shop");
  }

  @Test
  void applicationWithDuplicateServiceNamesIsRejected() {
    ApplicationCreationDto app =
        new ApplicationCreationDto(
            "shop",
            null,
            null,
            List.of(
                new ApplicationCreationDto.Service("orders", null, versionId, null, null),
                new ApplicationCreationDto.Service("orders", null, versionId, null, null)));

    assertThatThrownBy(() -> service.previewApplication(alice, app))
        .isInstanceOf(InvalidTemplateSelectionException.class)
        .hasMessageContaining("used twice");
    verifyNoInteractions(provisioner);
  }

  private static ProvisionerClient.RenderResponse rendered() {
    return new ProvisionerClient.RenderResponse(
        List.of(
            new ProvisionerClient.RenderedFile("pom.xml", "base", 10, false, "<project/>", false),
            new ProvisionerClient.RenderedFile(
                "src/main/resources/db/migration/V1__init.sql",
                "feature:flyway",
                20,
                false,
                "CREATE TABLE x();",
                false)),
        30);
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-30T00:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
