package com.example.devforge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.devforge.entity.AppService;
import com.example.devforge.entity.GithubAccount;
import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.entity.GithubInstallation;
import com.example.devforge.entity.GithubInstallationStatus;
import com.example.devforge.entity.ProvisioningJob;
import com.example.devforge.entity.ProvisioningStatus;
import com.example.devforge.entity.User;
import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceType;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.InvalidCredentialsException;
import com.example.devforge.repository.GithubAccountRepository;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.ProvisioningJobRepository;
import com.example.devforge.service.implementation.GithubUserTokens;
import com.example.devforge.service.implementation.ProvisioningCredentialServiceImplementation;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProvisioningCredentialServiceImplementationTest {

  private static final String SECRET = "internal-secret";

  private final ProvisioningJobRepository jobRepository = mock(ProvisioningJobRepository.class);
  private final GithubInstallationRepository installationRepository =
      mock(GithubInstallationRepository.class);
  private final GithubAccountRepository accountRepository = mock(GithubAccountRepository.class);
  private final GithubUserTokens userTokens = mock(GithubUserTokens.class);
  private final ProvisioningCredentialServiceImplementation service =
      new ProvisioningCredentialServiceImplementation(
          jobRepository, installationRepository, accountRepository, userTokens);

  private final UUID jobId = UUID.randomUUID();
  private final User owner = User.builder().id(UUID.randomUUID()).build();
  private final Workspace workspace =
      Workspace.builder().id(UUID.randomUUID()).type(WorkspaceType.PERSONAL).build();
  private final GithubInstallation installation =
      GithubInstallation.builder()
          .installationId(5L)
          .accountId(10L)
          .accountType(GithubAccountType.USER)
          .status(GithubInstallationStatus.ACTIVE)
          .workspace(workspace)
          .build();
  private ProvisioningJob job;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "internalApiToken", SECRET);
    job =
        ProvisioningJob.builder()
            .id(jobId)
            .status(ProvisioningStatus.GENERATING)
            .installationId(5L)
            .service(AppService.builder().owner(owner).workspace(workspace).build())
            .build();
    when(jobRepository.findWithServiceById(jobId)).thenReturn(Optional.of(job));
    when(installationRepository.findByInstallationId(5L)).thenReturn(Optional.of(installation));
    when(accountRepository.findByUserId(owner.getId()))
        .thenReturn(Optional.of(GithubAccount.builder().githubUserId(10L).login("alice").build()));
    when(userTokens.accessToken(owner)).thenReturn("ghu_token");
  }

  @Test
  void issuesOwnersTokenForInFlightPersonalJob() {
    assertThat(service.userTokenForJob(SECRET, jobId)).isEqualTo("ghu_token");
  }

  @Test
  void wrongOrMissingSecretIsRejected() {
    assertThatThrownBy(() -> service.userTokenForJob("guess", jobId))
        .isInstanceOf(InvalidCredentialsException.class);
    assertThatThrownBy(() -> service.userTokenForJob(null, jobId))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(userTokens);
  }

  @Test
  void disabledWhenNoSecretConfigured() {
    ReflectionTestUtils.setField(service, "internalApiToken", "");

    assertThatThrownBy(() -> service.userTokenForJob("", jobId))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void finishedJobGetsNoToken() {
    job.setStatus(ProvisioningStatus.COMPLETED);

    assertThatThrownBy(() -> service.userTokenForJob(SECRET, jobId))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(userTokens);
  }

  @Test
  void organizationJobGetsNoUserToken() {
    workspace.setType(WorkspaceType.ORGANIZATION);

    assertThatThrownBy(() -> service.userTokenForJob(SECRET, jobId))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(userTokens);
  }

  @Test
  void ownerWhoIsNotTheDestinationAccountGetsNoToken() {
    when(accountRepository.findByUserId(owner.getId()))
        .thenReturn(Optional.of(GithubAccount.builder().githubUserId(99L).login("bob").build()));

    assertThatThrownBy(() -> service.userTokenForJob(SECRET, jobId))
        .isInstanceOf(ConflictException.class);
    verifyNoInteractions(userTokens);
  }

  @Test
  void removedInstallationGetsNoToken() {
    installation.setStatus(GithubInstallationStatus.REMOVED);

    assertThatThrownBy(() -> service.userTokenForJob(SECRET, jobId))
        .isInstanceOf(ConflictException.class);
  }
}
