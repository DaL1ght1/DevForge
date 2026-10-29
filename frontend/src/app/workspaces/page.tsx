"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useState } from "react";
import { Building2, ChevronRight, Plus, UserRound } from "lucide-react";
import { Navbar } from "@/components/layout/Navbar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { useWorkspace } from "@/context/WorkspaceContext";
import { workspacesApi } from "@/lib/api/workspaces";
import { githubCallbackMessage } from "@/lib/githubCallback";

function WorkspacesContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const {
    workspaces,
    activeWorkspace,
    isLoading,
    refreshWorkspaces,
    setActiveWorkspaceId,
  } = useWorkspace();
  const [name, setName] = useState("");
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  // GitHub redirects here only when the request could not be tied to a workspace.
  const [callback] = useState(() =>
    githubCallbackMessage(
      searchParams.get("github"),
      searchParams.get("reason"),
    ),
  );

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setCreating(true);
    setError(null);
    try {
      const created = await workspacesApi.createOrganization(name.trim());
      await refreshWorkspaces();
      setActiveWorkspaceId(created.id);
      router.push(`/workspaces/${created.id}`);
    } catch (err) {
      setError(
        err instanceof Error ? err : new Error("Could not create organization"),
      );
      setCreating(false);
    }
  };

  return (
    <main className="flex-1 px-4 py-8 sm:px-6">
      <div className="mx-auto max-w-3xl space-y-6">
        <div className="pb-6 border-b border-zinc-800/80">
          <div className="flex items-center gap-2 text-xs font-mono text-zinc-400 mb-1">
            <span>Platform</span>
            <span>/</span>
            <span className="text-sky-400">Workspaces</span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">
            Workspaces
          </h1>
          <p className="text-xs text-zinc-400 mt-1 max-w-xl">
            Your personal workspace creates repositories in your own GitHub
            account. Organization workspaces create them in a connected GitHub
            organization.
          </p>
        </div>

        {callback && (
          <p
            role="status"
            className="rounded-md border border-amber-900/60 bg-amber-950/30 p-3 text-xs text-amber-200"
          >
            ⚠ {callback.text}
          </p>
        )}

        <Card className="border-zinc-800 bg-zinc-900/60">
          <CardContent className="p-0">
            {isLoading ? (
              <p className="p-5 text-xs text-zinc-500">Loading workspaces…</p>
            ) : (
              <ul className="divide-y divide-zinc-800/80">
                {workspaces.map((workspace) => (
                  <li key={workspace.id}>
                    <Link
                      href={`/workspaces/${workspace.id}`}
                      onClick={() => setActiveWorkspaceId(workspace.id)}
                      aria-current={
                        workspace.id === activeWorkspace?.id
                          ? "true"
                          : undefined
                      }
                      className="flex items-center justify-between gap-3 p-4 hover:bg-zinc-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-sky-500"
                    >
                      <span className="flex items-center gap-3 min-w-0">
                        {workspace.type === "PERSONAL" ? (
                          <UserRound className="h-4 w-4 text-zinc-500 shrink-0" />
                        ) : (
                          <Building2 className="h-4 w-4 text-zinc-500 shrink-0" />
                        )}
                        <span className="min-w-0">
                          <span className="block text-sm font-medium text-zinc-100 truncate">
                            {workspace.name}
                          </span>
                          <span className="block text-[11px] text-zinc-500">
                            {workspace.type === "PERSONAL"
                              ? "Personal workspace"
                              : "Organization workspace"}
                          </span>
                        </span>
                      </span>
                      <span className="flex items-center gap-2 shrink-0">
                        {workspace.id === activeWorkspace?.id && (
                          <Badge variant="info" size="sm">
                            CURRENT
                          </Badge>
                        )}
                        <Badge variant="outline" size="sm">
                          {workspace.role}
                        </Badge>
                        <ChevronRight className="h-4 w-4 text-zinc-600" />
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card className="border-zinc-800 bg-zinc-900/60">
          <CardHeader className="pb-3 border-b border-zinc-800/80">
            <CardTitle className="text-sm text-zinc-100 flex items-center gap-2">
              <Building2 className="h-4 w-4 text-sky-400" />
              Create an organization workspace
            </CardTitle>
            <CardDescription>
              You become its owner. Connect a GitHub organization afterwards.
            </CardDescription>
          </CardHeader>
          <CardContent className="p-5">
            <form onSubmit={handleCreate} className="space-y-4">
              <ErrorAlert error={error} />
              <Input
                id="organization-name"
                name="name"
                label="Organization name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                minLength={2}
                maxLength={100}
                autoComplete="organization"
                required
              />
              <Button
                type="submit"
                variant="primary"
                size="sm"
                isLoading={creating}
              >
                <Plus className="h-3.5 w-3.5" />
                Create organization
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </main>
  );
}

export default function WorkspacesPage() {
  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
      <Navbar />
      <Suspense fallback={null}>
        <WorkspacesContent />
      </Suspense>
    </div>
  );
}
