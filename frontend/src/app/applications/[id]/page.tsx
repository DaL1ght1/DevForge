"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ExternalLink, Loader2 } from "lucide-react";
import { Navbar } from "@/components/layout/Navbar";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { Badge } from "@/components/ui/Badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/Card";
import { applicationsApi } from "@/lib/api/applications";
import { isInProgress, statusBadge } from "@/lib/applicationStatus";
import type { ApplicationResponse } from "@/types/api";

const POLL_MS = 3000;

export default function ApplicationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [state, setState] = useState<{
    id: string;
    app: ApplicationResponse | null;
    error: string | null;
  } | null>(null);

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const load = () => {
      applicationsApi
        .getById(id)
        .then((app) => {
          if (!active) return;
          setState({ id, app, error: null });
          if (isInProgress(app.status)) timer = setTimeout(load, POLL_MS);
        })
        .catch((err: unknown) => {
          if (!active) return;
          setState({
            id,
            app: null,
            error:
              err instanceof Error ? err.message : "Failed to load application",
          });
        });
    };
    load();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [id]);

  const current = state?.id === id ? state : null;
  const app = current?.app ?? null;
  const badge = app ? statusBadge(app.status) : null;

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
      <Navbar />
      <main className="flex-1 px-4 py-8 sm:px-6">
        <div className="mx-auto max-w-5xl space-y-6">
          <div className="flex items-center gap-2 text-xs font-mono text-zinc-400">
            <Link href="/applications" className="hover:text-zinc-200">
              Applications
            </Link>
            <span>/</span>
            <span className="text-sky-400">{app?.name ?? "..."}</span>
          </div>

          <ErrorAlert error={current?.error ?? null} />

          {!app ? (
            !current?.error && (
              <div className="flex items-center gap-2 text-xs text-zinc-400">
                <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading...
              </div>
            )
          ) : (
            <>
              <div className="flex flex-wrap items-center justify-between gap-3 pb-6 border-b border-zinc-800/80">
                <div>
                  <h1 className="text-2xl font-bold tracking-tight">
                    {app.name}
                  </h1>
                  {app.description && (
                    <p className="text-xs text-zinc-400 mt-1">
                      {app.description}
                    </p>
                  )}
                </div>
                {badge && (
                  <Badge
                    variant={badge.variant}
                    pulse={isInProgress(app.status)}
                  >
                    {badge.label}
                  </Badge>
                )}
              </div>

              {app.failureReason && <ErrorAlert error={app.failureReason} />}

              {app.repositoryUrl && (
                <a
                  href={app.repositoryUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 text-sm text-sky-400 hover:text-sky-300"
                >
                  <ExternalLink className="h-4 w-4" />
                  {app.repositoryUrl}
                </a>
              )}

              <Card className="border-zinc-800 bg-zinc-900/60">
                <CardHeader className="pb-3 border-b border-zinc-800/80">
                  <CardTitle className="text-sm">Services</CardTitle>
                  <CardDescription>
                    Run them all with{" "}
                    <code className="text-zinc-300">
                      docker compose up --build
                    </code>{" "}
                    from the repository root.
                  </CardDescription>
                </CardHeader>
                <CardContent className="p-0">
                  <table className="w-full text-xs">
                    <thead className="text-left text-[10px] uppercase tracking-wider text-zinc-500">
                      <tr>
                        <th className="px-4 py-2">Service</th>
                        <th className="px-4 py-2">Path</th>
                        <th className="px-4 py-2">Port</th>
                        <th className="px-4 py-2">Template</th>
                        <th className="px-4 py-2">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800">
                      {app.services.map((s) => {
                        const sb = statusBadge(s.status);
                        return (
                          <tr key={s.id}>
                            <td className="px-4 py-2 font-mono">
                              <Link
                                href={`/services/${s.id}`}
                                className="text-zinc-100 hover:text-sky-300"
                              >
                                {s.name}
                              </Link>
                            </td>
                            <td className="px-4 py-2 font-mono text-zinc-400">
                              {s.repositoryUrl ? (
                                <a
                                  href={s.repositoryUrl}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="hover:text-sky-300"
                                >
                                  {s.path}
                                </a>
                              ) : (
                                s.path
                              )}
                            </td>
                            <td className="px-4 py-2 font-mono">{s.port}</td>
                            <td className="px-4 py-2 text-zinc-400">
                              {s.templateName} v{s.templateVersion}
                            </td>
                            <td className="px-4 py-2">
                              <Badge variant={sb.variant} size="sm">
                                {sb.label}
                              </Badge>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </CardContent>
              </Card>
            </>
          )}
        </div>
      </main>
    </div>
  );
}
