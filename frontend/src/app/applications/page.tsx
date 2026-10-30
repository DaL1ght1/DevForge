"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Boxes, ExternalLink, Plus } from "lucide-react";
import { Navbar } from "@/components/layout/Navbar";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardContent } from "@/components/ui/Card";
import { useAuth } from "@/context/AuthContext";
import { useWorkspace } from "@/context/WorkspaceContext";
import { applicationsApi } from "@/lib/api/applications";
import { statusBadge } from "@/lib/applicationStatus";
import type { ApplicationResponse } from "@/types/api";

export default function ApplicationsPage() {
  const { user, isLoading: authLoading } = useAuth();
  const { activeWorkspace } = useWorkspace();
  const workspaceId = activeWorkspace?.id;
  const [result, setResult] = useState<{
    workspaceId: string;
    applications: ApplicationResponse[];
    error: string | null;
  } | null>(null);

  useEffect(() => {
    if (authLoading || !user || !workspaceId) return;
    let active = true;
    applicationsApi
      .list(0, 50, workspaceId)
      .then((page) => {
        if (active)
          setResult({
            workspaceId,
            applications: page.content || [],
            error: null,
          });
      })
      .catch((err: unknown) => {
        if (active)
          setResult({
            workspaceId,
            applications: [],
            error:
              err instanceof Error
                ? err.message
                : "Failed to load applications",
          });
      });
    return () => {
      active = false;
    };
  }, [authLoading, user, workspaceId]);

  const current = result?.workspaceId === workspaceId ? result : null;

  return (
    <div className="min-h-screen w-full bg-zinc-950 text-zinc-100 flex flex-col">
      <Navbar />
      <main className="flex-1 px-4 py-8 sm:px-6">
        <div className="mx-auto max-w-7xl space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-6 border-b border-zinc-800/80 gap-4">
            <div>
              <div className="flex items-center gap-2 text-xs font-mono text-zinc-400 mb-1">
                <span>Platform</span>
                <span>/</span>
                <span className="text-sky-400">Applications</span>
              </div>
              <h1 className="text-2xl font-bold tracking-tight">
                Applications
              </h1>
              <p className="text-xs text-zinc-400 mt-1">
                Multi-service applications, each in a single repository.
              </p>
            </div>
            <Link href="/applications/new">
              <Button type="button" variant="primary" size="md">
                <Plus className="h-4 w-4 mr-1.5" />
                New Application
              </Button>
            </Link>
          </div>

          <ErrorAlert error={current?.error ?? null} />

          {!current ? (
            <p className="text-xs text-zinc-500">Loading applications...</p>
          ) : current.applications.length === 0 && !current.error ? (
            <EmptyState
              icon={<Boxes className="h-8 w-8 text-zinc-500" />}
              title="No applications yet"
              description="Group several services into one repository with shared infrastructure."
            />
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {current.applications.map((app) => {
                const badge = statusBadge(app.status);
                return (
                  <Card
                    key={app.id}
                    className="border-zinc-800 bg-zinc-900/60 hover:border-zinc-700"
                  >
                    <CardContent className="p-5 space-y-3">
                      <div className="flex items-start justify-between gap-3">
                        <Link
                          href={`/applications/${app.id}`}
                          className="text-sm font-semibold text-zinc-100 hover:text-sky-300"
                        >
                          {app.name}
                        </Link>
                        <Badge variant={badge.variant} size="sm">
                          {badge.label}
                        </Badge>
                      </div>
                      {app.description && (
                        <p className="text-xs text-zinc-400">
                          {app.description}
                        </p>
                      )}
                      <div className="flex flex-wrap gap-1.5">
                        {app.services.map((s) => (
                          <Badge key={s.id} variant="outline" size="sm">
                            {s.name}:{s.port}
                          </Badge>
                        ))}
                      </div>
                      {app.repositoryUrl && (
                        <a
                          href={app.repositoryUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center gap-1 text-xs text-sky-400 hover:text-sky-300"
                        >
                          <ExternalLink className="h-3 w-3" />
                          {app.repositoryUrl.replace("https://github.com/", "")}
                        </a>
                      )}
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
