package com.example.devforge.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.entity.OutboxEvent;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.repository.OutboxEventRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.service.KafkaService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class ProvisioningOutboxRelayTest {

  private final OutboxEventRepository events = mock(OutboxEventRepository.class);
  private final ProvisioningJobRepository jobs = mock(ProvisioningJobRepository.class);
  private final KafkaService kafka = mock(KafkaService.class);
  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final TransactionTemplate tx = mock(TransactionTemplate.class);
  private final ProvisioningOutboxRelay relay =
      new ProvisioningOutboxRelay(events, jobs, kafka, jsonMapper, tx);

  @SuppressWarnings("unchecked")
  private void runTransactionsInline() {
    when(tx.execute(any()))
        .thenAnswer(
            inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
  }

  @Test
  void sendsUnsentEventsAndMarksJobsGenerating() {
    runTransactionsInline();
    OutboxEvent event = event();
    when(events.lockUnsent(anyInt())).thenReturn(List.of(event));

    relay.drain();

    verify(kafka).sendProvisionRequest(eq("requests"), eq("svc"), any());
    assertThat(event.getSentAt()).isNotNull();
    verify(jobs)
        .transition(
            eq(event.getJobId()),
            eq(ProvisioningStatus.PENDING),
            eq(ProvisioningStatus.GENERATING),
            any());
  }

  @Test
  void failedSendKeepsEventForRetryAndRecordsError() {
    runTransactionsInline();
    OutboxEvent first = event();
    OutboxEvent second = event();
    when(events.lockUnsent(anyInt())).thenReturn(List.of(first, second));
    doThrow(new IllegalStateException("broker down"))
        .when(kafka)
        .sendProvisionRequest(any(), any(), any());

    relay.drain();

    assertThat(first.getSentAt()).isNull();
    assertThat(first.getAttempts()).isEqualTo(1);
    assertThat(first.getLastError()).isEqualTo("broker down");
    verify(kafka, times(1)).sendProvisionRequest(any(), any(), any());
    verify(jobs, never()).transition(any(), any(), any(), any());
  }

  private OutboxEvent event() {
    ProvisionerClient.ProvisionRequest request =
        new ProvisionerClient.ProvisionRequest(
            ProvisionerClient.CONTRACT_VERSION,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "demo",
            "spring",
            "1.0.0",
            null,
            "com.devforge.demo",
            "Demo",
            null,
            null,
            null,
            null,
            1L,
            "acme",
            "Organization",
            "private");
    return OutboxEvent.builder()
        .id(UUID.randomUUID())
        .topic("requests")
        .messageKey("svc")
        .payload(jsonMapper.writeValueAsString(request))
        .jobId(request.jobId())
        .build();
  }
}
