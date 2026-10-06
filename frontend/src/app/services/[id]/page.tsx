"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  ArrowLeft,
  Calendar,
  Code2,
  ExternalLink,
  GitBranch,
  RefreshCw,
  Server,
  Shield,
  Trash2,
  User,
} from "lucide-react";
import { deploymentsApi } from "@/lib/api/deployments";
import { servicesApi } from "@/lib/api/services";
import { AppDeploymentResponse, AppServiceResponse } from "@/types/api";
import { Navbar } from "@/components/layout/Navbar";
import { Button } from "@/components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Dialog } from "@/components/ui/Dialog";

function formatStatusBadge(status: string) {
  switch (status) {
    case "PUSHED":
      return <Badge variant="success">REPOSITORY PUSHED</Badge>;
    case "DEPLOYED":
      return <Badge variant="success">DEPLOYED</Badge>;
    case "PENDING":
    case "CREATING":
      return <Badge variant="warning">PROVISIONING</Badge>;
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
    case "DEPLOYING":
      return { label: "In Progress", variant: "warning" as const };
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
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);

  const serviceId = params?.id as string;

  const loadData = async () => {
    if (!serviceId) return;
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

  useEffect(() => {
    if (!serviceId) return;
    let active = true;
    Promise.all([
      servicesApi.getById(serviceId),
      deploymentsApi.getByService(serviceId, 0, 20),
    ])
      .then(([serviceResponse, deploymentPage]) => {
        if (!active) return;
        setService(serviceResponse);
        setDeployments(deploymentPage.content || []);
      })
      .catch((error) => {
        console.error("Failed to load service details", error);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [serviceId]);

  const handleDeleteConfirm = async () => {
    if (!service) return;
    setDeleting(true);
    try {
      await servicesApi.delete(service.id);
      router.push("/services");
    } catch (err) {
      console.error("Delete failed", err);
    } finally {
      setDeleting(false);
      setIsDeleteDialogOpen(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-zinc-950 flex flex-col">
        <Navbar />
        <div className="flex-1 flex flex-col items-center justify-center space-y-3 text-zinc-400">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-zinc-700 border-t-sky-400" />
          <p className="text-xs">Loading service metadata...</p>
        </div>
      </div>
    );
  }

  if (!service) {
    return (
      <div className="min-h-screen bg-zinc-950 flex flex-col">
        <Navbar />
        <div className="flex-1 flex items-center justify-center p-4">
          <Card className="w-full max-w-md border-zinc-800 bg-zinc-900/80 p-6 text-center">
            <h2 className="text-base font-semibold text-zinc-100 mb-2">Service Not Found</h2>
            <p className="text-xs text-zinc-400 mb-4">
              The service with identifier <code className="font-mono text-zinc-200">{serviceId}</code> does not exist or has been removed.
            </p>
            <Link href="/services">
              <Button variant="outline" size="sm">
                <ArrowLeft className="mr-1.5 h-3.5 w-3.5" /> Back to Catalog
              </Button>
            </Link>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
      <Navbar />

      <main className="flex-1 px-4 py-8 sm:px-6">
        <div className="mx-auto max-w-6xl space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between pb-6 border-b border-zinc-800/80 gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2 text-xs font-mono text-zinc-400 mb-1">
                <Link href="/services" className="hover:text-zinc-200 transition-colors">
                  Services
                </Link>
                <span>/</span>
                <span className="text-sky-400">{service.name}</span>
              </div>

              <div className="flex items-center gap-3">
                <h1 className="text-2xl font-bold font-mono tracking-tight text-zinc-100">
                  {service.name}
                </h1>
                {formatStatusBadge(service.status)}
              </div>

              <p className="text-xs text-zinc-400 max-w-2xl mt-1">
                {service.description || "No service description provided."}
              </p>
            </div>

            <div className="flex items-center gap-2">
              <Link href="/services">
                <Button variant="outline" size="sm">
                  <ArrowLeft className="mr-1.5 h-3.5 w-3.5" /> Catalog
                </Button>
              </Link>
              <Button
                variant="outline"
                size="sm"
                disabled
                title="Deployment support is coming soon"
              >
                <ExternalLink className="mr-1.5 h-3.5 w-3.5" /> Deploy
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={loadData}
                title="Refresh service status"
              >
                <RefreshCw className="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={() => setIsDeleteDialogOpen(true)}
              >
                <Trash2 className="mr-1.5 h-3.5 w-3.5" /> Delete Service
              </Button>
            </div>
          </div>

          <div className="grid gap-6 lg:grid-cols-[1.3fr_1fr]">
            <Card className="border-zinc-800 bg-zinc-900/60">
              <CardHeader className="pb-3 border-b border-zinc-800/80">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm text-zinc-100 flex items-center gap-2">
                    <Server className="h-4 w-4 text-sky-400" />
                    Service Architecture
                  </CardTitle>
                  <span className="font-mono text-[11px] text-zinc-500">ID: {service.id}</span>
                </div>
              </CardHeader>

              <CardContent className="p-5">
                <div className="grid grid-cols-2 gap-4 text-xs">
                  <div className="p-3.5 rounded-lg border border-zinc-800 bg-zinc-950/60">
                    <span className="text-[10px] font-mono uppercase tracking-wider text-zinc-500 block mb-1">
                      Template & Framework
                    </span>
                    <div className="flex items-center gap-1.5 font-medium text-zinc-200">
                      <Code2 className="h-3.5 w-3.5 text-zinc-400" />
                      {service.templateVersion?.sourcePath || "Template unavailable"}
                    </div>
                  </div>

                  <div className="p-3.5 rounded-lg border border-zinc-800 bg-zinc-950/60">
                    <span className="text-[10px] font-mono uppercase tracking-wider text-zinc-500 block mb-1">
                      Service Owner
                    </span>
                    <div className="flex items-center gap-1.5 font-medium text-zinc-200">
                      <User className="h-3.5 w-3.5 text-zinc-400" />
                      {service.owner?.username || "Unavailable"}
                    </div>
                  </div>
                  <div className="mt-4 rounded-lg border border-sky-900/50 bg-sky-950/20 p-3 text-xs">
                    <div className="font-mono text-[10px] uppercase tracking-wider text-sky-300">Template manifest metadata</div>
                    <pre className="mt-2 overflow-x-auto whitespace-pre-wrap text-[11px] leading-relaxed text-zinc-300">{`version: ${service.templateVersion?.version || "unknown"}\nsourcePath: ${service.templateVersion?.sourcePath || "unknown"}\nactive: ${service.templateVersion?.active ?? false}`}</pre>
                    <p className="mt-2 text-[11px] text-zinc-500">Metadata is read from the backend template version. The full YAML is not exposed by the current API.</p>
                  </div>

                  <div className="p-3.5 rounded-lg border border-zinc-800 bg-zinc-950/60">
                    <span className="text-[10px] font-mono uppercase tracking-wider text-zinc-500 block mb-1">
                      Registered At
                    </span>
                    <div className="flex items-center gap-1.5 font-medium text-zinc-200">
                      <Calendar className="h-3.5 w-3.5 text-zinc-400" />
                      {new Date(service.createdAt).toLocaleString()}
                    </div>
                  </div>

                  <div className="p-3.5 rounded-lg border border-zinc-800 bg-zinc-950/60">
                    <span className="text-[10px] font-mono uppercase tracking-wider text-zinc-500 block mb-1">
                      Git Repository
                    </span>
                    {service.repositoryUrl ? (
                      <a
                        href={service.repositoryUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 text-sky-400 hover:text-sky-300 font-medium truncate"
                      >
                        <GitBranch className="h-3.5 w-3.5" />
                        GitHub Repository <ExternalLink className="h-3 w-3" />
                      </a>
                    ) : (
                      <span className="text-zinc-500 font-mono">Provisioning repo...</span>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card className="border-zinc-800 bg-zinc-900/60">
              <CardHeader className="pb-3 border-b border-zinc-800/80">
                <CardTitle className="text-sm text-zinc-100 flex items-center gap-2">
                  <Shield className="h-4 w-4 text-emerald-400" />
                  Platform Governance
                </CardTitle>
              </CardHeader>
              <CardContent className="p-5 space-y-3 text-xs">
                <div className="p-3 rounded-lg border border-zinc-800 bg-zinc-950/60 flex items-center justify-between">
                  <span className="text-zinc-400">Repository</span>
                  <span className="font-mono text-zinc-200">{service.repositoryUrl ? "Connected" : "Pending"}</span>
                </div>

                <div className="p-3 rounded-lg border border-zinc-800 bg-zinc-950/60 flex items-center justify-between">
                  <span className="text-zinc-400">Lifecycle State</span>
                  <span className="font-mono text-sky-400">{service.status}</span>
                </div>
              </CardContent>
            </Card>
          </div>

          <Card className="border-zinc-800 bg-zinc-900/60">
            <CardHeader className="pb-3 border-b border-zinc-800/80">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-sm text-zinc-100">Deployment History</CardTitle>
                  <CardDescription>
                    Environment release logs and deployment statuses for this service.
                  </CardDescription>
                </div>
                <Badge variant="outline" size="sm">
                  {deployments.length} Record{deployments.length === 1 ? "" : "s"}
                </Badge>
              </div>
            </CardHeader>

            <CardContent className="p-0">
              {deployments.length === 0 ? (
                <div className="p-8 text-center text-xs text-zinc-500">
                  No automated deployments recorded yet. Trigger a pipeline build to initiate a release.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse">
                    <thead className="bg-zinc-950/70 border-b border-zinc-800 text-[11px] font-mono uppercase tracking-wider text-zinc-500">
                      <tr>
                        <th className="px-5 py-3">Environment</th>
                        <th className="px-5 py-3">Version / Commit</th>
                        <th className="px-5 py-3">Status</th>
                        <th className="px-5 py-3">Timestamp</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-800/60">
                      {deployments.map((dep) => {
                        const s = formatDeploymentStatus(dep.status);
                        return (
                          <tr key={dep.id} className="hover:bg-zinc-900/40">
                            <td className="px-5 py-3 font-semibold text-zinc-200">
                              {dep.environment}
                            </td>
                            <td className="px-5 py-3 font-mono text-zinc-300">
                              {dep.version}
                            </td>
                            <td className="px-5 py-3">
                              <Badge variant={s.variant} size="sm">
                                {s.label}
                              </Badge>
                            </td>
                            <td className="px-5 py-3 text-zinc-400">
                              {new Date(dep.deployedAt).toLocaleString()}
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
      </main>

      <Dialog
        isOpen={isDeleteDialogOpen}
        onClose={() => setIsDeleteDialogOpen(false)}
        title="Delete Service"
        description="Are you sure you want to delete this service? This action is permanent."
      >
        <div className="space-y-4 text-xs text-zinc-300">
          <p>
            Deleting <strong className="font-mono text-zinc-100">{service.name}</strong> will remove it from the catalog and teardown associated deployment records.
          </p>

          <div className="flex justify-end gap-2 pt-2 border-t border-zinc-800">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setIsDeleteDialogOpen(false)}
              disabled={deleting}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="danger"
              size="sm"
              isLoading={deleting}
              onClick={handleDeleteConfirm}
            >
              Confirm Deletion
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
