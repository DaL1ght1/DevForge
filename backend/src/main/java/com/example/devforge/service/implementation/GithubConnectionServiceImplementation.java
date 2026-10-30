package com.example.devforge.service.implementation;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.devforge.config.github.GithubApiClient;
import com.example.devforge.config.github.GithubApiClient.InstallationInfo;
import com.example.devforge.config.github.GithubAppProperties;
import com.example.devforge.dto.GithubAccountResponse;
import com.example.devforge.dto.GithubInstallationCandidateResponse;
import com.example.devforge.dto.GithubInstallationLinkDto;
import com.example.devforge.dto.GithubInstallationResponse;
import com.example.devforge.entity.GithubAccount;
import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.entity.GithubInstallation;
import com.example.devforge.entity.GithubInstallationStatus;
import com.example.devforge.entity.User;
import com.example.devforge.entity.Workspace;
import com.example.devforge.entity.WorkspaceRole;
import com.example.devforge.entity.WorkspaceType;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.GithubIntegrationException;
import com.example.devforge.exception.GithubReauthorizationRequiredException;
import com.example.devforge.exception.ResourceNotFoundException;
import com.example.devforge.exception.UnauthorizedAccessException;
import com.example.devforge.exception.UserNotFoundException;
import com.example.devforge.exception.WorkspaceNotFoundException;
import com.example.devforge.mapper.GithubInstallationMapper;
import com.example.devforge.repository.GithubAccountRepository;
import com.example.devforge.repository.GithubInstallationRepository;
import com.example.devforge.repository.UserRepository;
import com.example.devforge.service.GithubConnectionService;
import com.example.devforge.service.WorkspaceService;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubConnectionServiceImplementation implements GithubConnectionService {

  private final GithubAppProperties props;
  private final GithubApiClient githubApiClient;
  private final GithubUserTokens userTokens;
  private final GithubLinkStateStore stateStore;
  private final GithubAccountRepository accountRepository;
  private final GithubInstallationRepository installationRepository;
  private final UserRepository userRepository;
  private final WorkspaceService workspaceService;
  private final GithubInstallationMapper installationMapper;

  @Value("${devforge.frontend-url}")
  private String frontendUrl;

  @Value("${devforge.backend-public-url}")
  private String backendPublicUrl;

  @Override
  public String authorizationUrl(UUID keycloakId, UUID workspaceId) {
    props.requireConfigured();
    workspaceService.requireRole(workspaceId, keycloakId, WorkspaceRole.ADMIN);
    return UriComponentsBuilder.fromUriString(props.webUrl())
        .path("/login/oauth/authorize")
        .queryParam("client_id", "{clientId}")
        .queryParam("redirect_uri", "{redirectUri}")
        .queryParam("state", "{state}")
        .encode()
        .buildAndExpand(
            Map.of(
                "clientId", props.clientId(),
                "redirectUri", callbackUri(),
                "state", stateStore.create(keycloakId, workspaceId)))
        .toUriString();
  }

  @Override
  public String installationUrl(UUID keycloakId, UUID workspaceId) {
    props.requireConfigured();
    workspaceService.requireRole(workspaceId, keycloakId, WorkspaceRole.ADMIN);
    return UriComponentsBuilder.fromUriString(props.webUrl())
        .path("/apps/{slug}/installations/new")
        .queryParam("state", "{state}")
        .encode()
        .buildAndExpand(
            Map.of("slug", props.appSlug(), "state", stateStore.create(keycloakId, workspaceId)))
        .toUriString();
  }

  @Override
  public String handleCallback(String code, String state, Long installationId, String error) {
    GithubLinkStateStore.PendingLink pending = stateStore.consume(state).orElse(null);
    if (pending == null) {
      log.warn("GitHub callback with invalid or expired state");
      return frontendRedirect(null, "error", "invalid_state");
    }
    UUID workspaceId = pending.workspaceId();
    if (error != null) {
      log.warn("GitHub callback returned error: {}", error);
      return frontendRedirect(workspaceId, "error", "access_denied");
    }
    if (code == null || code.isBlank()) {
      return frontendRedirect(workspaceId, "error", "authorization_required");
    }
    try {
      props.requireConfigured();
      User user = requireUser(pending.keycloakId());
      GithubApiClient.UserTokens tokens = githubApiClient.exchangeCode(code, callbackUri());
      GithubApiClient.GithubUser githubUser = githubApiClient.getUser(tokens.accessToken());
      GithubAccount account = userTokens.saveAuthorization(user, githubUser, tokens);
      log.info("User {} authorized GitHub as {}", user.getId(), githubUser.login());
      if (installationId == null) {
        return frontendRedirect(workspaceId, "authorized", null);
      }
      linkVerified(user, account, workspaceId, installationId, tokens.accessToken());
      return frontendRedirect(workspaceId, "linked", null);
    } catch (ConflictException e) {
      return frontendRedirect(workspaceId, "error", "conflict");
    } catch (UnauthorizedAccessException | WorkspaceNotFoundException e) {
      return frontendRedirect(workspaceId, "error", "forbidden");
    } catch (IllegalArgumentException e) {
      return frontendRedirect(workspaceId, "error", "wrong_account_type");
    } catch (GithubReauthorizationRequiredException e) {
      return frontendRedirect(workspaceId, "error", "authorization_failed");
    } catch (GithubIntegrationException | UserNotFoundException e) {
      log.error("GitHub callback failed", e);
      return frontendRedirect(workspaceId, "error", "server_error");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public GithubAccountResponse getAccount(UUID keycloakId) {
    return accountRepository
        .findByUserId(requireUser(keycloakId).getId())
        .filter(a -> a.getAccessTokenEnc() != null)
        .map(a -> new GithubAccountResponse(true, a.getLogin()))
        .orElse(new GithubAccountResponse(false, null));
  }

  @Override
  @Transactional(readOnly = true)
  public List<GithubInstallationResponse> listLinked(UUID keycloakId, UUID workspaceId) {
    workspaceService.requireRole(workspaceId, keycloakId, WorkspaceRole.MEMBER);
    return installationRepository.findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
        .map(installationMapper::toResponse)
        .toList();
  }

  @Override
  public List<GithubInstallationCandidateResponse> listCandidates(
      UUID keycloakId, UUID workspaceId) {
    Workspace workspace =
        workspaceService.requireRole(workspaceId, keycloakId, WorkspaceRole.ADMIN).getWorkspace();
    User user = requireUser(keycloakId);
    String token = userTokens.accessToken(user);
    GithubAccount account =
        accountRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> new GithubReauthorizationRequiredException("Connect GitHub"));
    return githubApiClient.listUserInstallations(token).stream()
        .filter(i -> fitsWorkspace(workspace, account, i))
        .map(
            i -> {
              UUID linkedTo =
                  installationRepository
                      .findByInstallationId(i.id())
                      .map(existing -> existing.getWorkspace().getId())
                      .orElse(null);
              boolean linkedHere = workspaceId.equals(linkedTo);
              return new GithubInstallationCandidateResponse(
                  i.id(),
                  i.accountLogin(),
                  i.accountType(),
                  i.repositorySelection(),
                  linkedHere,
                  !i.suspended() && (linkedTo == null || linkedHere));
            })
        .toList();
  }

  @Override
  public GithubInstallationResponse link(UUID keycloakId, GithubInstallationLinkDto dto) {
    User user = requireUser(keycloakId);
    String token = userTokens.accessToken(user);
    GithubAccount account =
        accountRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> new GithubReauthorizationRequiredException("Connect GitHub"));
    return installationMapper.toResponse(
        linkVerified(user, account, dto.workspaceId(), dto.installationId(), token));
  }

  @Override
  @Transactional
  public void unlink(UUID keycloakId, UUID id) {
    GithubInstallation installation =
        installationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("GitHub connection not found"));
    try {
      workspaceService.requireRole(
          installation.getWorkspace().getId(), keycloakId, WorkspaceRole.ADMIN);
    } catch (WorkspaceNotFoundException e) {
      throw new ResourceNotFoundException("GitHub connection not found");
    }
    installationRepository.delete(installation);
    log.info("Unlinked GitHub installation {}", installation.getInstallationId());
  }

  private GithubInstallation linkVerified(
      User user, GithubAccount account, UUID workspaceId, long installationId, String token) {
    Workspace workspace =
        workspaceService
            .requireRole(workspaceId, user.getKeycloakId(), WorkspaceRole.ADMIN)
            .getWorkspace();
    InstallationInfo info =
        githubApiClient.listUserInstallations(token).stream()
            .filter(i -> i.id() == installationId)
            .findFirst()
            .orElseThrow(
                () ->
                    new UnauthorizedAccessException(
                        "This GitHub installation is not accessible to your GitHub account"));
    if (info.suspended()) {
      throw new ConflictException("This GitHub installation is suspended");
    }
    if (workspace.getType() == WorkspaceType.PERSONAL) {
      if (info.accountType() != GithubAccountType.USER
          || info.accountId() != account.getGithubUserId()) {
        throw new IllegalArgumentException(
            "A personal workspace can only connect your own GitHub account");
      }
    } else {
      if (info.accountType() != GithubAccountType.ORGANIZATION) {
        throw new IllegalArgumentException(
            "An organization workspace must connect a GitHub organization");
      }
      boolean orgAdmin =
          githubApiClient
              .getOrgMembershipRole(token, info.accountLogin())
              .map("admin"::equals)
              .orElse(false);
      if (!orgAdmin) {
        throw new UnauthorizedAccessException(
            "You must be an owner of the GitHub organization " + info.accountLogin());
      }
    }

    GithubInstallation installation =
        installationRepository
            .findByInstallationId(installationId)
            .orElseGet(() -> GithubInstallation.builder().installationId(installationId).build());
    if (installation.getWorkspace() != null
        && !installation.getWorkspace().getId().equals(workspaceId)) {
      throw new ConflictException("This GitHub installation is linked to another workspace");
    }
    installation.setAccountId(info.accountId());
    installation.setAccountLogin(info.accountLogin());
    installation.setAccountType(info.accountType());
    installation.setRepositorySelection(info.repositorySelection());
    installation.setStatus(GithubInstallationStatus.ACTIVE);
    installation.setWorkspace(workspace);
    installation.setConnectedBy(user);
    GithubInstallation saved = installationRepository.save(installation);
    log.info(
        "Linked GitHub installation {} ({}) to workspace {}",
        installationId,
        info.accountLogin(),
        workspaceId);
    return saved;
  }

  private static boolean fitsWorkspace(
      Workspace workspace, GithubAccount account, InstallationInfo info) {
    return workspace.getType() == WorkspaceType.PERSONAL
        ? info.accountType() == GithubAccountType.USER
            && info.accountId() == account.getGithubUserId()
        : info.accountType() == GithubAccountType.ORGANIZATION;
  }

  private User requireUser(UUID keycloakId) {
    return userRepository
        .findByKeycloakId(keycloakId)
        .orElseThrow(
            () -> new UserNotFoundException("User not found with KeycloakId " + keycloakId));
  }

  private String callbackUri() {
    return backendPublicUrl + "/github/callback";
  }

  private String frontendRedirect(UUID workspaceId, String result, String reason) {
    StringBuilder url = new StringBuilder(frontendUrl);
    url.append(workspaceId == null ? "/workspaces" : "/workspaces/" + workspaceId);
    url.append("?github=").append(result);
    if (reason != null) {
      url.append("&reason=").append(URLEncoder.encode(reason, UTF_8));
    }
    return url.toString();
  }
}
