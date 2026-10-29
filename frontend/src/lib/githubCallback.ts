/**
 * Turns the `?github=…&reason=…` parameters the backend appends after a GitHub redirect into a
 * message. Reasons are fixed codes from GithubConnectionServiceImplementation.
 */
export function githubCallbackMessage(
  result: string | null,
  reason: string | null,
): { tone: "success" | "error"; text: string } | null {
  switch (result) {
    case "linked":
      return {
        tone: "success",
        text: "GitHub connected. New services in this workspace will be created there.",
      };
    case "authorized":
      return {
        tone: "success",
        text: "GitHub authorized. Choose an installation below, or install the App.",
      };
    case "error":
      return { tone: "error", text: errorText(reason) };
    default:
      return null;
  }
}

function errorText(reason: string | null): string {
  switch (reason) {
    case "invalid_state":
      return "The GitHub connection request expired or was already used. Please start again.";
    case "access_denied":
      return "GitHub authorization was cancelled.";
    case "authorization_required":
      return "The App was installed, but DevForge still needs your authorization to verify it. Use “Authorize GitHub”.";
    case "conflict":
      return "That GitHub account or installation is already connected to another DevForge user or workspace.";
    case "forbidden":
      return "Not allowed: you must be an admin of this workspace and, for organizations, an owner of the GitHub organization.";
    case "wrong_account_type":
      return "That installation does not fit this workspace. Personal workspaces connect your own GitHub account; organization workspaces connect a GitHub organization.";
    case "authorization_failed":
      return "GitHub rejected the authorization. Please try again.";
    default:
      return "Something went wrong while talking to GitHub. Please try again.";
  }
}
