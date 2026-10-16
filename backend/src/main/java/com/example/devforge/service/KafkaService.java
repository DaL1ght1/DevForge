package com.example.devforge.service;

import com.example.devforge.dto.ProvisionRequestedEvent;

public interface KafkaService {

  void sendProvisionRequest(ProvisionRequestedEvent event);
}
