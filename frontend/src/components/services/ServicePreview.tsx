"use client";

import React, { useEffect, useMemo, useState } from "react";
import {
  ChevronDown,
  ChevronRight,
  FileCode2,
  Folder,
  Loader2,
  RefreshCw,
} from "lucide-react";
import { Badge } from "@/components/ui/Badge";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { applicationsApi } from "@/lib/api/applications";
import { servicesApi } from "@/lib/api/services";
import {
  TreeNode,
  buildTree,
  defaultFile,
  featureOf,
  formatBytes,
} from "@/lib/previewTree";
import { cn } from "@/lib/utils";
import type {
  AppServiceCreationDto,
  ApplicationCreationDto,
  PreviewFileResponse,
  ServicePreviewResponse,
} from "@/types/api";

const DEBOUNCE_MS = 300;

type PreviewSource =
  | { kind: "service"; request: AppServiceCreationDto | null }
  | { kind: "application"; request: ApplicationCreationDto | null };

const previewApis = {
  service: servicesApi,
  application: applicationsApi,
};

interface PreviewState {
  key: string;
  preview: ServicePreviewResponse | null;
  error: string | null;
}

interface FileState {
  file: PreviewFileResponse | null;
  error: string | null;
}

function errorMessage(err: unknown, fallback: string): string {
  return err instanceof Error ? err.message : fallback;
}

export function ServicePreview(source: PreviewSource) {
  const { request } = source;
  const previewApi = previewApis[source.kind];
  const requestKey = request ? JSON.stringify(request) : null;
  const [state, setState] = useState<PreviewState | null>(null);
  const [reload, setReload] = useState(0);
  const [selection, setSelection] = useState<{
    previewId: string;
    path: string;
  } | null>(null);
  const [files, setFiles] = useState<Record<string, FileState>>({});
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (!requestKey) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      previewApi
        .preview(JSON.parse(requestKey), controller.signal)
        .then((preview) => setState({ key: requestKey, preview, error: null }))
        .catch((err: unknown) => {
          if (controller.signal.aborted) return;
          setState({
            key: requestKey,
            preview: null,
            error: errorMessage(err, "Failed to render the preview"),
          });
        });
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [requestKey, reload, previewApi]);

  const current = state && state.key === requestKey ? state : null;
  const preview = current?.preview ?? null;

  const selectedPath = preview
    ? selection?.previewId === preview.previewId
      ? selection.path
      : defaultFile(preview.files)
    : null;
  const fileKey =
    preview && selectedPath ? `${preview.previewId}:${selectedPath}` : null;
  const selectedFile = fileKey ? files[fileKey] : undefined;

  useEffect(() => {
    if (!preview || !selectedPath || !fileKey || files[fileKey]) return;
    const controller = new AbortController();
    previewApi
      .previewFile(preview.previewId, selectedPath, controller.signal)
      .then((file) =>
        setFiles((prev) => ({ ...prev, [fileKey]: { file, error: null } })),
      )
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setFiles((prev) => ({
          ...prev,
          [fileKey]: {
            file: null,
            error: errorMessage(err, "Failed to load file"),
          },
        }));
      });
    return () => controller.abort();
  }, [preview, selectedPath, fileKey, files, previewApi]);

  const tree = useMemo(
    () => (preview ? buildTree(preview.files) : null),
    [preview],
  );
  const featureFiles = preview
    ? preview.files.filter((f) => featureOf(f.origin)).length
    : 0;

  if (!request) {
    return (
      <p className="text-xs text-zinc-500">
        Complete the service details to preview the generated repository.
      </p>
    );
  }

  if (current?.error) {
    return (
      <div className="space-y-3">
        <ErrorAlert error={current.error} />
        <button
          type="button"
          onClick={() => setReload((r) => r + 1)}
          className="inline-flex items-center gap-1.5 text-xs text-sky-400 hover:text-sky-300"
        >
          <RefreshCw className="h-3.5 w-3.5" /> Try again
        </button>
      </div>
    );
  }

  if (!preview || !tree) {
    return (
      <div className="flex items-center gap-2 text-xs text-zinc-400 py-6">
        <Loader2 className="h-3.5 w-3.5 animate-spin" />
        Rendering the repository preview...
      </div>
    );
  }

  const toggle = (path: string) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return next;
    });

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-zinc-400">
        <span>
          {preview.fileCount} files · {formatBytes(preview.totalBytes)}
        </span>
        {featureFiles > 0 && (
          <span className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-emerald-400" />
            {featureFiles} added by selected features
          </span>
        )}
        <span className="text-zinc-500">
          {preview.templateName} v{preview.templateVersion}
        </span>
      </div>

      <div className="grid gap-3 lg:grid-cols-[minmax(220px,300px)_1fr]">
        <div className="max-h-[480px] overflow-auto rounded-lg border border-zinc-800 bg-zinc-950/60 p-2 text-xs">
          <TreeLevel
            nodes={tree.children}
            depth={0}
            collapsed={collapsed}
            selectedPath={selectedPath}
            onToggle={toggle}
            onSelect={(path) =>
              setSelection({ previewId: preview.previewId, path })
            }
          />
        </div>

        <FileViewer path={selectedPath} state={selectedFile} />
      </div>
    </div>
  );
}

function TreeLevel({
  nodes,
  depth,
  collapsed,
  selectedPath,
  onToggle,
  onSelect,
}: {
  nodes: TreeNode[];
  depth: number;
  collapsed: Set<string>;
  selectedPath: string | null;
  onToggle: (path: string) => void;
  onSelect: (path: string) => void;
}) {
  return (
    <ul role={depth === 0 ? "tree" : "group"}>
      {nodes.map((node) => {
        const indent = { paddingLeft: `${depth * 12 + 4}px` };
        if (!node.file) {
          const open = !collapsed.has(node.path);
          return (
            <li
              key={node.path}
              role="treeitem"
              aria-expanded={open}
              aria-selected={false}
            >
              <button
                type="button"
                onClick={() => onToggle(node.path)}
                style={indent}
                className="flex w-full items-center gap-1 rounded px-1 py-0.5 text-left text-zinc-300 hover:bg-zinc-900"
              >
                {open ? (
                  <ChevronDown className="h-3 w-3 shrink-0 text-zinc-500" />
                ) : (
                  <ChevronRight className="h-3 w-3 shrink-0 text-zinc-500" />
                )}
                <Folder className="h-3.5 w-3.5 shrink-0 text-sky-400/70" />
                <span className="truncate">{node.name}</span>
              </button>
              {open && (
                <TreeLevel
                  nodes={node.children}
                  depth={depth + 1}
                  collapsed={collapsed}
                  selectedPath={selectedPath}
                  onToggle={onToggle}
                  onSelect={onSelect}
                />
              )}
            </li>
          );
        }
        const feature = featureOf(node.file.origin);
        const selected = node.path === selectedPath;
        return (
          <li key={node.path} role="treeitem" aria-selected={selected}>
            <button
              type="button"
              onClick={() => onSelect(node.path)}
              style={{ paddingLeft: `${depth * 12 + 20}px` }}
              title={feature ? `Added by ${feature}` : undefined}
              className={cn(
                "flex w-full items-center gap-1.5 rounded px-1 py-0.5 text-left hover:bg-zinc-900",
                selected ? "bg-sky-950/40 text-sky-200" : "text-zinc-400",
              )}
            >
              <FileCode2 className="h-3.5 w-3.5 shrink-0 text-zinc-500" />
              <span className="truncate">{node.name}</span>
              {feature && (
                <span className="ml-auto h-2 w-2 shrink-0 rounded-full bg-emerald-400" />
              )}
            </button>
          </li>
        );
      })}
    </ul>
  );
}

function FileViewer({
  path,
  state,
}: {
  path: string | null;
  state: FileState | undefined;
}) {
  const file = state?.file ?? null;
  const feature = file ? featureOf(file.origin) : null;
  const lines = file?.content != null ? file.content.split("\n") : null;

  return (
    <div className="min-w-0 rounded-lg border border-zinc-800 bg-zinc-950/60">
      <div className="flex items-center justify-between gap-3 border-b border-zinc-800 px-3 py-2">
        <span className="truncate font-mono text-[11px] text-zinc-300">
          {path ?? "No file selected"}
        </span>
        {file && (
          <div className="flex shrink-0 items-center gap-2">
            <Badge variant={feature ? "success" : "outline"} size="sm">
              {feature ?? (file.origin === "devforge" ? "DevForge" : "base")}
            </Badge>
            <span className="text-[10px] text-zinc-500">
              {formatBytes(file.size)}
            </span>
          </div>
        )}
      </div>
      <div className="max-h-[440px] overflow-auto">
        {state?.error ? (
          <div className="p-3">
            <ErrorAlert error={state.error} />
          </div>
        ) : !file ? (
          path && (
            <div className="flex items-center gap-2 p-3 text-xs text-zinc-500">
              <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading...
            </div>
          )
        ) : file.binary ? (
          <p className="p-3 text-xs text-zinc-500">Binary file, not shown.</p>
        ) : file.omitted || lines === null ? (
          <p className="p-3 text-xs text-zinc-500">
            File is too large to preview ({formatBytes(file.size)}).
          </p>
        ) : (
          <pre className="text-[11px] leading-5 text-zinc-300">
            <code className="grid grid-cols-[auto_1fr]">
              {lines.map((line, i) => (
                <React.Fragment key={i}>
                  <span className="select-none border-r border-zinc-800 pl-3 pr-3 text-right text-zinc-600">
                    {i + 1}
                  </span>
                  <span className="whitespace-pre pl-3 pr-4">
                    {line || " "}
                  </span>
                </React.Fragment>
              ))}
            </code>
          </pre>
        )}
      </div>
    </div>
  );
}
