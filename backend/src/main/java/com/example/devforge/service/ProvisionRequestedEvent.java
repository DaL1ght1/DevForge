package com.example.devforge.service;

import com.example.devforge.client.ProvisionerClient;

public record ProvisionRequestedEvent(ProvisionerClient.ProvisionRequest request) {}
