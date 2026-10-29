"use client";

import Link from "next/link";
import {
  useParams,
  usePathname,
  useRouter,
  useSearchParams,
} from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { ArrowLeft, Building2, UserRound } from "lucide-react";
import { Navbar } from "@/components/layout/Navbar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { GithubConnectionCard } from "@/components/workspace/GithubConnectionCard";
import { MembersCard } from "@/components/workspace/MembersCard";
import { useWorkspace } from "@/context/WorkspaceContext";
import { workspacesApi } from "@/lib/api/workspaces";
import { githubCallbackMessage } from "@/lib/githubCallback";
import { WorkspaceResponse } from "@/types/api";

function WorkspaceDetail() {
  const params = useParams<{ id: string }>();
  const workspaceId = params?.id as string;
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { setActiveWorkspaceId, refreshWorkspaces } = useWorkspace();
  const [workspace, setWorkspace] = useState<WorkspaceResponse | null>(null);
  const [error, setError] = useState<Error | null>(null);
  // Read the GitHub redirect result once, then drop it from the URL so reloads don't repeat it.
  const [callback] = useState(() =>
    githubCallbackMessage(
      searchParams.get("github"),
      searchParams.get("reason"),
    ),
  );

  useEffect(() => {
    if (searchParams.has("github")) router.replace(pathname);
  }, [searchParams, router, pathname]);

  useEffect(() => {
    if (!workspaceId) return;
    let cancelled = false;
    workspacesApi
      .getById(workspaceId)
      .then((ws) => {
        if (cancelled) return;
        setWorkspace(ws);
        setActiveWorkspaceId(ws.id);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(
            err instanceof Error ? err : new Error("Workspace not found"),
          );
        }
      });
    return () => {
      cancelled = true;
    };
  }, [workspaceId, setActiveWorkspaceId]);

  const handleLeft = async () => {
    const remaining = await refreshWorkspaces();
    const personal = remaining.find((w) => w.type === "PERSONAL");
    if (personal) setActiveWorkspaceId(personal.id);
    router.push("/workspaces");
  };

  if (error) {
    return (
      <main className="flex-1 px-4 py-8 sm:px-6">
        <div className="mx-auto max-w-3xl space-y-4">
          <ErrorAlert error={error} />
          <Link href="/workspaces">
            <Button variant="outline" size="sm">
              <ArrowLeft className="h-3.5 w-3.5" /> All workspaces
            </Button>
          </Link>
        </div>
      </main>
    );
  }

  if (!workspace) {
    return (
      <main className="flex-1 px-4 py-8 sm:px-6">
        <p className="mx-auto max-w-3xl text-xs text-zinc-500">
          Loading workspace…
        </p>
      </main>
    );
  }

  return (
    <main className="flex-1 px-4 py-8 sm:px-6">
      <div className="mx-auto max-w-3xl space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-6 border-b border-zinc-800/80">
          <div>
            <div className="flex items-center gap-2 text-xs font-mono text-zinc-400 mb-1">
              <Link
                href="/workspaces"
                className="hover:text-zinc-200 transition-colors"
              >
                Workspaces
              </Link>
              <span>/</span>
              <span className="text-sky-400">{workspace.slug}</span>
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-zinc-100 flex items-center gap-2">
              {workspace.type === "PERSONAL" ? (
                <UserRound className="h-5 w-5 text-zinc-500" />
              ) : (
                <Building2 className="h-5 w-5 text-zinc-500" />
              )}
              {workspace.name}
            </h1>
            <div className="mt-2 flex items-center gap-2">
              <Badge variant="outline" size="sm">
                {workspace.type === "PERSONAL" ? "PERSONAL" : "ORGANIZATION"}
              </Badge>
              <Badge variant="info" size="sm">
                YOU: {workspace.role}
              </Badge>
            </div>
          </div>
          <Link href="/services">
            <Button variant="outline" size="sm">
              View services
            </Button>
          </Link>
        </div>

        <GithubConnectionCard
          workspace={workspace}
          callbackMessage={callback}
        />
        <MembersCard workspace={workspace} onLeft={() => void handleLeft()} />
      </div>
    </main>
  );
}

export default function WorkspacePage() {
  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
      <Navbar />
      <Suspense fallback={null}>
        <WorkspaceDetail />
      </Suspense>
    </div>
  );
}
