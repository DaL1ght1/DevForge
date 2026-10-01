"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  ArrowLeft,
  ExternalLink,
  GitBranch,
  Package,
  ShieldCheck,
  Trash2,
} from "lucide-react";
import { deploymentsApi } from "@/lib/api/deployments";
import { servicesApi } from "@/lib/api/services";
import { AppDeploymentResponse, AppServiceResponse } from "@/types/api";
import { PageHeader } from "@/components/common/PageHeader";
import { Button } from "@/components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";

function formatStatusBadge(status: string) {
  switch (status) {
    case "DEPLOYED":
      return <Badge variant="success">READY</Badge>;
    case "PENDING":
    case "CREATING":
      return <Badge variant="warning" pulse>PROVISIONING</Badge>;
    case "FAILED":
      return <Badge variant="danger">FAILED</Badge>;
    default:
      return <Badge variant="default">{status}</Badge>;
  }
}

function formatDeploymentStatus(status: string) {
  switch (status) {
    case "DEPLOYED":
      return { label: "Deployed", variant: "success" as const };
    case "PENDING":
      return { label: "Pending", variant: "warning" as const };
    case "FAILED":
      return { label: "Failed", variant: "danger" as const };
    default:
      return { label: status, variant: "default" as const };
  }
}

export default function ServiceDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [service, setService] = useState<AppServiceResponse | null>(null);
  const [deployments, setDeployments] = useState<AppDeploymentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);

  const serviceId = params?.id as string;

  useEffect(() => {
    if (!serviceId) return;

    const load = async () => {
      setLoading(true);
      try {
        const [serviceResponse, deploymentPage] = await Promise.all([
          servicesApi.getById(serviceId),
          deploymentsApi.getByService(serviceId, 0, 20),
        ]);

        setService(serviceResponse);
        setDeployments(deploymentPage.content || []);
      } catch (error) {
        console.error("Failed to load service details", error);
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [serviceId]);

  const handleDelete = async () => {
    if (!service || !window.confirm(`Delete service ${service.name}?`)) return;

    setDeleting(true);
    try {
      await servicesApi.delete(service.id);
      router.push("/services");
    } finally {
      setDeleting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-zinc-950">
        <div className="h-8 w-8 animate-spin rounded-full border-b-2 border-blue-500" />
      </div>
    );
  }

  if (!service) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-zinc-950 px-4">
        <Card className="w-full max-w-md border-zinc-800 bg-zinc-900/80 p-6 text-center">
          <p className="text-sm text-zinc-300">Service not found</p>
          <Link href="/services" className="mt-4 inline-flex items-center text-blue-400 hover:text-blue-300">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to catalog
          </Link>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-950 p-6">
      <div className="mx-auto max-w-6xl space-y-6">
        <PageHeader
          title={service.name}
          description={service.description || "No description provided"}
          breadcrumbs={[{ label: "Services", href: "/services" }, { label: service.name }]}
          actions={
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" onClick={() => router.push("/services")}>
                <ArrowLeft className="mr-1 h-3.5 w-3.5" /> Back
              </Button>
              <Button variant="danger" size="sm" onClick={handleDelete} isLoading={deleting}>
                <Trash2 className="mr-1 h-3.5 w-3.5" /> Delete
              </Button>
            </div>
          }
        />

        <div className="grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
          <Card className="border-zinc-800 bg-zinc-900/60">
            <CardHeader className="flex flex-row items-center justify-between gap-3 pb-3">
              <div className="flex items-center gap-2">
                <Package className="h-4 w-4 text-blue-400" />
                <CardTitle className="text-base text-zinc-100">Service overview</CardTitle>
              </div>
              {formatStatusBadge(service.status)}
            </CardHeader>

            <CardContent className="grid gap-4 md:grid-cols-2 text-sm text-zinc-300">
              <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-3">
                <div className="text-[11px] uppercase tracking-[0.18em] text-zinc-500">Template</div>
                <div className="mt-2 flex items-center gap-2 font-mono text-zinc-100">
                  <GitBranch className="h-4 w-4 text-zinc-400" />
                  {service.templateVersion?.version ? "spring_Boot_Maven" : "Standard"}
                </div>
              </div>

              <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-3">
                <div className="text-[11px] uppercase tracking-[0.18em] text-zinc-500">Owner</div>
                <div className="mt-2 text-zinc-100">{service.owner?.username || "System"}</div>
              </div>

              <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-3">
                <div className="text-[11px] uppercase tracking-[0.18em] text-zinc-500">Created</div>
                <div className="mt-2 text-zinc-100">
                  {new Date(service.createdAt).toLocaleString()}
                </div>
              </div>

              <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-3">
                <div className="text-[11px] uppercase tracking-[0.18em] text-zinc-500">Repository</div>
                {service.repositoryUrl ? (
                  <a
                    href={service.repositoryUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="mt-2 inline-flex items-center gap-2 text-blue-400 hover:text-blue-300"
                  >
                    Open GitHub <ExternalLink className="h-3.5 w-3.5" />
                  </a>
                ) : (
                  <div className="mt-2 text-zinc-100">Not available yet</div>
                )}
              </div>
            </CardContent>
          </Card>

          <Card className="border-zinc-800 bg-zinc-900/60">
            <CardHeader className="pb-3">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-emerald-400" />
                <CardTitle className="text-base text-zinc-100">Platform checks</CardTitle>
              </div>
            </CardHeader>
            <CardContent className="space-y-3 text-sm text-zinc-300">
              <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-3">
                <div className="text-[11px] uppercase tracking-[0.18em] text-zinc-500">Access</div>
                <div className="mt-2 text-zinc-100">Authenticated via Spring Security</div>
              </div>
              <div className="rounded-lg border border-zinc-800 bg-zinc-950/60 p-3">
                <div className="text-[11px] uppercase tracking-[0.18em] text-zinc-500">Deployment state</div>
                <div className="mt-2 text-zinc-100">
                  {service.status === "PENDING" ? "Queued for deployment" : service.status}
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        <Card className="border-zinc-800 bg-zinc-900/60">
          <CardHeader className="pb-3">
            <CardTitle className="text-base text-zinc-100">Deployment history</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {deployments.length === 0 ? (
              <div className="px-5 py-6 text-sm text-zinc-400">
                No deployments have been recorded for this service yet.
              </div>
            ) : (
              <div className="overflow-hidden">
                <table className="w-full border-collapse text-left text-sm">
                  <thead className="bg-zinc-950/70 text-[11px] uppercase tracking-[0.18em] text-zinc-500">
                    <tr>
                      <th className="px-5 py-3">Environment</th>
                      <th className="px-5 py-3">Version</th>
                      <th className="px-5 py-3">Status</th>
                      <th className="px-5 py-3">Deployed</th>
                    </tr>
                  </thead>
                  <tbody>
                    {deployments.map((deployment) => {
                      const status = formatDeploymentStatus(deployment.status);
                      return (
                        <tr key={deployment.id} className="border-t border-zinc-800 text-zinc-300">
                          <td className="px-5 py-3 font-medium text-zinc-100">{deployment.environment}</td>
                          <td className="px-5 py-3 font-mono text-zinc-200">{deployment.version}</td>
                          <td className="px-5 py-3">
                            <Badge variant={status.variant} size="sm">{status.label}</Badge>
                          </td>
                          <td className="px-5 py-3 text-zinc-400">
                            {new Date(deployment.deployedAt).toLocaleString()}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
