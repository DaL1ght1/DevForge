package com.example.devforge.service;

import com.example.devforge.client.ProvisionerClient;

public interface KafkaService {

  void sendProvisionRequest(ProvisionerClient.ProvisionRequest provisionRequest);
}
