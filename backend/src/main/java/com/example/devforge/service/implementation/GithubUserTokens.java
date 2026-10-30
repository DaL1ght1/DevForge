package com.example.devforge.service.implementation;

import com.example.devforge.config.github.GithubApiClient;
import com.example.devforge.entity.GithubAccount;
import com.example.devforge.entity.User;
import com.example.devforge.exception.ConflictException;
import com.example.devforge.exception.GithubReauthorizationRequiredException;
import com.example.devforge.repository.GithubAccountRepository;
import com.example.devforge.security.TokenCipher;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GithubUserTokens {

  private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(2);

  private final GithubAccountRepository accountRepository;
  private final GithubApiClient githubApiClient;
  private final TokenCipher cipher;

  @Transactional
  public GithubAccount saveAuthorization(
      User user, GithubApiClient.GithubUser githubUser, GithubApiClient.UserTokens tokens) {
    accountRepository
        .findByGithubUserId(githubUser.id())
        .filter(other -> !other.getUser().getId().equals(user.getId()))
        .ifPresent(
            _ -> {
              throw new ConflictException(
                  "This GitHub account is already connected to another DevForge user");
            });
    GithubAccount account =
        accountRepository
            .findByUserIdForUpdate(user.getId())
            .orElseGet(() -> GithubAccount.builder().user(user).build());
    account.setGithubUserId(githubUser.id());
    account.setLogin(githubUser.login());
    apply(account, tokens);
    return accountRepository.save(account);
  }

  /**
   * Returns a usable access token for the user, refreshing (and rotating) it if needed. Dead tokens
   * are cleared even though the method then throws, hence {@code noRollbackFor}.
   */
  @Transactional(noRollbackFor = GithubReauthorizationRequiredException.class)
  public String accessToken(User user) {
    GithubAccount account =
        accountRepository
            .findByUserIdForUpdate(user.getId())
            .orElseThrow(
                () -> new GithubReauthorizationRequiredException("Connect your GitHub account"));
    Instant now = Instant.now();
    if (account.getAccessTokenEnc() != null
        && (account.getAccessTokenExpiresAt() == null
            || account.getAccessTokenExpiresAt().isAfter(now.plus(EXPIRY_MARGIN)))) {
      return cipher.decrypt(account.getAccessTokenEnc());
    }
    if (account.getRefreshTokenEnc() == null
        || (account.getRefreshTokenExpiresAt() != null
            && account.getRefreshTokenExpiresAt().isBefore(now))) {
      account.clearTokens();
      throw new GithubReauthorizationRequiredException(
          "GitHub authorization expired; please reconnect GitHub");
    }
    GithubApiClient.UserTokens refreshed;
    try {
      refreshed = githubApiClient.refresh(cipher.decrypt(account.getRefreshTokenEnc()));
    } catch (GithubReauthorizationRequiredException e) {
      account.clearTokens();
      throw e;
    }
    apply(account, refreshed);
    log.info("Refreshed GitHub user token for user {}", user.getId());
    return refreshed.accessToken();
  }

  /** Drops stored tokens after GitHub reports the user revoked the App authorization. */
  @Transactional
  public void revoke(long githubUserId) {
    accountRepository.findByGithubUserId(githubUserId).ifPresent(GithubAccount::clearTokens);
  }

  private void apply(GithubAccount account, GithubApiClient.UserTokens tokens) {
    account.setAccessTokenEnc(cipher.encrypt(tokens.accessToken()));
    account.setAccessTokenExpiresAt(tokens.accessTokenExpiresAt());
    account.setRefreshTokenEnc(
        tokens.refreshToken() == null ? null : cipher.encrypt(tokens.refreshToken()));
    account.setRefreshTokenExpiresAt(tokens.refreshTokenExpiresAt());
  }
}
