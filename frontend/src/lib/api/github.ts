import { api } from "./client";
import {
  GithubAccountResponse,
  GithubInstallationCandidateResponse,
  GithubInstallationResponse,
  GithubRedirectResponse,
} from "@/types/api";

/** Error titles the backend uses for GitHub states the UI must react to. */
export const GITHUB_NOT_CONNECTED = "GitHub Not Connected";
export const GITHUB_REAUTH_REQUIRED = "GitHub Reauthorization Required";

const query = (workspaceId: string) =>
  `workspaceId=${encodeURIComponent(workspaceId)}`;

export const githubApi = {
  account(): Promise<GithubAccountResponse> {
    return api.get<GithubAccountResponse>("/github/account");
  },

  /** URL that installs the DevForge GitHub App for this workspace. */
  installUrl(workspaceId: string): Promise<GithubRedirectResponse> {
    return api.get<GithubRedirectResponse>(
      `/github/install?${query(workspaceId)}`,
    );
  },

  /** URL that authorizes DevForge on GitHub without changing installations. */
  authorizeUrl(workspaceId: string): Promise<GithubRedirectResponse> {
    return api.get<GithubRedirectResponse>(
      `/github/authorize?${query(workspaceId)}`,
    );
  },

  listLinked(workspaceId: string): Promise<GithubInstallationResponse[]> {
    return api.get<GithubInstallationResponse[]>(
      `/github/installations?${query(workspaceId)}`,
    );
  },

  listCandidates(
    workspaceId: string,
  ): Promise<GithubInstallationCandidateResponse[]> {
    return api.get<GithubInstallationCandidateResponse[]>(
      `/github/installations/available?${query(workspaceId)}`,
    );
  },

  link(
    workspaceId: string,
    installationId: number,
  ): Promise<GithubInstallationResponse> {
    return api.post<GithubInstallationResponse>("/github/installations", {
      workspaceId,
      installationId,
    });
  },

  unlink(id: string): Promise<void> {
    return api.delete<void>(`/github/installations/${id}`);
  },
};
