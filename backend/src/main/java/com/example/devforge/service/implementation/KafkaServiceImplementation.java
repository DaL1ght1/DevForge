package com.example.devforge.service.implementation;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.config.kafka.KafkaProperties;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.service.KafkaService;
import com.example.devforge.service.ProvisionRequestedEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaServiceImplementation implements KafkaService {

  private final KafkaProperties kafkaProperties;
  private final KafkaTemplate<String, ProvisionerClient.ProvisionRequest> kafkaTemplate;
  private final AppServiceRepository appServiceRepository;
  private final ProvisioningJobRepository provisioningJobRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void sendProvisionRequest(ProvisionRequestedEvent event) {
    ProvisionerClient.ProvisionRequest provisionRequest = event.request();
    provisioningJobRepository
        .findTopByServiceIdOrderByCreatedAtDesc(provisionRequest.serviceId())
        .ifPresent(
            job -> {
              job.setStatus(ProvisioningStatus.GENERATING);
              job.setAttempt(job.getAttempt() + 1);
              job.setStartedAt(Instant.now());
              provisioningJobRepository.save(job);
            });
    log.info(
        "Sending provision request to topic [{}] for service [{}] (ID: {})",
        kafkaProperties.requestTopic(),
        provisionRequest.serviceName(),
        provisionRequest.serviceId());
    kafkaTemplate.send(
        kafkaProperties.requestTopic(), provisionRequest.serviceId().toString(), provisionRequest);
  }

  @KafkaListener(
      topics = "${devforge.kafka.topics.responseTopic}",
      groupId = "${devforge.kafka.group.id}")
  @Transactional
  public void listenProvisionResponse(ProvisionerClient.ProvisionResponse response) {
    log.info(
        "Received provisioning response for service ID [{}] with status [{}]",
        response.serviceId(),
        response.status());

    if (response.serviceId() == null) {
      log.warn("Received response without serviceId: {}", response);
      return;
    }
    appServiceRepository
        .findById(response.serviceId())
        .ifPresentOrElse(
            service -> {
              ProvisioningJob job =
                  provisioningJobRepository
                      .findTopByServiceIdOrderByCreatedAtDesc(response.serviceId())
                      .orElseGet(
                          () ->
                              ProvisioningJob.builder()
                                  .service(service)
                                  .attempt(1)
                                  .startedAt(Instant.now())
                                  .build());
              if ("COMPLETED".equalsIgnoreCase(response.status())) {
                service.setStatus(ServiceStatus.PUSHED);
                service.setRepositoryUrl(response.repositoryUrl());
                job.setStatus(ProvisioningStatus.COMPLETED);
              } else {
                service.setStatus(ServiceStatus.FAILED);
                service.setFailureReason(response.errorMessage());
                job.setStatus(ProvisioningStatus.FAILED);
                job.setErrorMessage(response.errorMessage());
              }
              job.setCompletedAt(Instant.now());
              provisioningJobRepository.save(job);
              appServiceRepository.save(service);
              log.info(
                  "Service [{}] updated to status [{}] with repo [{}]",
                  service.getName(),
                  service.getStatus(),
                  service.getRepositoryUrl());
            },
            () -> log.error("No service found in DB matching ID: {}", response.serviceId()));
  }
}
