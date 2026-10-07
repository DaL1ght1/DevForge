import React from "react";
import { cn } from "@/lib/utils";
import { AlertTriangle } from "lucide-react";
import { ApiClientError } from "@/lib/api/client";

export interface ErrorAlertProps {
  error: Error | string | null | undefined;
  className?: string;
  title?: string;
}

export function ErrorAlert({ error, className, title }: ErrorAlertProps) {
  if (!error) return null;

  const isClientError = error instanceof ApiClientError;
  const message = typeof error === "string" ? error : error.message;
  const validationErrors = isClientError ? error.validationErrors : undefined;

  return (
    <div
      className={cn(
        "p-4 rounded-md border border-red-900/60 bg-red-950/40 text-red-200 text-xs space-y-2",
        className,
      )}
    >
      <div className="flex items-start gap-2.5">
        <AlertTriangle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
        <div className="flex-1 space-y-1">
          <p className="font-semibold text-red-300">
            {title ||
              (isClientError ? error.error || "Request Failed" : "Error")}
          </p>
          <p className="text-red-300/90 leading-relaxed">{message}</p>
        </div>
      </div>

      {validationErrors && Object.keys(validationErrors).length > 0 && (
        <div className="mt-2 pt-2 border-t border-red-900/50 pl-6 space-y-1">
          <p className="font-medium text-red-300">Validation issues:</p>
          <ul className="list-disc list-inside space-y-0.5 text-red-300/80">
            {Object.entries(validationErrors).map(([field, msg]) => (
              <li key={field}>
                <span className="font-mono text-[11px] text-red-200">
                  {field}
                </span>
                : {msg}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
