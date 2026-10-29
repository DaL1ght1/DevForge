package com.example.devforge.config.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.devforge.entity.GithubAccountType;
import com.example.devforge.exception.GithubIntegrationException;
import com.example.devforge.exception.GithubReauthorizationRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubApiClientTest {

  private MockRestServiceServer apiServer;
  private MockRestServiceServer webServer;
  private GithubApiClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder api = RestClient.builder();
    RestClient.Builder web = RestClient.builder();
    apiServer = MockRestServiceServer.bindTo(api).build();
    webServer = MockRestServiceServer.bindTo(web).build();
    client =
        new GithubApiClient(
            new GithubAppProperties(
                "devforge", "client", "secret", null, null, "https://api.test", "https://web.test"),
            api,
            web);
  }

  @Test
  void exchangeCodeReturnsTokensWithExpiry() {
    webServer
        .expect(requestTo("https://web.test/login/oauth/access_token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                {"access_token":"ghu_a","expires_in":28800,
                 "refresh_token":"ghr_r","refresh_token_expires_in":15897600}
                """,
                MediaType.APPLICATION_JSON));

    GithubApiClient.UserTokens tokens = client.exchangeCode("code", "https://cb");

    assertThat(tokens.accessToken()).isEqualTo("ghu_a");
    assertThat(tokens.refreshToken()).isEqualTo("ghr_r");
    assertThat(tokens.accessTokenExpiresAt()).isNotNull();
  }

  @Test
  void oauthErrorReportedWithHttp200IsAnError() {
    webServer
        .expect(requestTo("https://web.test/login/oauth/access_token"))
        .andRespond(
            withSuccess(
                "{\"error\":\"incorrect_client_credentials\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.exchangeCode("code", "https://cb"))
        .isInstanceOf(GithubIntegrationException.class);
  }

  @Test
  void rejectedRefreshRequiresReauthorization() {
    webServer
        .expect(requestTo("https://web.test/login/oauth/access_token"))
        .andRespond(withSuccess("{\"error\":\"bad_refresh_token\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.refresh("ghr_old"))
        .isInstanceOf(GithubReauthorizationRequiredException.class);
  }

  @Test
  void listsInstallationsWithTrustedAccountMetadata() {
    apiServer
        .expect(requestTo("https://api.test/user/installations?per_page=100&page=1"))
        .andExpect(header("Authorization", "Bearer ghu_a"))
        .andRespond(
            withSuccess(
                """
                {"total_count":2,"installations":[
                  {"id":1,"account":{"id":10,"login":"alice","type":"User"},
                   "repository_selection":"all"},
                  {"id":2,"account":{"id":20,"login":"acme","type":"Organization"},
                   "repository_selection":"selected","suspended_at":"2026-01-01T00:00:00Z"}]}
                """,
                MediaType.APPLICATION_JSON));

    var installations = client.listUserInstallations("ghu_a");

    assertThat(installations)
        .containsExactly(
            new GithubApiClient.InstallationInfo(
                1, 10, "alice", GithubAccountType.USER, "all", false),
            new GithubApiClient.InstallationInfo(
                2, 20, "acme", GithubAccountType.ORGANIZATION, "selected", true));
  }

  @Test
  void unauthorizedTokenRequiresReauthorization() {
    apiServer
        .expect(requestTo("https://api.test/user"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> client.getUser("ghu_dead"))
        .isInstanceOf(GithubReauthorizationRequiredException.class);
  }

  @Test
  void nonMemberHasNoOrgRole() {
    apiServer
        .expect(requestTo("https://api.test/user/memberships/orgs/acme"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(client.getOrgMembershipRole("ghu_a", "acme")).isEmpty();
  }

  @Test
  void pendingMembershipIsNotAdmin() {
    apiServer
        .expect(requestTo("https://api.test/user/memberships/orgs/acme"))
        .andRespond(
            withSuccess("{\"state\":\"pending\",\"role\":\"admin\"}", MediaType.APPLICATION_JSON));

    assertThat(client.getOrgMembershipRole("ghu_a", "acme")).isEmpty();
  }
}
