"use client";

import { useCallback, useEffect, useState } from "react";
import {
  CheckCircle2,
  ExternalLink,
  GitBranch,
  Link2,
  RefreshCw,
  Unlink,
} from "lucide-react";
import {
  GithubAccountResponse,
  GithubInstallationCandidateResponse,
  GithubInstallationResponse,
  GithubInstallationStatus,
  WorkspaceResponse,
} from "@/types/api";
import { GITHUB_REAUTH_REQUIRED, githubApi } from "@/lib/api/github";
import { ApiClientError } from "@/lib/api/client";
import { hasRole } from "@/lib/workspaceRoles";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/Card";
import { Dialog } from "@/components/ui/Dialog";
import { ErrorAlert } from "@/components/common/ErrorAlert";

const STATUS_BADGE: Record<
  GithubInstallationStatus,
  { label: string; variant: "success" | "warning" | "danger" }
> = {
  ACTIVE: { label: "ACTIVE", variant: "success" },
  SUSPENDED: { label: "SUSPENDED", variant: "warning" },
  REMOVED: { label: "UNINSTALLED", variant: "danger" },
};

interface GithubConnectionCardProps {
  workspace: WorkspaceResponse;
  /** Message describing the result of a GitHub redirect, shown once. */
  callbackMessage?: { tone: "success" | "error"; text: string } | null;
}

export function GithubConnectionCard({
  workspace,
  callbackMessage,
}: GithubConnectionCardProps) {
  const isAdmin = hasRole(workspace, "ADMIN");
  const isPersonal = workspace.type === "PERSONAL";
  const [linked, setLinked] = useState<GithubInstallationResponse[]>([]);
  const [account, setAccount] = useState<GithubAccountResponse | null>(null);
  const [candidates, setCandidates] = useState<
    GithubInstallationCandidateResponse[] | null
  >(null);
  const [needsReauth, setNeedsReauth] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [unlinkTarget, setUnlinkTarget] =
    useState<GithubInstallationResponse | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [linkedList, githubAccount] = await Promise.all([
        githubApi.listLinked(workspace.id),
        githubApi.account(),
      ]);
      setLinked(linkedList);
      setAccount(githubAccount);
      setNeedsReauth(false);
      if (isAdmin && githubAccount.connected) {
        try {
          setCandidates(await githubApi.listCandidates(workspace.id));
        } catch (err) {
          if (
            err instanceof ApiClientError &&
            err.error === GITHUB_REAUTH_REQUIRED
          ) {
            setNeedsReauth(true);
            setCandidates(null);
          } else {
            throw err;
          }
        }
      } else {
        setCandidates(null);
      }
    } catch (err) {
      setError(
        err instanceof Error ? err : new Error("Failed to load GitHub status"),
      );
    } finally {
      setLoading(false);
    }
  }, [workspace.id, isAdmin]);

  useEffect(() => {
    queueMicrotask(() => void load());
  }, [load]);

  // Both flows leave DevForge for GitHub and come back through the backend callback.
  const redirectTo = async (
    kind: "install" | "authorize",
    fetchUrl: () => Promise<{ url: string }>,
  ) => {
    setBusy(kind);
    setError(null);
    try {
      const { url } = await fetchUrl();
      window.location.assign(url);
    } catch (err) {
      setError(
        err instanceof Error ? err : new Error("Could not reach GitHub"),
      );
      setBusy(null);
    }
  };

  const connect = async (candidate: GithubInstallationCandidateResponse) => {
    setBusy(`link-${candidate.installationId}`);
    setError(null);
    setStatus(null);
    try {
      await githubApi.link(workspace.id, candidate.installationId);
      setStatus(`Connected github.com/${candidate.accountLogin}.`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error("Connection failed"));
    } finally {
      setBusy(null);
    }
  };

  const unlink = async () => {
    if (!unlinkTarget) return;
    setBusy(`unlink-${unlinkTarget.id}`);
    setError(null);
    setStatus(null);
    try {
      await githubApi.unlink(unlinkTarget.id);
      setStatus(`Disconnected github.com/${unlinkTarget.accountLogin}.`);
      setUnlinkTarget(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error("Disconnect failed"));
    } finally {
      setBusy(null);
    }
  };

  const active = linked.find((i) => i.status === "ACTIVE");
  const connectable = (candidates ?? []).filter(
    (c) => c.available && !c.linked,
  );

  return (
    <Card className="border-zinc-800 bg-zinc-900/60">
      <CardHeader className="pb-3 border-b border-zinc-800/80">
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="text-sm text-zinc-100 flex items-center gap-2">
              <GitBranch className="h-4 w-4 text-sky-400" />
              GitHub destination
            </CardTitle>
            <CardDescription>
              {isPersonal
                ? "Services in this workspace are created in your own GitHub account."
                : "Services in this workspace are created in the connected GitHub organization."}
            </CardDescription>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void load()}
            disabled={loading}
            aria-label="Refresh GitHub status"
          >
            <RefreshCw
              className={loading ? "h-3.5 w-3.5 animate-spin" : "h-3.5 w-3.5"}
            />
          </Button>
        </div>
      </CardHeader>
      <CardContent className="p-5 space-y-4 text-xs">
        <div role="status" aria-live="polite" className="space-y-2">
          {callbackMessage && (
            <p
              className={
                callbackMessage.tone === "success"
                  ? "rounded-md border border-emerald-900/60 bg-emerald-950/30 p-3 text-emerald-200"
                  : "rounded-md border border-amber-900/60 bg-amber-950/30 p-3 text-amber-200"
              }
            >
              {callbackMessage.tone === "success" ? "✓ " : "⚠ "}
              {callbackMessage.text}
            </p>
          )}
          {status && (
            <p className="rounded-md border border-emerald-900/60 bg-emerald-950/30 p-3 text-emerald-200">
              ✓ {status}
            </p>
          )}
        </div>
        <ErrorAlert error={error} />

        {active ? (
          <div className="flex items-center gap-2 rounded-lg border border-emerald-900/50 bg-emerald-950/20 p-3 text-emerald-100">
            <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
            <span>
              New repositories will be created in{" "}
              <a
                href={`https://github.com/${active.accountLogin}`}
                target="_blank"
                rel="noreferrer"
                className="font-mono font-semibold text-emerald-300 underline-offset-2 hover:underline"
              >
                github.com/{active.accountLogin}
              </a>
              .
            </span>
          </div>
        ) : (
          !loading && (
            <p className="rounded-lg border border-amber-900/50 bg-amber-950/20 p-3 text-amber-200">
              ⚠ No active GitHub connection. Services cannot be created in this
              workspace until one is connected
              {isAdmin ? "." : " by a workspace owner or admin."}
            </p>
          )
        )}

        {linked.length > 0 && (
          <section
            aria-labelledby={`linked-${workspace.id}`}
            className="space-y-2"
          >
            <h4
              id={`linked-${workspace.id}`}
              className="text-[10px] font-mono uppercase tracking-wider text-zinc-500"
            >
              Connected installations
            </h4>
            <ul className="space-y-2">
              {linked.map((installation) => {
                const badge = STATUS_BADGE[installation.status];
                return (
                  <li
                    key={installation.id}
                    className="flex items-center justify-between gap-3 rounded-lg border border-zinc-800 bg-zinc-950/60 p-3"
                  >
                    <div className="min-w-0">
                      <div className="font-mono text-zinc-100 truncate">
                        {installation.accountLogin}
                      </div>
                      <div className="text-[11px] text-zinc-500">
                        {installation.accountType === "ORGANIZATION"
                          ? "Organization"
                          : "Personal account"}
                        {installation.repositorySelection &&
                          ` · ${installation.repositorySelection === "all" ? "all repositories" : "selected repositories"}`}
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <Badge variant={badge.variant} size="sm">
                        {badge.label}
                      </Badge>
                      {isAdmin && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setUnlinkTarget(installation)}
                          aria-label={`Disconnect ${installation.accountLogin}`}
                        >
                          <Unlink className="h-3.5 w-3.5" />
                        </Button>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        )}

        {isAdmin && connectable.length > 0 && (
          <section
            aria-labelledby={`candidates-${workspace.id}`}
            className="space-y-2"
          >
            <h4
              id={`candidates-${workspace.id}`}
              className="text-[10px] font-mono uppercase tracking-wider text-zinc-500"
            >
              Available on your GitHub account
            </h4>
            <ul className="space-y-2">
              {connectable.map((candidate) => (
                <li
                  key={candidate.installationId}
                  className="flex items-center justify-between gap-3 rounded-lg border border-zinc-800 bg-zinc-950/60 p-3"
                >
                  <span className="font-mono text-zinc-100 truncate">
                    {candidate.accountLogin}
                  </span>
                  <Button
                    variant="secondary"
                    size="sm"
                    isLoading={busy === `link-${candidate.installationId}`}
                    onClick={() => void connect(candidate)}
                  >
                    <Link2 className="h-3.5 w-3.5" />
                    Connect
                  </Button>
                </li>
              ))}
            </ul>
          </section>
        )}

        {isAdmin && (
          <div className="space-y-2 border-t border-zinc-800/80 pt-4">
            {needsReauth && (
              <p className="text-amber-200">
                ⚠ Your GitHub authorization expired. Authorize again to manage
                installations.
              </p>
            )}
            {account?.connected && !needsReauth && (
              <p className="text-zinc-400">
                Authorized on GitHub as{" "}
                <span className="font-mono text-zinc-200">{account.login}</span>
                .
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              <Button
                variant="primary"
                size="sm"
                isLoading={busy === "install"}
                disabled={busy !== null}
                onClick={() =>
                  void redirectTo("install", () =>
                    githubApi.installUrl(workspace.id),
                  )
                }
              >
                <ExternalLink className="h-3.5 w-3.5" />
                {isPersonal
                  ? "Install on my GitHub account"
                  : "Install on a GitHub organization"}
              </Button>
              <Button
                variant="outline"
                size="sm"
                isLoading={busy === "authorize"}
                disabled={busy !== null}
                onClick={() =>
                  void redirectTo("authorize", () =>
                    githubApi.authorizeUrl(workspace.id),
                  )
                }
              >
                {account?.connected && !needsReauth
                  ? "Re-authorize GitHub"
                  : "Already installed? Authorize GitHub"}
              </Button>
            </div>
            <p className="text-[11px] text-zinc-500">
              {isPersonal
                ? "Choose your personal account when GitHub asks where to install."
                : "You must be an owner of the GitHub organization you install on."}
            </p>
          </div>
        )}
      </CardContent>

      <Dialog
        isOpen={unlinkTarget !== null}
        onClose={() => setUnlinkTarget(null)}
        title="Disconnect GitHub"
        description="DevForge stops creating repositories here. Existing repositories are not touched."
      >
        <div className="space-y-4 text-xs text-zinc-300">
          <p>
            Disconnect{" "}
            <strong className="font-mono text-zinc-100">
              {unlinkTarget?.accountLogin}
            </strong>{" "}
            from this workspace? The GitHub App stays installed on GitHub; you
            can uninstall it there.
          </p>
          <div className="flex justify-end gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setUnlinkTarget(null)}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              size="sm"
              isLoading={busy?.startsWith("unlink-") ?? false}
              onClick={() => void unlink()}
            >
              Disconnect
            </Button>
          </div>
        </div>
      </Dialog>
    </Card>
  );
}
