"use client";

import Link from "next/link";
import { GitBranch } from "lucide-react";
import { WorkspaceResponse } from "@/types/api";
import { hasRole } from "@/lib/workspaceRoles";

interface DestinationNoticeProps {
  workspace: WorkspaceResponse | null;
  /** GitHub login of the workspace's active installation; null when none is connected. */
  githubLogin: string | null;
  loading: boolean;
  serviceName?: string;
}

/** Tells the user exactly which GitHub account will own the repository before they create it. */
export function DestinationNotice({
  workspace,
  githubLogin,
  loading,
  serviceName,
}: DestinationNoticeProps) {
  if (!workspace) return null;
  const settingsHref = `/workspaces/${workspace.id}`;

  if (loading) {
    return (
      <p className="rounded-lg border border-zinc-800 bg-zinc-900/60 p-3 text-xs text-zinc-500">
        Checking the GitHub destination for {workspace.name}…
      </p>
    );
  }

  if (!hasRole(workspace, "ADMIN")) {
    return (
      <p
        role="status"
        className="rounded-lg border border-amber-900/50 bg-amber-950/20 p-3 text-xs text-amber-200"
      >
        ⚠ You are a member of <strong>{workspace.name}</strong>. Only owners and
        admins can create services here. Switch workspace to create one in your
        personal account.
      </p>
    );
  }

  if (!githubLogin) {
    return (
      <p
        role="status"
        className="rounded-lg border border-amber-900/50 bg-amber-950/20 p-3 text-xs text-amber-200"
      >
        ⚠ <strong>{workspace.name}</strong> has no GitHub connection yet.{" "}
        <Link
          href={settingsHref}
          className="font-semibold text-amber-100 underline underline-offset-2"
        >
          Connect GitHub
        </Link>{" "}
        before creating a service.
      </p>
    );
  }

  return (
    <p className="flex items-center gap-2 rounded-lg border border-sky-900/50 bg-sky-950/20 p-3 text-xs text-sky-100">
      <GitBranch className="h-4 w-4 text-sky-400 shrink-0" />
      <span>
        The repository will be created in{" "}
        <span className="font-mono font-semibold text-sky-300">
          github.com/{githubLogin}/{serviceName || "<service-name>"}
        </span>{" "}
        (
        {workspace.type === "PERSONAL"
          ? "your personal account"
          : "organization"}{" "}
        workspace <strong>{workspace.name}</strong>).{" "}
        <Link
          href={settingsHref}
          className="text-sky-300 underline underline-offset-2"
        >
          Change
        </Link>
      </span>
    </p>
  );
}
