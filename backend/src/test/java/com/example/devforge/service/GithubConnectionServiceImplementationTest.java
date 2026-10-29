package com.example.devforge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.devforge.config.github.GithubApiClient;
import com.example.devforge.config.github.GithubApiClient.InstallationInfo;
import com.example.devforge.config.github.GithubAppProperties;
import com.example.devforge.dto.GithubInstallationLinkDto;
import com.example.devforge.entity.GithubAccount;
import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.entity.GithubInstallation;
import com.example.devforge.entity.GithubInstallationStatus;
import com.example.devforge.entity.User;
import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceMember;
import com.example.devforge.entity.WorkspaceRole;
import com.example.devforge.entity.WorkspaceType;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.UnauthorizedAccessException;
import com.example.devforge.mapper.GithubInstallationMapper;
import com.example.devforge.repository.GithubAccountRepository;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.implementation.GithubConnectionServiceImplementation;
import com.example.devforge.service.implementation.GithubLinkStateStore;
import com.example.devforge.service.implementation.GithubUserTokens;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GithubConnectionServiceImplementationTest {

  private static final long GITHUB_USER_ID = 10;
  private static final String TOKEN = "ghu_token";

  private final GithubApiClient githubApiClient = mock(GithubApiClient.class);
  private final GithubUserTokens userTokens = mock(GithubUserTokens.class);
  private final GithubLinkStateStore stateStore = new GithubLinkStateStore();
  private final GithubAccountRepository accountRepository = mock(GithubAccountRepository.class);
  private final GithubInstallationRepository installationRepository =
      mock(GithubInstallationRepository.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final WorkspaceService workspaceService = mock(WorkspaceService.class);
  private final GithubConnectionServiceImplementation service =
      new GithubConnectionServiceImplementation(
          new GithubAppProperties("devforge", "client", "secret", null, null, null, null),
          githubApiClient,
          userTokens,
          stateStore,
          accountRepository,
          installationRepository,
          userRepository,
          workspaceService,
          mock(GithubInstallationMapper.class));

  private final UUID keycloakId = UUID.randomUUID();
  private final User user = User.builder().id(UUID.randomUUID()).keycloakId(keycloakId).build();
  private final GithubAccount account =
      GithubAccount.builder().user(user).githubUserId(GITHUB_USER_ID).login("alice").build();
  private final Workspace personal =
      Workspace.builder().id(UUID.randomUUID()).type(WorkspaceType.PERSONAL).build();
  private final Workspace org =
      Workspace.builder().id(UUID.randomUUID()).type(WorkspaceType.ORGANIZATION).build();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "frontendUrl", "http://front");
    ReflectionTestUtils.setField(service, "backendPublicUrl", "http://back/api/v1");
    when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
    when(userTokens.accessToken(user)).thenReturn(TOKEN);
    when(accountRepository.findByUserId(user.getId())).thenReturn(Optional.of(account));
    when(installationRepository.findByInstallationId(anyLong())).thenReturn(Optional.empty());
    when(installationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    asAdminOf(personal);
    asAdminOf(org);
  }

  @Test
  void installationTheUserCannotSeeOnGithubIsRejected() {
    githubReturns(installation(1, GITHUB_USER_ID, "alice", GithubAccountType.USER));

    assertThatThrownBy(
            () -> service.link(keycloakId, new GithubInstallationLinkDto(personal.getId(), 999L)))
        .isInstanceOf(UnauthorizedAccessException.class);
    verify(installationRepository, never()).save(any());
  }

  @Test
  void personalWorkspaceAcceptsOwnAccount() {
    githubReturns(installation(1, GITHUB_USER_ID, "alice", GithubAccountType.USER));

    service.link(keycloakId, new GithubInstallationLinkDto(personal.getId(), 1L));

    GithubInstallation saved = savedInstallation();
    assertThat(saved.getAccountLogin()).isEqualTo("alice");
    assertThat(saved.getWorkspace()).isSameAs(personal);
    assertThat(saved.getStatus()).isEqualTo(GithubInstallationStatus.ACTIVE);
  }

  @Test
  void personalWorkspaceRejectsOrganizationInstall() {
    githubReturns(installation(2, 20, "acme", GithubAccountType.ORGANIZATION));

    assertThatThrownBy(
            () -> service.link(keycloakId, new GithubInstallationLinkDto(personal.getId(), 2L)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(installationRepository, never()).save(any());
  }

  @Test
  void personalWorkspaceRejectsSomeoneElsesUserAccount() {
    githubReturns(installation(3, 99, "bob", GithubAccountType.USER));

    assertThatThrownBy(
            () -> service.link(keycloakId, new GithubInstallationLinkDto(personal.getId(), 3L)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void organizationWorkspaceRejectsNonAdminOfGithubOrg() {
    githubReturns(installation(2, 20, "acme", GithubAccountType.ORGANIZATION));
    when(githubApiClient.getOrgMembershipRole(TOKEN, "acme")).thenReturn(Optional.of("member"));

    assertThatThrownBy(
            () -> service.link(keycloakId, new GithubInstallationLinkDto(org.getId(), 2L)))
        .isInstanceOf(UnauthorizedAccessException.class);
    verify(installationRepository, never()).save(any());
  }

  @Test
  void organizationWorkspaceAcceptsGithubOrgAdmin() {
    githubReturns(installation(2, 20, "acme", GithubAccountType.ORGANIZATION));
    when(githubApiClient.getOrgMembershipRole(TOKEN, "acme")).thenReturn(Optional.of("admin"));

    service.link(keycloakId, new GithubInstallationLinkDto(org.getId(), 2L));

    assertThat(savedInstallation().getAccountType()).isEqualTo(GithubAccountType.ORGANIZATION);
  }

  @Test
  void installationLinkedToAnotherWorkspaceIsAConflict() {
    githubReturns(installation(2, 20, "acme", GithubAccountType.ORGANIZATION));
    when(githubApiClient.getOrgMembershipRole(TOKEN, "acme")).thenReturn(Optional.of("admin"));
    Workspace other = Workspace.builder().id(UUID.randomUUID()).build();
    when(installationRepository.findByInstallationId(2L))
        .thenReturn(
            Optional.of(GithubInstallation.builder().installationId(2L).workspace(other).build()));

    assertThatThrownBy(
            () -> service.link(keycloakId, new GithubInstallationLinkDto(org.getId(), 2L)))
        .isInstanceOf(ConflictException.class);
    verify(installationRepository, never()).save(any());
  }

  @Test
  void suspendedInstallationCannotBeLinked() {
    when(githubApiClient.listUserInstallations(TOKEN))
        .thenReturn(
            List.of(
                new InstallationInfo(
                    1, GITHUB_USER_ID, "alice", GithubAccountType.USER, "all", true)));

    assertThatThrownBy(
            () -> service.link(keycloakId, new GithubInstallationLinkDto(personal.getId(), 1L)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void callbackWithUnknownStateDoesNothing() {
    String redirect = service.handleCallback("code", "forged-state", 1L, null);

    assertThat(redirect).isEqualTo("http://front/workspaces?github=error&reason=invalid_state");
    verifyNoInteractions(githubApiClient);
  }

  @Test
  void callbackStateIsSingleUse() {
    String state = stateStore.create(keycloakId, personal.getId());
    when(githubApiClient.exchangeCode(eq("code"), any()))
        .thenReturn(new GithubApiClient.UserTokens(TOKEN, null, null, null));
    when(githubApiClient.getUser(TOKEN))
        .thenReturn(new GithubApiClient.GithubUser(GITHUB_USER_ID, "alice"));
    when(userTokens.saveAuthorization(any(), any(), any())).thenReturn(account);

    assertThat(service.handleCallback("code", state, null, null))
        .isEqualTo("http://front/workspaces/" + personal.getId() + "?github=authorized");
    assertThat(service.handleCallback("code", state, null, null)).contains("invalid_state");
  }

  @Test
  void callbackLinksVerifiedInstallation() {
    String state = stateStore.create(keycloakId, personal.getId());
    when(githubApiClient.exchangeCode(eq("code"), any()))
        .thenReturn(new GithubApiClient.UserTokens(TOKEN, null, null, null));
    when(githubApiClient.getUser(TOKEN))
        .thenReturn(new GithubApiClient.GithubUser(GITHUB_USER_ID, "alice"));
    when(userTokens.saveAuthorization(any(), any(), any())).thenReturn(account);
    githubReturns(installation(1, GITHUB_USER_ID, "alice", GithubAccountType.USER));

    assertThat(service.handleCallback("code", state, 1L, null)).endsWith("?github=linked");
    assertThat(savedInstallation().getInstallationId()).isEqualTo(1L);
  }

  private void asAdminOf(Workspace workspace) {
    when(workspaceService.requireRole(workspace.getId(), keycloakId, WorkspaceRole.ADMIN))
        .thenReturn(
            WorkspaceMember.builder().workspace(workspace).role(WorkspaceRole.OWNER).build());
  }

  private void githubReturns(InstallationInfo... installations) {
    when(githubApiClient.listUserInstallations(TOKEN)).thenReturn(List.of(installations));
  }

  private static InstallationInfo installation(
      long id, long accountId, String login, GithubAccountType type) {
    return new InstallationInfo(id, accountId, login, type, "all", false);
  }

  private GithubInstallation savedInstallation() {
    ArgumentCaptor<GithubInstallation> captor = ArgumentCaptor.forClass(GithubInstallation.class);
    verify(installationRepository).save(captor.capture());
    return captor.getValue();
  }
}
