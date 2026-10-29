package com.example.devforge.config.github;

import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.exception.GithubIntegrationException;
import com.example.devforge.exception.GithubReauthorizationRequiredException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class GithubApiClient {

  private static final int PAGE_SIZE = 100;
  private static final int MAX_PAGES = 10;

  private final GithubAppProperties props;
  private final RestClient api;
  private final RestClient web;

  @Autowired
  public GithubApiClient(GithubAppProperties props) {
    this(props, RestClient.builder(), RestClient.builder());
  }

  GithubApiClient(
      GithubAppProperties props, RestClient.Builder apiBuilder, RestClient.Builder webBuilder) {
    this.props = props;
    this.api =
        apiBuilder
            .baseUrl(props.apiUrl())
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    this.web =
        webBuilder
            .baseUrl(props.webUrl())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  public record UserTokens(
      String accessToken,
      Instant accessTokenExpiresAt,
      String refreshToken,
      Instant refreshTokenExpiresAt) {}

  public record GithubUser(long id, String login) {}

  public record InstallationInfo(
      long id,
      long accountId,
      String accountLogin,
      GithubAccountType accountType,
      String repositorySelection,
      boolean suspended) {}

  public UserTokens exchangeCode(String code, String redirectUri) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", props.clientId());
    form.add("client_secret", props.clientSecret());
    form.add("code", code);
    form.add("redirect_uri", redirectUri);
    return requestTokens(form, false);
  }

  public UserTokens refresh(String refreshToken) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", props.clientId());
    form.add("client_secret", props.clientSecret());
    form.add("grant_type", "refresh_token");
    form.add("refresh_token", refreshToken);
    return requestTokens(form, true);
  }

  public GithubUser getUser(String accessToken) {
    UserResponse user =
        call(
            () ->
                api.get()
                    .uri("/user")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .body(UserResponse.class));
    if (user == null) {
      throw new GithubIntegrationException("GitHub returned no user");
    }
    return new GithubUser(user.id(), user.login());
  }

  public List<InstallationInfo> listUserInstallations(String accessToken) {
    List<InstallationInfo> result = new ArrayList<>();
    for (int page = 1; page <= MAX_PAGES; page++) {
      int current = page;
      InstallationsResponse body =
          call(
              () ->
                  api.get()
                      .uri("/user/installations?per_page={size}&page={page}", PAGE_SIZE, current)
                      .headers(h -> h.setBearerAuth(accessToken))
                      .retrieve()
                      .body(InstallationsResponse.class));
      if (body == null || body.installations() == null) {
        break;
      }
      for (InstallationResponse i : body.installations()) {
        if (i.account() == null) {
          continue;
        }
        result.add(
            new InstallationInfo(
                i.id(),
                i.account().id(),
                i.account().login(),
                GithubAccountType.fromGithub(i.account().type()),
                i.repositorySelection(),
                i.suspendedAt() != null));
      }
      if (body.installations().size() < PAGE_SIZE) {
        break;
      }
    }
    return result;
  }

  public Optional<String> getOrgMembershipRole(String accessToken, String org) {
    try {
      MembershipResponse membership =
          api.get()
              .uri("/user/memberships/orgs/{org}", org)
              .headers(h -> h.setBearerAuth(accessToken))
              .retrieve()
              .body(MembershipResponse.class);
      if (membership == null || !"active".equals(membership.state())) {
        return Optional.empty();
      }
      return Optional.ofNullable(membership.role());
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.FORBIDDEN) {
        return Optional.empty();
      }
      throw mapError(e);
    } catch (RestClientException e) {
      throw new GithubIntegrationException("GitHub request failed", e);
    }
  }

  private UserTokens requestTokens(MultiValueMap<String, String> form, boolean refreshing) {
    TokenResponse res;
    try {
      res =
          web.post()
              .uri("/login/oauth/access_token")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(TokenResponse.class);
    } catch (RestClientException e) {
      throw new GithubIntegrationException("GitHub token request failed", e);
    }
    if (res == null || res.error() != null || res.accessToken() == null) {
      String error = res == null ? "empty_response" : res.error();
      log.warn("GitHub token request rejected: {}", error);
      if (refreshing || "bad_verification_code".equals(error)) {
        throw new GithubReauthorizationRequiredException(
            "GitHub authorization expired or was revoked; please reconnect GitHub");
      }
      throw new GithubIntegrationException("GitHub token request rejected: " + error);
    }
    Instant now = Instant.now();
    return new UserTokens(
        res.accessToken(),
        res.expiresIn() == null ? null : now.plusSeconds(res.expiresIn()),
        res.refreshToken(),
        res.refreshTokenExpiresIn() == null ? null : now.plusSeconds(res.refreshTokenExpiresIn()));
  }

  private static <T> T call(Supplier<T> request) {
    try {
      return request.get();
    } catch (HttpClientErrorException e) {
      throw mapError(e);
    } catch (RestClientException e) {
      throw new GithubIntegrationException("GitHub request failed", e);
    }
  }

  private static RuntimeException mapError(HttpClientErrorException e) {
    if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
      return new GithubReauthorizationRequiredException(
          "GitHub authorization is no longer valid; please reconnect GitHub");
    }
    return new GithubIntegrationException("GitHub request failed: " + e.getStatusCode(), e);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("refresh_token_expires_in") Long refreshTokenExpiresIn,
      @JsonProperty("error") String error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record UserResponse(long id, String login) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record AccountResponse(long id, String login, String type) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record InstallationResponse(
      long id,
      AccountResponse account,
      @JsonProperty("repository_selection") String repositorySelection,
      @JsonProperty("suspended_at") String suspendedAt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record InstallationsResponse(
      @JsonProperty("total_count") int totalCount, List<InstallationResponse> installations) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record MembershipResponse(String state, String role) {}
}
