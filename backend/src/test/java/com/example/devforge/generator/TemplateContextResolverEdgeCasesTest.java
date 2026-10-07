package com.example.devforge.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.devforge.client.TemplateContextResolver;
import com.example.devforge.client.model.TemplateContext;
import com.example.devforge.dto.AppServiceCreationDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemplateContextResolverEdgeCasesTest {

  private final TemplateContextResolver resolver = new TemplateContextResolver();

  @Test
  void normalizesWhitespaceAndCase() {
    TemplateContext context =
        resolver.resolve(
            new AppServiceCreationDto("  Billing_API  ", "billing", UUID.randomUUID(), "NONE"));

    assertThat(context.serviceName()).isEqualTo("billing_api");
    assertThat(context.className()).isEqualTo("BillingApi");
    assertThat(context.packageName()).isEqualTo("com.devforge.billingapi");
    assertThat(context.asMap())
        .containsEntry("SERVICE_NAME", "billing_api")
        .containsEntry("DATABASE_TYPE", "NONE");
  }

  @Test
  void convertsMixedSeparatorsToPascalCase() {
    TemplateContext context =
        resolver.resolve(
            new AppServiceCreationDto(
                "order-processing_v2", null, UUID.randomUUID(), "POSTGRESQL"));

    assertThat(context.className()).isEqualTo("OrderProcessingV2");
    assertThat(context.packagePath()).isEqualTo("com/devforge/orderprocessingv2");
  }

  @Test
  void preservesDescriptionInResolvedContext() {
    String description = "Creates invoices for customers";

    TemplateContext context =
        resolver.resolve(
            new AppServiceCreationDto("invoice-service", description, UUID.randomUUID(), "MYSQL"));

    assertThat(context.description()).isEqualTo(description);
  }
}
