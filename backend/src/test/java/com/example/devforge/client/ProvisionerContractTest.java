package com.example.devforge.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ProvisionerContractTest {

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void requestSerializesWithFieldNamesTheWorkerExpects() {
    ProvisionerClient.ProvisionRequest request =
        new ProvisionerClient.ProvisionRequest(
            ProvisionerClient.CONTRACT_VERSION,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "billing",
            "spring",
            "1.0.0",
            "com.acme.billing",
            "Billing",
            "d",
            "POSTGRESQL",
            123456789012L,
            "acme",
            "Organization",
            "private");

    JsonNode json = mapper.readTree(mapper.writeValueAsString(request));

    assertThat(json.propertyNames())
        .containsExactlyInAnyOrder(
            "contractVersion",
            "jobId",
            "serviceId",
            "serviceName",
            "templateName",
            "templateVersion",
            "packageName",
            "className",
            "description",
            "databaseType",
            "installationId",
            "ownerLogin",
            "ownerType",
            "visibility");
    assertThat(json.get("installationId").asLong()).isEqualTo(123456789012L);
  }

  @Test
  void responseFromWorkerDeserializes() {
    String fromGo =
        """
        {"jobId":"11111111-1111-1111-1111-111111111111",
         "serviceId":"22222222-2222-2222-2222-222222222222",
         "serviceName":"billing","repositoryUrl":"https://github.com/acme/billing",
         "repositoryId":9,"status":"COMPLETED"}
        """;

    ProvisionerClient.ProvisionResponse response =
        mapper.readValue(fromGo, ProvisionerClient.ProvisionResponse.class);

    assertThat(response.jobId()).hasToString("11111111-1111-1111-1111-111111111111");
    assertThat(response.repositoryId()).isEqualTo(9L);
    assertThat(response.status()).isEqualTo("COMPLETED");
  }
}
