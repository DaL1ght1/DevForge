"use client";

import React, { useState, useEffect, useMemo } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  CheckCircle2,
  Code2,
  Layers,
  Loader2,
  Package,
  Server,
  Settings,
  Wrench,
} from "lucide-react";
import { Navbar } from "@/components/layout/Navbar";
import { StepIndicator, StepItem } from "@/components/common/StepIndicator";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { servicesApi } from "@/lib/api/services";
import { templatesApi } from "@/lib/api/templates";
import { AppTemplateResponse } from "@/types/api";
import { cn } from "@/lib/utils";

function formatIdentifier(text?: string): string {
  if (!text) return "";
  if (text.toUpperCase() === "NEXT_JS") return "Next.js";
  if (text.toUpperCase() === "SPRING_BOOT") return "Spring Boot";
  if (text.toUpperCase() === "GO_MODULES") return "Go Modules";
  return text
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(" ");
}

export default function NewServicePage() {
  const router = useRouter();
  const [currentStep, setCurrentStep] = useState(1);
  const [allTemplates, setAllTemplates] = useState<AppTemplateResponse[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [selectedLanguage, setSelectedLanguage] = useState<string>("");
  const [selectedFramework, setSelectedFramework] = useState<string>("");
  const [selectedBuildTool, setSelectedBuildTool] = useState<string>("");
  const [serviceName, setServiceName] = useState("");
  const [description, setDescription] = useState("");
  const [databaseType, setDatabaseType] = useState("");
  const [isProvisioning, setIsProvisioning] = useState(false);
  const [provisionError, setProvisionError] = useState<string | null>(null);

  // Load the template catalog. State is only set after an await, never
  // synchronously in the effect body. `reloadKey` re-runs it on retry.
  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const page = await templatesApi.list(0, 100);
        if (cancelled) return;

        const list = page.content || [];
        setAllTemplates(list);
        setLoadError(null);

        if (list.length > 0) {
          const first = list[0];
          if (first.language) setSelectedLanguage(first.language);
          if (first.framework) setSelectedFramework(first.framework);
          if (first.buildTool) setSelectedBuildTool(first.buildTool);
          if (first.databaseType) setDatabaseType(first.databaseType);
        }
      } catch (err: unknown) {
        if (cancelled) return;
        const msg =
          err instanceof Error
            ? err.message
            : "Failed to load templates from catalog";
        setLoadError(msg);
      } finally {
        if (!cancelled) setLoadingTemplates(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const handleRetry = () => {
    setLoadingTemplates(true);
    setLoadError(null);
    setReloadKey((k) => k + 1);
  };

  const availableLanguages = useMemo(() => {
    const langs = new Set<string>();
    for (const t of allTemplates) {
      if (t.language) langs.add(t.language);
    }
    return Array.from(langs);
  }, [allTemplates]);

  const languageTemplates = useMemo(() => {
    if (!selectedLanguage) return allTemplates;
    return allTemplates.filter((t) => t.language === selectedLanguage);
  }, [allTemplates, selectedLanguage]);

  const availableFrameworks = useMemo(() => {
    const fws = new Set<string>();
    for (const t of languageTemplates) {
      if (t.framework) fws.add(t.framework);
    }
    return Array.from(fws);
  }, [languageTemplates]);

  const frameworkTemplates = useMemo(() => {
    if (!selectedFramework) return languageTemplates;
    return languageTemplates.filter((t) => t.framework === selectedFramework);
  }, [languageTemplates, selectedFramework]);

  const availableBuildTools = useMemo(() => {
    const tools = new Set<string>();
    for (const t of frameworkTemplates) {
      if (t.buildTool) tools.add(t.buildTool);
    }
    return Array.from(tools);
  }, [frameworkTemplates]);

  const matchedTemplate = useMemo(() => {
    return (
      frameworkTemplates.find((t) => t.buildTool === selectedBuildTool) ||
      frameworkTemplates[0] ||
      languageTemplates[0] ||
      allTemplates[0] ||
      null
    );
  }, [frameworkTemplates, selectedBuildTool, languageTemplates, allTemplates]);

  const handleSelectLanguage = async (lang: string) => {
    setSelectedLanguage(lang);
    try {
      const res = await templatesApi.getByLanguage(lang, 0, 100);
      const list = res.content || [];
      if (list.length > 0) {
        const firstFw = list[0].framework || "";
        setSelectedFramework(firstFw);
        const tools = Array.from(
          new Set(
            list.filter((t) => t.framework === firstFw).map((t) => t.buildTool),
          ),
        ).filter(Boolean);
        if (tools.length > 0 && tools[0]) {
          setSelectedBuildTool(tools[0]);
        }
        if (list[0].databaseType && !databaseType) {
          setDatabaseType(list[0].databaseType);
        }
      }
    } catch (e) {
      console.warn("Backend by-languages query fallback to local cache", e);
      const matchingFws = allTemplates.filter((t) => t.language === lang);
      if (matchingFws.length > 0) {
        setSelectedFramework(matchingFws[0].framework || "");
        setSelectedBuildTool(matchingFws[0].buildTool || "");
        if (matchingFws[0].databaseType && !databaseType) {
          setDatabaseType(matchingFws[0].databaseType);
        }
      }
    }
  };

  const handleSelectFramework = async (fw: string) => {
    setSelectedFramework(fw);
    try {
      const res = await templatesApi.getByFramework(fw, 0, 100);
      const list = res.content || [];
      if (list.length > 0) {
        const tools = Array.from(new Set(list.map((t) => t.buildTool))).filter(
          Boolean,
        );
        if (tools.length > 0 && tools[0]) {
          setSelectedBuildTool(tools[0]);
        }
        if (list[0].databaseType && !databaseType) {
          setDatabaseType(list[0].databaseType);
        }
      }
    } catch (e) {
      console.warn("Backend by-framework query fallback to local cache", e);
      const matchingTools = languageTemplates.filter((t) => t.framework === fw);
      if (matchingTools.length > 0) {
        setSelectedBuildTool(matchingTools[0].buildTool || "");
        if (matchingTools[0].databaseType && !databaseType) {
          setDatabaseType(matchingTools[0].databaseType);
        }
      }
    }
  };

  const handleSelectBuildTool = async (tool: string) => {
    setSelectedBuildTool(tool);
    try {
      const res = await templatesApi.filter({
        framework: selectedFramework,
        buildTool: tool,
      });
      const list = res.content || [];
      if (list.length > 0 && list[0].databaseType && !databaseType) {
        setDatabaseType(list[0].databaseType);
      }
    } catch (e) {
      console.warn("Backend filter query fallback to local cache", e);
    }
  };

  const handleNameChange = (val: string) => {
    const clean = val
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, "")
      .slice(0, 20);
    setServiceName(clean);
  };

  const steps: StepItem[] = [
    {
      id: 1,
      label: "Language",
      summary: selectedLanguage
        ? formatIdentifier(selectedLanguage)
        : undefined,
      description: "Runtime ecosystem",
    },
    {
      id: 2,
      label: "Framework",
      summary: selectedFramework
        ? formatIdentifier(selectedFramework)
        : undefined,
      description: "Application engine",
    },
    {
      id: 3,
      label: "Build Tool",
      summary: selectedBuildTool
        ? formatIdentifier(selectedBuildTool)
        : undefined,
      description: "Packaging lifecycle",
    },
    {
      id: 4,
      label: "Specifications",
      summary: serviceName || "Name & Database",
      description: "Service metadata",
    },
    {
      id: 5,
      label: "Scaffold & Deploy",
      summary: "Blueprint Review",
      description: "Provision service",
    },
  ];

  const handleProvision = async () => {
    if (!matchedTemplate) {
      setProvisionError(
        "No valid backend template selected. Please choose a template from the catalog.",
      );
      return;
    }

    setProvisionError(null);
    setIsProvisioning(true);

    try {
      const versionIdToSend =
        matchedTemplate.templateVersionId || matchedTemplate.id;

      const created = await servicesApi.create({
        name: serviceName,
        description: description || undefined,
        templateVersionId: versionIdToSend,
        databaseType:
          databaseType || matchedTemplate.databaseType || "POSTGRESQL",
      });

      if (created?.id) {
        router.push(`/services/${created.id}`);
      } else {
        router.push("/services");
      }
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to provision service";
      setProvisionError(message);
    } finally {
      setIsProvisioning(false);
    }
  };

  const canProceedToNext = () => {
    if (currentStep === 1) return !!selectedLanguage;
    if (currentStep === 2) return !!selectedFramework;
    if (currentStep === 3) return !!selectedBuildTool;
    if (currentStep === 4) {
      return (
        serviceName.trim().length >= 3 &&
        serviceName.trim().length <= 20 &&
        /^[a-z0-9-]{3,20}$/.test(serviceName) &&
        databaseType.trim().length > 0
      );
    }
    return true;
  };

  if (loadingTemplates) {
    return (
      <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
        <Navbar />
        <main className="flex-1 flex flex-col items-center justify-center space-y-3">
          <Loader2 className="h-8 w-8 animate-spin text-sky-400" />
          <p className="text-xs text-zinc-400">
            Loading templates from catalog...
          </p>
        </main>
      </div>
    );
  }

  if (allTemplates.length === 0) {
    return (
      <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
        <Navbar />
        <main className="flex-1 px-4 py-12 sm:px-6 flex items-center justify-center">
          <Card className="max-w-md w-full border-zinc-800 bg-zinc-900/60 p-6 text-center space-y-4">
            <Package className="h-10 w-10 text-amber-400 mx-auto" />
            <h2 className="text-base font-semibold text-zinc-100">
              No Templates Found
            </h2>
            <p className="text-xs text-zinc-400">
              There are no application templates registered in your DevForge
              backend. Please seed templates into your backend database before
              scaffolding a service.
            </p>
            {loadError && <ErrorAlert error={loadError} />}
            <div className="pt-2 flex justify-center gap-3">
              <Button variant="outline" size="sm" onClick={handleRetry}>
                Retry
              </Button>
              <Link href="/services">
                <Button variant="primary" size="sm">
                  Back to Services
                </Button>
              </Link>
            </div>
          </Card>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col">
      <Navbar />

      <main className="flex-1 px-4 py-8 sm:px-6">
        <div className="mx-auto max-w-5xl space-y-8">
          {/* Header */}
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-6 border-b border-zinc-800/80 gap-4">
            <div>
              <div className="flex items-center gap-2 text-xs font-mono text-zinc-400 mb-1">
                <Link
                  href="/services"
                  className="hover:text-zinc-200 transition-colors"
                >
                  Services
                </Link>
                <span>/</span>
                <span className="text-sky-400">Initialize</span>
              </div>
              <h1 className="text-2xl font-bold tracking-tight text-zinc-100">
                Service Scaffolding Wizard
              </h1>
              <p className="text-xs text-zinc-400 mt-1 max-w-xl">
                Configure runtime language, application framework, build
                lifecycle, and storage specifications.
              </p>
            </div>

            <Link href="/services">
              <Button variant="outline" size="sm">
                <ArrowLeft className="h-3.5 w-3.5 mr-1" /> Back to Catalog
              </Button>
            </Link>
          </div>
          <StepIndicator
            steps={steps}
            currentStep={currentStep}
            onStepClick={(id) => setCurrentStep(id)}
          />
          <div className="min-h-95">
            {currentStep === 1 && (
              <div className="space-y-6 animate-in fade-in duration-150">
                <div>
                  <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
                    <Code2 className="h-4 w-4 text-sky-400" />
                    Level 1: Choose Programming Language
                  </h2>
                  <p className="text-xs text-zinc-400 mt-1">
                    Select a supported runtime language available in your
                    backend template catalog.
                  </p>
                </div>

                <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3">
                  {availableLanguages.map((lang) => {
                    const isSelected = selectedLanguage === lang;
                    const matchingCount = allTemplates.filter(
                      (t) => t.language === lang,
                    ).length;
                    return (
                      <button
                        key={lang}
                        type="button"
                        onClick={() => handleSelectLanguage(lang)}
                        onDoubleClick={async () => {
                          await handleSelectLanguage(lang);
                          setCurrentStep(2);
                        }}
                        className={cn(
                          "flex flex-col text-left p-5 rounded-xl border transition-all relative focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500",
                          isSelected
                            ? "border-sky-500 bg-sky-950/20 shadow-[0_0_20px_rgba(14,165,233,0.1)]"
                            : "border-zinc-800 bg-zinc-900/60 hover:border-zinc-700 hover:bg-zinc-900",
                        )}
                      >
                        <div className="flex items-start justify-between w-full mb-3">
                          <div className="flex items-center gap-2.5">
                            <div
                              className={cn(
                                "flex h-9 w-9 items-center justify-center rounded-lg border font-mono font-bold text-sm",
                                isSelected
                                  ? "border-sky-500/60 bg-sky-500/20 text-sky-300"
                                  : "border-zinc-700 bg-zinc-800 text-zinc-300",
                              )}
                            >
                              {lang.slice(0, 2)}
                            </div>
                            <div>
                              <div className="text-sm font-semibold text-zinc-100">
                                {formatIdentifier(lang)}
                              </div>
                              <div className="text-xs text-zinc-400 font-mono">
                                {matchingCount}{" "}
                                {matchingCount === 1 ? "template" : "templates"}
                              </div>
                            </div>
                          </div>

                          <Badge
                            variant={isSelected ? "info" : "outline"}
                            size="sm"
                          >
                            {lang}
                          </Badge>
                        </div>

                        <div className="mt-4 pt-3 border-t border-zinc-800/80 flex items-center justify-between text-[11px] text-zinc-500">
                          <span>Runtime Environment</span>
                          {isSelected && (
                            <span className="flex items-center gap-1 text-sky-400 font-medium">
                              <Check className="h-3 w-3 stroke-3" /> Selected
                            </span>
                          )}
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
            {currentStep === 2 && (
              <div className="space-y-6 animate-in fade-in duration-150">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
                      <Layers className="h-4 w-4 text-sky-400" />
                      Level 2: Select Application Framework
                    </h2>
                    <p className="text-xs text-zinc-400 mt-1">
                      Available frameworks for{" "}
                      <span className="font-semibold text-zinc-200">
                        {formatIdentifier(selectedLanguage)}
                      </span>
                      .
                    </p>
                  </div>
                  <Badge variant="outline" size="md">
                    {formatIdentifier(selectedLanguage)}
                  </Badge>
                </div>

                <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3">
                  {availableFrameworks.map((fw) => {
                    const isSelected = selectedFramework === fw;
                    const count = languageTemplates.filter(
                      (t) => t.framework === fw,
                    ).length;
                    return (
                      <button
                        key={fw}
                        type="button"
                        onClick={() => handleSelectFramework(fw)}
                        onDoubleClick={async () => {
                          await handleSelectFramework(fw);
                          setCurrentStep(3);
                        }}
                        className={cn(
                          "flex flex-col text-left p-5 rounded-xl border transition-all relative focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500",
                          isSelected
                            ? "border-sky-500 bg-sky-950/20 shadow-[0_0_20px_rgba(14,165,233,0.1)]"
                            : "border-zinc-800 bg-zinc-900/60 hover:border-zinc-700 hover:bg-zinc-900",
                        )}
                      >
                        <div className="flex items-start justify-between w-full mb-2">
                          <div className="text-sm font-semibold text-zinc-100">
                            {formatIdentifier(fw)}
                          </div>
                          <Badge
                            variant={isSelected ? "info" : "outline"}
                            size="sm"
                          >
                            {fw}
                          </Badge>
                        </div>

                        <p className="text-xs text-zinc-400 leading-relaxed flex-1 mt-1">
                          Catalog entry for {formatIdentifier(fw)} architecture.
                        </p>

                        <div className="mt-4 pt-3 border-t border-zinc-800/80 flex items-center justify-between text-[11px] text-zinc-500">
                          <span className="font-mono">
                            {count} variant{count === 1 ? "" : "s"}
                          </span>
                          {isSelected ? (
                            <span className="flex items-center gap-1 text-sky-400 font-medium">
                              <Check className="h-3 w-3 stroke-3" /> Active
                            </span>
                          ) : (
                            <span>Select</span>
                          )}
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            {currentStep === 3 && (
              <div className="space-y-6 animate-in fade-in duration-150">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
                      <Wrench className="h-4 w-4 text-sky-400" />
                      Level 3: Choose Build & Packaging Tool
                    </h2>
                    <p className="text-xs text-zinc-400 mt-1">
                      Packaging lifecycle and dependency resolution for{" "}
                      <span className="font-semibold text-zinc-200">
                        {formatIdentifier(selectedFramework)}
                      </span>
                      .
                    </p>
                  </div>
                  <Badge variant="outline" size="md">
                    {formatIdentifier(selectedFramework)}
                  </Badge>
                </div>

                <div className="grid gap-4 sm:grid-cols-2 md:grid-cols-3">
                  {availableBuildTools.map((tool) => {
                    const isSelected = selectedBuildTool === tool;
                    const templateForTool = frameworkTemplates.find(
                      (t) => t.buildTool === tool,
                    );
                    return (
                      <button
                        key={tool}
                        type="button"
                        onClick={() => handleSelectBuildTool(tool)}
                        onDoubleClick={async () => {
                          await handleSelectBuildTool(tool);
                          setCurrentStep(4);
                        }}
                        className={cn(
                          "flex flex-col text-left p-5 rounded-xl border transition-all relative focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500",
                          isSelected
                            ? "border-sky-500 bg-sky-950/20 shadow-[0_0_20px_rgba(14,165,233,0.1)]"
                            : "border-zinc-800 bg-zinc-900/60 hover:border-zinc-700 hover:bg-zinc-900",
                        )}
                      >
                        <div className="flex items-start justify-between w-full mb-2">
                          <div className="text-sm font-semibold text-zinc-100">
                            {formatIdentifier(tool)}
                          </div>
                          <Badge
                            variant={isSelected ? "info" : "outline"}
                            size="sm"
                          >
                            {tool}
                          </Badge>
                        </div>

                        {templateForTool && (
                          <div className="font-mono text-[11px] text-sky-400 bg-zinc-950/60 px-2 py-1 rounded border border-zinc-800 inline-block mb-3 truncate">
                            Template: {templateForTool.name}
                          </div>
                        )}

                        <div className="mt-4 pt-3 border-t border-zinc-800/80 flex items-center justify-between text-[11px] text-zinc-500">
                          <span>Build Toolchain</span>
                          {isSelected && (
                            <span className="flex items-center gap-1 text-sky-400 font-medium">
                              <Check className="h-3 w-3 stroke-3" /> Active
                            </span>
                          )}
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
            {currentStep === 4 && (
              <div className="space-y-6 animate-in fade-in duration-150">
                <div>
                  <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
                    <Settings className="h-4 w-4 text-sky-400" />
                    Level 4: Service Architecture & Configuration
                  </h2>
                  <p className="text-xs text-zinc-400 mt-1">
                    Set service identifiers, storage adapter, and service
                    description.
                  </p>
                </div>

                <Card className="border-zinc-800 bg-zinc-900/60">
                  <CardContent className="p-6 space-y-5">
                    <div className="grid gap-5 md:grid-cols-2">
                      <Input
                        label="Service Name"
                        placeholder="e.g. order-service"
                        value={serviceName}
                        onChange={(e) => handleNameChange(e.target.value)}
                        hint="3-20 characters: lowercase letters, numbers, and hyphens"
                        pattern="^[a-z0-9-]{3,20}$"
                        required
                      />

                      <Input
                        label="Database Storage"
                        placeholder="e.g. POSTGRESQL, MYSQL, NONE"
                        value={databaseType}
                        onChange={(e) =>
                          setDatabaseType(e.target.value.toUpperCase())
                        }
                        hint="Target database engine"
                        required
                      />
                    </div>

                    <Input
                      label="Service Description"
                      placeholder="Brief summary of service responsibilities and domain logic"
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                    />
                  </CardContent>
                </Card>
              </div>
            )}
            {currentStep === 5 && (
              <div className="space-y-6 animate-in fade-in duration-150">
                <div>
                  <h2 className="text-base font-semibold text-zinc-100 flex items-center gap-2">
                    <CheckCircle2 className="h-4 w-4 text-emerald-400" />
                    Level 5: Review & Scaffold Blueprint
                  </h2>
                  <p className="text-xs text-zinc-400 mt-1">
                    Confirm configuration parameters before provisioning git
                    repository and service catalog entry.
                  </p>
                </div>

                <ErrorAlert error={provisionError} />

                <Card className="border-zinc-800 bg-zinc-900/60">
                  <CardHeader className="pb-3 border-b border-zinc-800/80">
                    <CardTitle className="text-sm text-zinc-100 flex items-center gap-2">
                      <Server className="h-4 w-4 text-sky-400" />
                      Service Blueprint Specification
                    </CardTitle>
                    <CardDescription>
                      Parameters resolved dynamically from backend template
                      catalog.
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="p-6 space-y-4">
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs">
                      <div className="p-3 rounded-lg border border-zinc-800 bg-zinc-950/60">
                        <span className="text-[10px] uppercase tracking-wider text-zinc-500 block mb-1">
                          Service Name
                        </span>
                        <span className="font-mono font-semibold text-zinc-100 text-sm">
                          {serviceName}
                        </span>
                      </div>

                      <div className="p-3 rounded-lg border border-zinc-800 bg-zinc-950/60">
                        <span className="text-[10px] uppercase tracking-wider text-zinc-500 block mb-1">
                          Template
                        </span>
                        <span className="font-mono text-sky-400 truncate block">
                          {matchedTemplate?.name || "Template unavailable"}
                        </span>
                      </div>

                      <div className="p-3 rounded-lg border border-zinc-800 bg-zinc-950/60">
                        <span className="text-[10px] uppercase tracking-wider text-zinc-500 block mb-1">
                          Language
                        </span>
                        <span className="font-medium text-zinc-100">
                          {formatIdentifier(selectedLanguage)}
                        </span>
                      </div>

                      <div className="p-3 rounded-lg border border-zinc-800 bg-zinc-950/60">
                        <span className="text-[10px] uppercase tracking-wider text-zinc-500 block mb-1">
                          Framework
                        </span>
                        <span className="font-medium text-zinc-100">
                          {formatIdentifier(selectedFramework)}
                        </span>
                      </div>

                      <div className="p-3 rounded-lg border border-zinc-800 bg-zinc-950/60">
                        <span className="text-[10px] uppercase tracking-wider text-zinc-500 block mb-1">
                          Build Tool
                        </span>
                        <span className="font-mono text-zinc-200">
                          {formatIdentifier(selectedBuildTool)}
                        </span>
                      </div>

                      <div className="p-3 rounded-lg border border-zinc-800 bg-zinc-950/60">
                        <span className="text-[10px] uppercase tracking-wider text-zinc-500 block mb-1">
                          Database
                        </span>
                        <span className="font-mono text-zinc-200">
                          {databaseType}
                        </span>
                      </div>
                    </div>

                    {description && (
                      <div className="p-3 rounded-lg border border-zinc-800 bg-zinc-950/60 text-xs">
                        <span className="text-[10px] uppercase tracking-wider text-zinc-500 block mb-1">
                          Description
                        </span>
                        <p className="text-zinc-300">{description}</p>
                      </div>
                    )}
                    <div className="rounded-lg border border-sky-900/50 bg-sky-950/20 p-4">
                      <div className="flex items-center justify-between gap-3">
                        <span className="text-[10px] font-mono uppercase tracking-wider text-sky-300">
                          Backend template manifest
                        </span>
                        <Badge variant="outline" size="sm">
                          Live catalog
                        </Badge>
                      </div>
                      <pre className="mt-3 overflow-x-auto whitespace-pre-wrap rounded-md border border-zinc-800 bg-zinc-950/70 p-3 text-[11px] leading-relaxed text-zinc-300">{`name: ${matchedTemplate?.name || "unavailable"}\nlanguage: ${matchedTemplate?.language || "unavailable"}\nframework: ${matchedTemplate?.framework || "unavailable"}\nbuildTool: ${matchedTemplate?.buildTool || "unavailable"}\n                          databaseType: ${databaseType || matchedTemplate?.databaseType || "unavailable"}`}</pre>
                      <p className="mt-2 text-[11px] text-zinc-500">
                        This preview contains the manifest fields exposed by the
                        backend catalog. No client-side template data is used.
                      </p>
                    </div>
                  </CardContent>
                </Card>
              </div>
            )}
          </div>
          <div className="flex items-center justify-between pt-6 border-t border-zinc-800/80">
            <div>
              {currentStep > 1 ? (
                <Button
                  type="button"
                  variant="outline"
                  size="md"
                  onClick={() => setCurrentStep(currentStep - 1)}
                  disabled={isProvisioning}
                >
                  <ArrowLeft className="h-3.5 w-3.5 mr-1.5" />
                  Previous Level
                </Button>
              ) : (
                <Link href="/services">
                  <Button type="button" variant="ghost" size="md">
                    Cancel
                  </Button>
                </Link>
              )}
            </div>

            <div>
              {currentStep < 5 ? (
                <Button
                  type="button"
                  variant="primary"
                  size="md"
                  disabled={!canProceedToNext()}
                  onClick={() => setCurrentStep(currentStep + 1)}
                >
                  Continue to Level {currentStep + 1}
                  <ArrowRight className="h-3.5 w-3.5 ml-1.5" />
                </Button>
              ) : (
                <Button
                  type="button"
                  variant="primary"
                  size="md"
                  isLoading={isProvisioning}
                  onClick={handleProvision}
                  className="bg-sky-600 hover:bg-sky-500 text-white"
                >
                  <Package className="h-4 w-4 mr-2" />
                  Scaffold & Provision Service
                </Button>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
