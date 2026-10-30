package com.example.devforge.service.implementation;

import com.example.devforge.entity.AppService;
import com.example.devforge.entity.GithubAccount;
import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.entity.GithubInstallation;
import com.example.devforge.entity.GithubInstallationStatus;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.entity.WorkspaceType;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.ResourceNotFoundException;
import com.example.devforge.repository.GithubAccountRepository;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.security.InternalApiGuard;
import com.example.devforge.service.ProvisioningCredentialService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvisioningCredentialServiceImplementation implements ProvisioningCredentialService {

  private final ProvisioningJobRepository jobRepository;
  private final GithubInstallationRepository installationRepository;
  private final GithubAccountRepository accountRepository;
  private final GithubUserTokens userTokens;
  private final InternalApiGuard internalApiGuard;

  @Override
  public String userTokenForJob(String providedToken, UUID jobId) {
    requireInternalCaller(providedToken);
    ProvisioningJob job =
        jobRepository
            .findWithServiceById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Provisioning job not found"));
    if (job.getStatus() != ProvisioningStatus.GENERATING || job.getInstallationId() == null) {
      throw new ConflictException("Provisioning job is not in progress");
    }
    AppService service = job.getService();
    if (service.getWorkspace().getType() != WorkspaceType.PERSONAL) {
      throw new ConflictException("User tokens are only issued for personal workspaces");
    }
    GithubInstallation installation =
        installationRepository
            .findByInstallationId(job.getInstallationId())
            .filter(i -> i.getStatus() == GithubInstallationStatus.ACTIVE)
            .filter(i -> i.getAccountType() == GithubAccountType.USER)
            .filter(i -> i.getWorkspace().getId().equals(service.getWorkspace().getId()))
            .orElseThrow(() -> new ConflictException("Job installation is no longer valid"));
    GithubAccount account =
        accountRepository
            .findByUserId(service.getOwner().getId())
            .filter(a -> a.getGithubUserId() == installation.getAccountId())
            .orElseThrow(
                () -> new ConflictException("Service owner is not the destination GitHub account"));
    String token = userTokens.accessToken(service.getOwner());
    log.info("Issued GitHub user token for job {} (destination {})", jobId, account.getLogin());
    return token;
  }

  private void requireInternalCaller(String providedToken) {
    internalApiGuard.require(providedToken);
  }
}
