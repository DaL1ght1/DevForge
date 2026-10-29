package com.example.devforge.service;

import java.util.UUID;

public interface ProvisioningCredentialService {

  String userTokenForJob(String internalApiToken, UUID jobId);
}
