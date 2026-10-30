package com.example.devforge.service.implementation;

import com.example.devforge.dto.InternalJobClaimResponse;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.entity.TemplateArtifact;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.ResourceNotFoundException;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.repository.TemplateArtifactRepository;
import com.example.devforge.security.InternalApiGuard;
import com.example.devforge.service.InternalProvisioningService;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalProvisioningServiceImplementation implements InternalProvisioningService {

  private static final Pattern DIGEST = Pattern.compile("^[0-9a-f]{64}$");
  private static final EnumSet<ProvisioningStatus> CLAIMABLE =
      EnumSet.of(ProvisioningStatus.PENDING, ProvisioningStatus.GENERATING);

  private final InternalApiGuard guard;
  private final ProvisioningJobRepository jobs;
  private final TemplateArtifactRepository artifacts;

  @Override
  @Transactional
  public InternalJobClaimResponse claim(String internalToken, UUID jobId) {
    guard.require(internalToken);
    int claimed = jobs.claim(jobId, CLAIMABLE, ProvisioningStatus.GENERATING, Instant.now());
    ProvisioningJob job =
        jobs.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Provisioning job not found"));
    if (claimed == 0) {
      log.info("Job {} is already {}; skipping redelivered request", jobId, job.getStatus());
      return new InternalJobClaimResponse(false, job.getAttempt(), job.getRepositoryId());
    }
    return new InternalJobClaimResponse(true, job.getAttempt(), job.getRepositoryId());
  }

  @Override
  @Transactional
  public void recordRepository(String internalToken, UUID jobId, long repositoryId) {
    guard.require(internalToken);
    if (jobs.recordRepository(jobId, repositoryId, ProvisioningStatus.GENERATING) == 0) {
      throw new ConflictException("Provisioning job is not in progress");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] templateArtifact(String internalToken, String digest) {
    guard.require(internalToken);
    if (digest == null || !DIGEST.matcher(digest).matches()) {
      throw new ResourceNotFoundException("Template artifact not found");
    }
    return artifacts
        .findById(digest)
        .map(TemplateArtifact::getData)
        .orElseThrow(() -> new ResourceNotFoundException("Template artifact not found"));
  }
}
