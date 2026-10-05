package com.example.devforge.dto;

import com.example.devforge.client.ProvisionerClient;

import java.io.Serializable;

public record ProvisionRequestedEvent(
        ProvisionerClient.ProvisionRequest request
) implements Serializable {
}