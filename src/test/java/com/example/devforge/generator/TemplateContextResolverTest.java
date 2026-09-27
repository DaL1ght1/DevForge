package com.example.devforge.generator;

import com.example.devforge.dto.AppServiceCreationDto;
import com.example.devforge.generator.model.TemplateContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateContextResolverTest {

    private final TemplateContextResolver resolver = new TemplateContextResolver();

    @Test
    void shouldResolveKebabCaseServiceCorrectly() {
        AppServiceCreationDto dto = new AppServiceCreationDto(
                "payment-service",
                "Handles payment checkout",
                UUID.randomUUID(),
                "POSTGRESQL"
        );

        TemplateContext context = resolver.resolve(dto);

        assertThat(context.serviceName()).isEqualTo("payment-service");
        assertThat(context.className()).isEqualTo("PaymentService");
        assertThat(context.packageName()).isEqualTo("com.devforge.paymentservice");
        assertThat(context.packagePath()).isEqualTo("com/devforge/paymentservice");
        assertThat(context.asMap()).containsEntry("CLASS_NAME", "PaymentService");
    }
}