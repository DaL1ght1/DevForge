package com.example.devforge.outbox;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.entity.OutboxEvent;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.repository.OutboxEventRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.service.KafkaService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProvisioningOutboxRelay {

  private static final int BATCH_SIZE = 50;
  private static final int MAX_ERROR_LENGTH = 2000;

  private final OutboxEventRepository events;
  private final ProvisioningJobRepository jobs;
  private final KafkaService kafkaService;
  private final JsonMapper jsonMapper;
  private final TransactionTemplate transactionTemplate;
  private final AtomicBoolean draining = new AtomicBoolean();

  public void drainAsync() {
    Thread.ofVirtual().name("outbox-relay").start(this::drain);
  }

  @Scheduled(
      fixedDelayString = "${devforge.outbox.poll-interval:2s}",
      initialDelayString = "${devforge.outbox.poll-interval:2s}")
  public void drain() {
    if (!draining.compareAndSet(false, true)) return;
    try {
      while (Boolean.TRUE.equals(transactionTemplate.execute(_ -> publishBatch()))) {}
    } catch (RuntimeException e) {
      log.warn("Outbox relay stopped early: {}", e.getMessage());
    } finally {
      draining.set(false);
    }
  }

  private boolean publishBatch() {
    List<OutboxEvent> batch = events.lockUnsent(BATCH_SIZE);
    for (OutboxEvent event : batch) {
      try {
        ProvisionerClient.ProvisionRequest request =
            jsonMapper.readValue(event.getPayload(), ProvisionerClient.ProvisionRequest.class);
        kafkaService.sendProvisionRequest(event.getTopic(), event.getMessageKey(), request);
      } catch (RuntimeException e) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(truncate(e.getMessage()));
        log.warn("Outbox event {} not sent (attempt {})", event.getId(), event.getAttempts(), e);
        return false;
      }
      event.setSentAt(Instant.now());
      if (event.getJobId() != null) {
        jobs.transition(
            event.getJobId(),
            ProvisioningStatus.PENDING,
            ProvisioningStatus.GENERATING,
            Instant.now());
      }
    }
    return batch.size() == BATCH_SIZE;
  }

  private static String truncate(String message) {
    if (message == null) return null;
    return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
  }
}
