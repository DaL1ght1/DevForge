package com.example.devforge.service;

import com.example.devforge.client.ProvisionerClient;

public interface KafkaService {

  void sendProvisionRequest(String topic, String key, ProvisionerClient.ProvisionRequest request);
}
