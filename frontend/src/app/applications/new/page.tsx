"use client";

import React, { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowLeft,
  ArrowRight,
  Boxes,
  Code2,
  Layers,
  Plus,
  Rocket,
  Trash2,
} from "lucide-react";
import { Navbar } from "@/components/layout/Navbar";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { Badge } from "@/components/ui/Badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/Card";
import { ServicePreview } from "@/components/services/ServicePreview";
import { TemplateOptionsForm } from "@/components/services/TemplateOptionsForm";
import { useWorkspace } from "@/context/WorkspaceContext";
import { ApiClientError } from "@/lib/api/client";
import { GITHUB_NOT_CONNECTED } from "@/lib/api/github";
import { applicationsApi } from "@/lib/api/applications";
import { templatesApi } from "@/lib/api/templates";
import {
  ApplicationDraft,
  MAX_SERVICES,
  ServiceDraft,
  applicationErrors,
  cleanName,
  portOf,
  serviceErrors,
  toCreationDto,
} from "@/lib/applicationDraft";
import { initialVariables, selectedLabels } from "@/lib/templateSelection";
import type { AppTemplateResponse, TemplateOptionsResponse } from "@/types/api";

let nextKey = 0;
const newService = (): ServiceDraft => ({
  key: `svc-${nextKey++}`,
  name: "",
  description: "",
  templateId: "",
  variables: {},
  features: {},
});

function templateLabel(t: AppTemplateResponse): string {
  return `${t.name} · ${t.language} / ${t.framework} / ${t.buildTool}`;
}

export default function NewApplicationPage() {
  const router = useRouter();
  const { activeWorkspace } = useWorkspace();
  const [templates, setTemplates] = useState<AppTemplateResponse[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [options, setOptions] = useState<
    Record<string, TemplateOptionsResponse>
  >({});
  const [draft, setDraft] = useState<ApplicationDraft>(() => ({
    name: "",
    description: "",
    services: [newService()],
  }));
  const [step, setStep] = useState<1 | 2>(1);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [githubNotConnected, setGithubNotConnected] = useState(false);

  useEffect(() => {
    let active = true;
    templatesApi
      .list(0, 100)
      .then((page) => {
        if (active) setTemplates(page.content || []);
      })
      .catch((err: unknown) => {
        if (active)
          setLoadError(
            err instanceof Error ? err.message : "Failed to load templates",
          );
      });
    return () => {
      active = false;
    };
  }, []);

  const errors = useMemo(
    () => applicationErrors(draft, options),
    [draft, options],
  );
  const request = useMemo(
    () =>
      step === 2 && errors.length === 0
        ? toCreationDto(draft, activeWorkspace?.id)
        : null,
    [step, errors, draft, activeWorkspace],
  );

  const updateService = (key: string, change: Partial<ServiceDraft>) =>
    setDraft((d) => ({
      ...d,
      services: d.services.map((s) =>
        s.key === key ? { ...s, ...change } : s,
      ),
    }));

  const chooseTemplate = (key: string, templateId: string) => {
    const known = options[templateId];
    updateService(key, {
      templateId,
      variables: known ? initialVariables(known) : {},
      features: {},
    });
    if (!templateId || known) return;
    templatesApi
      .options(templateId)
      .then((opts) => {
        setOptions((prev) => ({ ...prev, [templateId]: opts }));
        setDraft((d) => ({
          ...d,
          services: d.services.map((s) =>
            s.key === key && s.templateId === templateId
              ? { ...s, variables: initialVariables(opts) }
              : s,
          ),
        }));
      })
      .catch((err: unknown) =>
        setLoadError(
          err instanceof Error
            ? err.message
            : "Failed to load template options",
        ),
      );
  };

  const create = async () => {
    if (!request) return;
    setSubmitting(true);
    setSubmitError(null);
    setGithubNotConnected(false);
    try {
      const created = await applicationsApi.create(request);
      router.push(`/applications/${created.id}`);
    } catch (err: unknown) {
      setSubmitError(
        err instanceof Error ? err.message : "Failed to create application",
      );
      setGithubNotConnected(
        err instanceof ApiClientError && err.error === GITHUB_NOT_CONNECTED,
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
      <Navbar />
      <main className="flex-1 px-4 py-8 sm:px-6">
        <div className="mx-auto max-w-5xl space-y-6">
          <div className="pb-6 border-b border-zinc-800/80">
            <div className="flex items-center gap-2 text-xs font-mono text-zinc-400 mb-1">
              <Link href="/applications" className="hover:text-zinc-200">
                Applications
              </Link>
              <span>/</span>
              <span className="text-sky-400">New</span>
            </div>
            <h1 className="text-2xl font-bold tracking-tight">
              New Microservice Application
            </h1>
            <p className="text-xs text-zinc-400 mt-1">
              Several services in one repository, each in{" "}
              <code className="text-zinc-300">services/&lt;name&gt;</code>, with
              a shared <code className="text-zinc-300">compose.yaml</code> and
              one database or broker container per type.
            </p>
          </div>

          <ErrorAlert error={loadError} />

          {step === 1 ? (
            <>
              <Card className="border-zinc-800 bg-zinc-900/60">
                <CardContent className="p-6 grid gap-5 md:grid-cols-2">
                  <Input
                    label="Application Name"
                    placeholder="e.g. shop"
                    value={draft.name}
                    onChange={(e) =>
                      setDraft((d) => ({
                        ...d,
                        name: cleanName(e.target.value, 40),
                      }))
                    }
                    hint="Also the GitHub repository name"
                    required
                  />
                  <Input
                    label="Description"
                    placeholder="What the application does"
                    value={draft.description}
                    onChange={(e) =>
                      setDraft((d) => ({ ...d, description: e.target.value }))
                    }
                  />
                </CardContent>
              </Card>

              {draft.services.map((service, index) => (
                <ServiceCard
                  key={service.key}
                  index={index}
                  service={service}
                  siblings={draft.services}
                  templates={templates}
                  options={options[service.templateId]}
                  removable={draft.services.length > 1}
                  onChange={(change) => updateService(service.key, change)}
                  onTemplate={(id) => chooseTemplate(service.key, id)}
                  onRemove={() =>
                    setDraft((d) => ({
                      ...d,
                      services: d.services.filter((s) => s.key !== service.key),
                    }))
                  }
                />
              ))}

              <Button
                type="button"
                variant="outline"
                size="md"
                disabled={draft.services.length >= MAX_SERVICES}
                onClick={() =>
                  setDraft((d) => ({
                    ...d,
                    services: [...d.services, newService()],
                  }))
                }
              >
                <Plus className="h-3.5 w-3.5 mr-1.5" />
                Add Service
              </Button>
            </>
          ) : (
            <>
              <Card className="border-zinc-800 bg-zinc-900/60">
                <CardHeader className="pb-3 border-b border-zinc-800/80">
                  <CardTitle className="text-sm flex items-center gap-2">
                    <Boxes className="h-4 w-4 text-sky-400" />
                    {draft.name}
                  </CardTitle>
                  <CardDescription>
                    {draft.services.length} services in one repository
                  </CardDescription>
                </CardHeader>
                <CardContent className="p-4">
                  <ul className="divide-y divide-zinc-800 text-xs">
                    {draft.services.map((s, i) => (
                      <li
                        key={s.key}
                        className="flex flex-wrap items-center gap-2 py-2"
                      >
                        <span className="font-mono text-zinc-100 w-36 truncate">
                          {s.name}
                        </span>
                        <Badge variant="outline" size="sm">
                          :{portOf(i)}
                        </Badge>
                        <span className="text-zinc-400">
                          {templates.find((t) => t.id === s.templateId)?.name}
                        </span>
                        {options[s.templateId] &&
                          selectedLabels(options[s.templateId], s.features).map(
                            (f) => (
                              <Badge
                                key={`${f.group}-${f.option}`}
                                variant="info"
                                size="sm"
                              >
                                {f.option}
                              </Badge>
                            ),
                          )}
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>

              <Card className="border-zinc-800 bg-zinc-900/60">
                <CardHeader className="pb-3 border-b border-zinc-800/80">
                  <CardTitle className="text-sm flex items-center gap-2">
                    <Code2 className="h-4 w-4 text-sky-400" />
                    Repository Preview
                  </CardTitle>
                  <CardDescription>
                    The exact files that will be pushed.
                  </CardDescription>
                </CardHeader>
                <CardContent className="p-4">
                  <ServicePreview kind="application" request={request} />
                </CardContent>
              </Card>

              <ErrorAlert error={submitError} />
              {githubNotConnected && activeWorkspace && (
                <p className="text-xs text-amber-200">
                  <Link
                    href={`/workspaces/${activeWorkspace.id}`}
                    className="font-semibold underline underline-offset-2"
                  >
                    Open workspace settings to connect GitHub
                  </Link>
                </p>
              )}
            </>
          )}

          {step === 1 && errors.length > 0 && (
            <ul className="space-y-1 text-xs text-amber-300">
              {errors.map((e, i) => (
                <li key={`${i}-${e}`}>{e}</li>
              ))}
            </ul>
          )}

          <div className="flex items-center justify-between pt-6 border-t border-zinc-800/80">
            {step === 1 ? (
              <Link href="/applications">
                <Button type="button" variant="ghost" size="md">
                  Cancel
                </Button>
              </Link>
            ) : (
              <Button
                type="button"
                variant="outline"
                size="md"
                onClick={() => setStep(1)}
                disabled={submitting}
              >
                <ArrowLeft className="h-3.5 w-3.5 mr-1.5" />
                Edit Services
              </Button>
            )}
            {step === 1 ? (
              <Button
                type="button"
                variant="primary"
                size="md"
                disabled={errors.length > 0}
                onClick={() => setStep(2)}
              >
                Review & Preview
                <ArrowRight className="h-3.5 w-3.5 ml-1.5" />
              </Button>
            ) : (
              <Button
                type="button"
                variant="primary"
                size="md"
                isLoading={submitting}
                onClick={create}
              >
                <Rocket className="h-4 w-4 mr-2" />
                Create Application
              </Button>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

function ServiceCard({
  index,
  service,
  siblings,
  templates,
  options,
  removable,
  onChange,
  onTemplate,
  onRemove,
}: {
  index: number;
  service: ServiceDraft;
  siblings: ServiceDraft[];
  templates: AppTemplateResponse[];
  options: TemplateOptionsResponse | undefined;
  removable: boolean;
  onChange: (change: Partial<ServiceDraft>) => void;
  onTemplate: (templateId: string) => void;
  onRemove: () => void;
}) {
  const problems = serviceErrors(service, siblings, options);
  return (
    <Card className="border-zinc-800 bg-zinc-900/60">
      <CardHeader className="pb-3 border-b border-zinc-800/80">
        <div className="flex items-center justify-between gap-3">
          <CardTitle className="text-sm flex items-center gap-2">
            <Layers className="h-4 w-4 text-sky-400" />
            {service.name || `Service ${index + 1}`}
            <Badge variant="outline" size="sm">
              :{portOf(index)}
            </Badge>
          </CardTitle>
          {removable && (
            <button
              type="button"
              onClick={onRemove}
              aria-label={`Remove ${service.name || `service ${index + 1}`}`}
              className="text-zinc-500 hover:text-rose-400"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}
        </div>
      </CardHeader>
      <CardContent className="p-6 space-y-5">
        <div className="grid gap-5 md:grid-cols-3">
          <Input
            id={`${service.key}-name`}
            label="Service Name"
            placeholder="e.g. orders"
            value={service.name}
            onChange={(e) => onChange({ name: cleanName(e.target.value, 20) })}
            required
          />
          <Select
            id={`${service.key}-template`}
            label="Template"
            value={service.templateId}
            onChange={(e) => onTemplate(e.target.value)}
            options={[
              { value: "", label: "Choose a template" },
              ...templates.map((t) => ({
                value: t.id,
                label: templateLabel(t),
              })),
            ]}
            required
          />
          <Input
            id={`${service.key}-description`}
            label="Description"
            value={service.description}
            onChange={(e) => onChange({ description: e.target.value })}
          />
        </div>
        {options && (
          <TemplateOptionsForm
            options={options}
            variables={service.variables}
            features={service.features}
            onVariablesChange={(variables) => onChange({ variables })}
            onFeaturesChange={(features) => onChange({ features })}
          />
        )}
        {service.name && problems.length > 0 && (
          <ul className="space-y-1 text-xs text-amber-300">
            {problems.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
