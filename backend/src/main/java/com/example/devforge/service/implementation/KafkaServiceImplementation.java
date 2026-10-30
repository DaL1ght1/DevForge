package com.example.devforge.service.implementation;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.entity.AppService;
import com.example.devforge.entity.Application;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.repository.ApplicationRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.service.KafkaService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaServiceImplementation implements KafkaService {

  private static final long SEND_TIMEOUT_SECONDS = 10;
  private static final Set<ProvisioningStatus> TERMINAL =
      Set.of(ProvisioningStatus.COMPLETED, ProvisioningStatus.FAILED);

  private final KafkaTemplate<String, ProvisionerClient.ProvisionRequest> kafkaTemplate;
  private final AppServiceRepository appServiceRepository;
  private final ApplicationRepository applicationRepository;
  private final ProvisioningJobRepository provisioningJobRepository;

  @Override
  public void sendProvisionRequest(
      String topic, String key, ProvisionerClient.ProvisionRequest provisionRequest) {
    log.info(
        "Sending provision request to topic [{}] for service [{}] (ID: {}, job: {})",
        topic,
        provisionRequest.serviceName(),
        provisionRequest.serviceId(),
        provisionRequest.jobId());
    try {
      kafkaTemplate.send(topic, key, provisionRequest).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while sending provision request", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("Kafka send failed: " + e.getMessage(), e);
    }
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
    if (job.getApplication() != null) {
      completeApplication(job, response);
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

  private void completeApplication(
      ProvisioningJob job, ProvisionerClient.ProvisionResponse response) {
    Application application = job.getApplication();
    boolean completed = "COMPLETED".equalsIgnoreCase(response.status());
    application.setStatus(completed ? ServiceStatus.PUSHED : ServiceStatus.FAILED);
    if (completed) {
      application.setRepositoryUrl(response.repositoryUrl());
      application.setRepositoryId(response.repositoryId());
      job.setStatus(ProvisioningStatus.COMPLETED);
    } else {
      application.setFailureReason(response.errorMessage());
      job.setStatus(ProvisioningStatus.FAILED);
      job.setErrorMessage(response.errorMessage());
    }
    List<AppService> services =
        appServiceRepository.findAllByApplicationIdOrderByPort(application.getId());
    for (AppService service : services) {
      service.setStatus(application.getStatus());
      if (completed) {
        service.setRepositoryUrl(
            response.repositoryUrl() + "/tree/main/services/" + service.getName());
        service.setRepositoryId(response.repositoryId());
      } else {
        service.setFailureReason(response.errorMessage());
      }
    }
    job.setCompletedAt(Instant.now());
    provisioningJobRepository.save(job);
    applicationRepository.save(application);
    appServiceRepository.saveAll(services);
    log.info(
        "Application [{}] updated to status [{}] with repo [{}]",
        application.getName(),
        application.getStatus(),
        application.getRepositoryUrl());
  }

  private Optional<ProvisioningJob> findJob(ProvisionerClient.ProvisionResponse response) {
    if (response.jobId() == null) {
      return provisioningJobRepository.findTopByServiceIdOrderByCreatedAtDesc(response.serviceId());
    }
    return provisioningJobRepository
        .findById(response.jobId())
        .filter(job -> job.getService().getId().equals(response.serviceId()));
  }
}
