"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { ArrowRight, ExternalLink, LogOut, Plus, Search } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { servicesApi } from "@/lib/api/services";
import { templatesApi } from "@/lib/api/templates";
import { AppServiceResponse, AppTemplateResponse } from "@/types/api";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/Card";
import { Dialog } from "@/components/ui/Dialog";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";

const DEFAULT_TEMPLATE_VERSION_ID = "b0000000-0000-0000-0000-000000000001";
const CANONICAL_TEMPLATE_NAME = "spring_Boot_Maven";

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

function formatTemplateName(template: AppTemplateResponse) {
  return template.name || CANONICAL_TEMPLATE_NAME;
}

export default function ServicesPage() {
  const { user, logout } = useAuth();
  const [services, setServices] = useState<AppServiceResponse[]>([]);
  const [templates, setTemplates] = useState<AppTemplateResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [databaseType, setDatabaseType] = useState("POSTGRESQL");
  const [selectedTemplateId, setSelectedTemplateId] = useState(DEFAULT_TEMPLATE_VERSION_ID);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    const loadData = async () => {
      try {
        setLoading(true);
        const [servicesPage, templatesPage] = await Promise.all([
          servicesApi.list(0, 50),
          templatesApi.list(0, 50),
        ]);

        if (!active) return;
        setServices(servicesPage.content || []);
        setTemplates(templatesPage.content || []);
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : "Failed to load services";
        console.error(message);
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void loadData();
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
      ready: services.filter((svc) => svc.status === "DEPLOYED" || svc.status === "PENDING").length,
      provisioning: services.filter((svc) => svc.status === "CREATING").length,
    };
  }, [services]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError(null);
    setCreating(true);

    try {
      await servicesApi.create({
        name,
        description,
        templateVersionId: selectedTemplateId || DEFAULT_TEMPLATE_VERSION_ID,
        databaseType,
      });

      setIsModalOpen(false);
      setName("");
      setDescription("");
      setDatabaseType("POSTGRESQL");
      await servicesApi.list(0, 50).then((page) => setServices(page.content || []));
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to create service";
      const normalized = message.toLowerCase();

      if (normalized.includes("template") && normalized.includes("name")) {
        setCreateError(
          "Provisioning is currently blocked by a backend template-name mismatch: the provisioner rejects the seeded template name. The backend must align its template configuration before new services can be created."
        );
        return;
      }

      setCreateError(message);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="min-h-screen w-full bg-zinc-950 p-6">
      <div className="mx-auto max-w-7xl space-y-6">
        <PageHeader
          title="Service Catalog"
          description="Provision, manage, and monitor developer services across the platform."
          actions={
            <div className="flex items-center gap-2">
              <Button variant="outline" size="sm" onClick={logout}>
                <LogOut className="h-3.5 w-3.5 mr-1" />
                Sign Out ({user?.username})
              </Button>
              <Button variant="primary" size="sm" onClick={() => setIsModalOpen(true)}>
                <Plus className="h-4 w-4 mr-1" />
                Create Service
              </Button>
            </div>
          }
        />

        <div className="grid gap-3 md:grid-cols-3">
          <Card className="bg-zinc-900 border-zinc-800">
            <CardContent className="p-4">
              <div className="text-[11px] uppercase tracking-[0.2em] text-zinc-500">Total services</div>
              <div className="mt-3 text-2xl font-semibold text-zinc-100">{summary.total}</div>
            </CardContent>
          </Card>
          <Card className="bg-zinc-900 border-zinc-800">
            <CardContent className="p-4">
              <div className="text-[11px] uppercase tracking-[0.2em] text-zinc-500">Ready</div>
              <div className="mt-3 text-2xl font-semibold text-zinc-100">{summary.ready}</div>
            </CardContent>
          </Card>
          <Card className="bg-zinc-900 border-zinc-800">
            <CardContent className="p-4">
              <div className="text-[11px] uppercase tracking-[0.2em] text-zinc-500">Provisioning</div>
              <div className="mt-3 text-2xl font-semibold text-zinc-100">{summary.provisioning}</div>
            </CardContent>
          </Card>
        </div>

        <Card className="border-zinc-800 bg-zinc-900/60">
          <CardContent className="flex flex-col gap-3 p-4 md:flex-row md:items-center md:justify-between">
            <div className="flex-1">
              <Input
                aria-label="Search services"
                placeholder="Search by service name or description"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                leftIcon={<Search className="h-3.5 w-3.5" />}
              />
            </div>
            <div className="md:w-52">
              <Select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                options={[
                  { value: "ALL", label: "All statuses" },
                  { value: "CREATING", label: "Provisioning" },
                  { value: "PENDING", label: "Pending" },
                  { value: "DEPLOYED", label: "Ready" },
                  { value: "FAILED", label: "Failed" },
                ]}
              />
            </div>
          </CardContent>
        </Card>

        {loading ? (
          <div className="flex justify-center p-12">
            <div className="h-8 w-8 animate-spin rounded-full border-b-2 border-blue-500" />
          </div>
        ) : filteredServices.length === 0 ? (
          <EmptyState
            title="No services match your filters"
            description="Create an application or adjust the search to find the service you need."
            action={
              <Button variant="primary" size="sm" onClick={() => setIsModalOpen(true)}>
                <Plus className="h-4 w-4 mr-1" />
                Create Service
              </Button>
            }
          />
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {filteredServices.map((srv) => {
              const status = formatStatus(srv.status);

              return (
                <Link key={srv.id} href={`/services/${srv.id}`} className="block rounded-xl focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/70">
                  <Card className="h-full border-zinc-800 bg-zinc-900/60 hover:border-zinc-700 transition-colors">
                    <CardHeader className="flex flex-row items-start justify-between gap-3 pb-3">
                      <div className="min-w-0 space-y-1">
                        <CardTitle className="truncate font-mono text-sm text-zinc-100">{srv.name}</CardTitle>
                        <p className="line-clamp-2 text-[11px] text-zinc-400">
                          {srv.description || "No description provided"}
                        </p>
                      </div>
                      <Badge variant={status.variant} size="sm">{status.label}</Badge>
                    </CardHeader>

                    <CardContent className="space-y-3 pt-0 text-xs text-zinc-400">
                      <div className="flex items-center justify-between border-t border-zinc-800/80 pt-3">
                        <span>Framework</span>
                        <span className="text-zinc-200">
                          {srv.templateVersion?.version ? CANONICAL_TEMPLATE_NAME : "Standard"}
                        </span>
                      </div>

                      <div className="flex items-center justify-between">
                        <span>Owner</span>
                        <span className="text-zinc-200">{srv.owner?.username || "System"}</span>
                      </div>

                      <div className="flex items-center justify-between">
                        <span>Database</span>
                        <span className="text-zinc-200">PostgreSQL</span>
                      </div>

                      {srv.repositoryUrl && (
                        <div className="flex items-center justify-between border-t border-zinc-800/80 pt-3">
                          <span className="inline-flex items-center gap-1.5 text-zinc-300">
                            <ExternalLink className="h-3.5 w-3.5" />
                            Repository
                          </span>
                          <span className="font-mono text-blue-400">GitHub</span>
                        </div>
                      )}

                      <div className="flex items-center justify-between pt-2 text-[11px] text-zinc-500">
                        <span>Open details</span>
                        <ArrowRight className="h-3.5 w-3.5 text-zinc-400" />
                      </div>
                    </CardContent>
                  </Card>
                </Link>
              );
            })}
          </div>
        )}
      </div>

      <Dialog
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title="Create New Service"
        description="Scaffold a new microservice and provision its repository automatically."
      >
        <form onSubmit={handleCreate} className="space-y-4">
          <ErrorAlert error={createError} />

          <Input
            label="Service Name"
            placeholder="e.g. order-service"
            value={name}
            onChange={(e) => setName(e.target.value.toLowerCase())}
            hint="Lowercase letters, numbers, and hyphens only"
            pattern="^[a-z0-9-]{3,20}$"
            required
          />

          <Input
            label="Description"
            placeholder="Handles order processing and fulfillment"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />

          <Select
            label="Template"
            value={selectedTemplateId}
            onChange={(e) => setSelectedTemplateId(e.target.value)}
            options={templates.length > 0 ? templates.map((template) => ({
              value: DEFAULT_TEMPLATE_VERSION_ID,
              label: formatTemplateName(template),
            })) : [{ value: DEFAULT_TEMPLATE_VERSION_ID, label: CANONICAL_TEMPLATE_NAME }]}
          />

          <Select
            label="Database"
            value={databaseType}
            onChange={(e) => setDatabaseType(e.target.value)}
            options={[
              { value: "POSTGRESQL", label: "PostgreSQL" },
              { value: "MYSQL", label: "MySQL" },
              { value: "NONE", label: "None" },
            ]}
          />

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="ghost" onClick={() => setIsModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" isLoading={creating}>
              Create & Provision
            </Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
}