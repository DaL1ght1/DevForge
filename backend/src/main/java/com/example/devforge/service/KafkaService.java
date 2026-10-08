package com.example.devforge.service;

public interface KafkaService {

  void sendProvisionRequest(ProvisionRequestedEvent event);
}
