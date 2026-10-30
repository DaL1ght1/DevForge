package com.example.devforge.service;

import com.example.devforge.dto.InternalJobClaimResponse;
import java.util.UUID;

public interface InternalProvisioningService {

  InternalJobClaimResponse claim(String internalToken, UUID jobId);

  void recordRepository(String internalToken, UUID jobId, long repositoryId);

  byte[] templateArtifact(String internalToken, String digest);
}
