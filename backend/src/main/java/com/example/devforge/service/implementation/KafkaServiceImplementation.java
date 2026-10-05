package com.example.devforge.service.implementation;

import com.example.devforge.client.ProvisionerClient;
import com.example.devforge.config.kafka.KafkaProperties;
import com.example.devforge.entity.ServiceStatus;
import com.example.devforge.repository.AppServiceRepository;
import com.example.devforge.service.KafkaService;
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

    private final KafkaProperties kafkaProperties;
    private final KafkaTemplate<String, ProvisionerClient.ProvisionRequest> kafkaTemplate;
    private final AppServiceRepository appServiceRepository;

    @Override
    public void sendProvisionRequest(ProvisionerClient.ProvisionRequest provisionRequest) {
        log.info("Sending provision request to topic [{}] for service [{}] (ID: {})",
                kafkaProperties.requestTopic(), provisionRequest.serviceName(), provisionRequest.serviceId());
        kafkaTemplate.send(kafkaProperties.requestTopic(), provisionRequest.serviceId().toString(), provisionRequest);
    }

    @KafkaListener(
            topics = "${devforge.kafka.topics.responseTopic}",
            groupId = "${devforge.kafka.group.id}"
    )
    @Transactional
    public void listenProvisionResponse(ProvisionerClient.ProvisionResponse response) {
        log.info("Received provisioning response for service ID [{}] with status [{}]",
                response.serviceId(), response.status());

        if (response.serviceId() == null) {
            log.warn("Received response without serviceId: {}", response);
            return;
        }
        appServiceRepository.findById(response.serviceId()).ifPresentOrElse(service -> {
            if ("COMPLETED".equalsIgnoreCase(response.status())) {
                service.setStatus(ServiceStatus.PUSHED);
                service.setRepositoryUrl(response.repositoryUrl());
            } else {
                service.setStatus(ServiceStatus.FAILED);
            }
            appServiceRepository.save(service);
            log.info("Service [{}] updated to status [{}] with repo [{}]",
                    service.getName(), service.getStatus(), service.getRepositoryUrl());
        }, () -> log.error("No service found in DB matching ID: {}", response.serviceId()));
    }
}