"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  ArrowRight,
  Code2,
  ExternalLink,
  GitBranch,
  Plus,
  RefreshCw,
  Search,
} from "lucide-react";
import { servicesApi } from "@/lib/api/services";
import { AppServiceResponse } from "@/types/api";
import { Navbar } from "@/components/layout/Navbar";
import { EmptyState } from "@/components/common/EmptyState";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";

function formatStatus(status: string) {
  switch (status) {
    case "READY":
    case "DEPLOYED":
      return { label: "READY", variant: "success" as const };
    case "PENDING":
    case "CREATING":
      return { label: "PROVISIONING", variant: "warning" as const };
    case "FAILED":
      return { label: "FAILED", variant: "danger" as const };
    default:
      return { label: status, variant: "default" as const };
  }
}

export default function ServicesPage() {
  const [services, setServices] = useState<AppServiceResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const loadData = async () => {
    try {
      setLoading(true);
      const servicesPage = await servicesApi.list(0, 50);
      setServices(servicesPage.content || []);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to load services";
      console.error(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let active = true;
    servicesApi
      .list(0, 50)
      .then((servicesPage) => {
        if (!active) return;
        setServices(servicesPage.content || []);
      })
      .catch((err: unknown) => {
        console.error(err);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const filteredServices = useMemo(() => {
    const q = search.trim().toLowerCase();
    return services.filter((service) => {
      const matchesSearch =
        !q ||
        service.name.toLowerCase().includes(q) ||
        (service.description || "").toLowerCase().includes(q);
      const matchesStatus = statusFilter === "ALL" || service.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [search, services, statusFilter]);

  const summary = useMemo(() => {
    return {
      total: services.length,
      ready: services.filter((svc) => svc.status === "DEPLOYED" || svc.status === "READY").length,
      provisioning: services.filter(
        (svc) => svc.status === "CREATING" || svc.status === "PENDING"
      ).length,
      failed: services.filter((svc) => svc.status === "FAILED").length,
    };
  }, [services]);

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
                <span className="text-sky-400">Services</span>
              </div>
              <h1 className="text-2xl font-bold tracking-tight text-zinc-100">
                Service Catalog
              </h1>
              <p className="text-xs text-zinc-400 mt-1 max-w-xl">
                Provisioned microservices, active deployments, and automated repository configurations.
              </p>
            </div>

            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={loadData}
                disabled={loading}
                title="Refresh services"
              >
                <RefreshCw className={loading ? "h-3.5 w-3.5 animate-spin mr-1" : "h-3.5 w-3.5 mr-1"} />
                Refresh
              </Button>

              <Link href="/services/new">
                <Button
                  variant="primary"
                  size="sm"
                  className="bg-sky-600 hover:bg-sky-500 text-white"
                >
                  <Plus className="h-4 w-4 mr-1.5" />
                  Initialize Service
                </Button>
              </Link>
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Card className="bg-zinc-900/60 border-zinc-800">
              <CardContent className="p-4">
                <div className="text-[11px] font-mono uppercase tracking-wider text-zinc-400">
                  Total Services
                </div>
                <div className="mt-2 text-2xl font-bold text-zinc-100 font-mono">
                  {summary.total}
                </div>
              </CardContent>
            </Card>

            <Card className="bg-zinc-900/60 border-zinc-800">
              <CardContent className="p-4">
                <div className="text-[11px] font-mono uppercase tracking-wider text-emerald-400 flex items-center justify-between">
                  <span>Operational / Ready</span>
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
                </div>
                <div className="mt-2 text-2xl font-bold text-zinc-100 font-mono">
                  {summary.ready}
                </div>
              </CardContent>
            </Card>

            <Card className="bg-zinc-900/60 border-zinc-800">
              <CardContent className="p-4">
                <div className="text-[11px] font-mono uppercase tracking-wider text-amber-400 flex items-center justify-between">
                  <span>Provisioning</span>
                  <span className="h-1.5 w-1.5 rounded-full bg-amber-400" />
                </div>
                <div className="mt-2 text-2xl font-bold text-zinc-100 font-mono">
                  {summary.provisioning}
                </div>
              </CardContent>
            </Card>

            <Card className="bg-zinc-900/60 border-zinc-800">
              <CardContent className="p-4">
                <div className="text-[11px] font-mono uppercase tracking-wider text-rose-400 flex items-center justify-between">
                  <span>Failed</span>
                  <span className="h-1.5 w-1.5 rounded-full bg-rose-400" />
                </div>
                <div className="mt-2 text-2xl font-bold text-zinc-100 font-mono">
                  {summary.failed}
                </div>
              </CardContent>
            </Card>
          </div>

          <Card className="border-zinc-800 bg-zinc-900/40">
            <CardContent className="flex flex-col gap-3 p-3.5 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex-1">
                <Input
                  aria-label="Filter services by name or description"
                  placeholder="Filter services by identifier or description..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  leftIcon={<Search className="h-3.5 w-3.5" />}
                />
              </div>
              <div className="sm:w-52">
                <Select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  options={[
                    { value: "ALL", label: "All Statuses" },
                    { value: "DEPLOYED", label: "Ready / Deployed" },
                    { value: "CREATING", label: "Provisioning" },
                    { value: "PENDING", label: "Pending" },
                    { value: "FAILED", label: "Failed" },
                  ]}
                />
              </div>
            </CardContent>
          </Card>

          {loading ? (
            <div className="flex flex-col items-center justify-center p-16 text-zinc-400 space-y-3">
              <div className="h-8 w-8 animate-spin rounded-full border-2 border-zinc-700 border-t-sky-400" />
              <p className="text-xs">Loading service catalog...</p>
            </div>
          ) : filteredServices.length === 0 ? (
            <EmptyState
              title={services.length === 0 ? "No services registered" : "No matching services found"}
              description={
                services.length === 0
                  ? "Scaffold your first microservice using the multi-level initialization wizard."
                  : "Try adjusting your search query or status filter to locate the service."
              }
              action={
                <Link href="/services/new">
                  <Button
                    variant="primary"
                    size="sm"
                    className="bg-sky-600 hover:bg-sky-500 text-white"
                  >
                    <Plus className="h-4 w-4 mr-1.5" />
                    Initialize Service
                  </Button>
                </Link>
              }
            />
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {filteredServices.map((srv) => {
                const status = formatStatus(srv.status);

                return (
                  <Link
                    key={srv.id}
                    href={`/services/${srv.id}`}
                    className="group block rounded-xl focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-500"
                  >
                    <Card className="h-full border-zinc-800 bg-zinc-900/60 transition-colors group-hover:border-zinc-700 group-hover:bg-zinc-900/90 flex flex-col justify-between">
                      <div>
                        <CardHeader className="flex flex-row items-start justify-between gap-3 pb-3 border-b-0">
                          <div className="min-w-0 space-y-1">
                            <CardTitle className="truncate font-mono text-sm text-zinc-100 group-hover:text-sky-300 transition-colors">
                              {srv.name}
                            </CardTitle>
                            <p className="line-clamp-2 text-xs text-zinc-400">
                              {srv.description || "No description provided"}
                            </p>
                          </div>
                          <Badge variant={status.variant} size="sm">
                            {status.label}
                          </Badge>
                        </CardHeader>

                        <CardContent className="space-y-2.5 pt-0 text-xs text-zinc-400">
                          <div className="flex items-center justify-between border-t border-zinc-800/80 pt-3">
                            <span className="flex items-center gap-1.5 text-zinc-400">
                              <Code2 className="h-3.5 w-3.5 text-zinc-500" />
                              Template
                            </span>
                            <span className="font-mono text-zinc-200 truncate max-w-45">
                              {srv.templateVersion?.version ? `v${srv.templateVersion.version}` : "Standard Runtime"}
                            </span>
                          </div>

                          <div className="flex items-center justify-between">
                            <span className="text-zinc-400">Owner</span>
                            <span className="font-mono text-zinc-300">
                              {srv.owner?.username || "system"}
                            </span>
                          </div>

                          {srv.repositoryUrl && (
                            <div className="flex items-center justify-between border-t border-zinc-800/80 pt-2.5">
                              <span className="inline-flex items-center gap-1.5 text-zinc-400">
                                <GitBranch className="h-3.5 w-3.5 text-zinc-500" />
                                Repository
                              </span>
                              <span className="font-mono text-sky-400 flex items-center gap-1 text-[11px]">
                                GitHub <ExternalLink className="h-3 w-3" />
                              </span>
                            </div>
                          )}
                        </CardContent>
                      </div>

                      <div className="p-4 pt-0 border-t border-zinc-800/60 mt-3 flex items-center justify-between text-[11px] text-zinc-400 group-hover:text-zinc-300 transition-colors">
                        <span>Created {new Date(srv.createdAt).toLocaleDateString()}</span>
                        <div className="flex items-center gap-1 font-medium text-sky-400">
                          <span>Inspect</span>
                          <ArrowRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
                        </div>
                      </div>
                    </Card>
                  </Link>
                );
              })}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}