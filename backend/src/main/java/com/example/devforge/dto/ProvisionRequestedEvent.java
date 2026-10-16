package com.example.devforge.dto;

import com.example.devforge.client.ProvisionerClient;

public record ProvisionRequestedEvent(ProvisionerClient.ProvisionRequest request) {}
