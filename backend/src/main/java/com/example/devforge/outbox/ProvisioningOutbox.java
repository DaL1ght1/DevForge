package com.example.devforge.outbox;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.config.kafka.KafkaProperties;
import com.example.devforge.dto.ProvisionRequestedEvent;
import com.example.devforge.entity.OutboxEvent;
import com.example.devforge.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ProvisioningOutbox {

  private final OutboxEventRepository events;
  private final KafkaProperties kafkaProperties;
  private final JsonMapper jsonMapper;
  private final ProvisioningOutboxRelay relay;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void enqueue(ProvisionRequestedEvent event) {
    ProvisionerClient.ProvisionRequest request = event.request();
    events.save(
        OutboxEvent.builder()
            .topic(kafkaProperties.requestTopic())
            .messageKey(request.serviceId().toString())
            .payload(jsonMapper.writeValueAsString(request))
            .jobId(request.jobId())
            .build());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publishCommitted(ProvisionRequestedEvent event) {
    relay.drainAsync();
  }
}
