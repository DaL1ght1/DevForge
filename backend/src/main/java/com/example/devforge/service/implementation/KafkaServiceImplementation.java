package com.example.devforge.service.implementation;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.config.kafka.KafkaProperties;
import com.example.devforge.dto.ProvisionRequestedEvent;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.service.KafkaService;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
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

  private static final Set<ProvisioningStatus> TERMINAL =
      Set.of(ProvisioningStatus.COMPLETED, ProvisioningStatus.FAILED);

  private final KafkaProperties kafkaProperties;
  private final KafkaTemplate<String, ProvisionerClient.ProvisionRequest> kafkaTemplate;
  private final AppServiceRepository appServiceRepository;
  private final ProvisioningJobRepository provisioningJobRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void sendProvisionRequest(ProvisionRequestedEvent event) {
    ProvisionerClient.ProvisionRequest provisionRequest = event.request();
    provisioningJobRepository
        .findById(provisionRequest.jobId())
        .ifPresent(
            job -> {
              job.setStatus(ProvisioningStatus.GENERATING);
              job.setAttempt(job.getAttempt() + 1);
              job.setStartedAt(Instant.now());
              provisioningJobRepository.save(job);
            });
    log.info(
        "Sending provision request to topic [{}] for service [{}] (ID: {}, job: {})",
        kafkaProperties.requestTopic(),
        provisionRequest.serviceName(),
        provisionRequest.serviceId(),
        provisionRequest.jobId());
    kafkaTemplate.send(
        kafkaProperties.requestTopic(), provisionRequest.serviceId().toString(), provisionRequest);
  }

  @KafkaListener(
      topics = "${devforge.kafka.topics.responseTopic}",
      groupId = "${devforge.kafka.group.id}")
  @Transactional
  public void listenProvisionResponse(ProvisionerClient.ProvisionResponse response) {
    log.info(
        "Received provisioning response for service ID [{}] (job: {}) with status [{}]",
        response.serviceId(),
        response.jobId(),
        response.status());

    if (response.serviceId() == null) {
      log.warn("Received response without serviceId: {}", response);
      return;
    }
    Optional<AppService> found = appServiceRepository.findById(response.serviceId());
    if (found.isEmpty()) {
      log.error("No service found in DB matching ID: {}", response.serviceId());
      return;
    }
    AppService service = found.get();
    Optional<ProvisioningJob> matchedJob = findJob(response);
    if (response.jobId() != null && matchedJob.isEmpty()) {
      log.warn(
          "Ignoring response for job {} that does not belong to service {}",
          response.jobId(),
          response.serviceId());
      return;
    }
    ProvisioningJob job =
        matchedJob.orElseGet(
            () ->
                ProvisioningJob.builder()
                    .service(service)
                    .attempt(1)
                    .startedAt(Instant.now())
                    .build());
    if (response.jobId() != null && TERMINAL.contains(job.getStatus())) {
      log.info("Ignoring duplicate response for finished job {}", job.getId());
      return;
    }
    if ("COMPLETED".equalsIgnoreCase(response.status())) {
      service.setStatus(ServiceStatus.PUSHED);
      service.setRepositoryUrl(response.repositoryUrl());
      service.setRepositoryId(response.repositoryId());
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
  }

  /** Responses with a job ID must match that exact job; legacy responses use the latest job. */
  private Optional<ProvisioningJob> findJob(ProvisionerClient.ProvisionResponse response) {
    if (response.jobId() == null) {
      return provisioningJobRepository.findTopByServiceIdOrderByCreatedAtDesc(response.serviceId());
    }
    return provisioningJobRepository
        .findById(response.jobId())
        .filter(job -> job.getService().getId().equals(response.serviceId()));
  }
}
