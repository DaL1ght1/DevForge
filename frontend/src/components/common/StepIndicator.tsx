"use client";

import React from "react";
import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

export interface StepItem {
  id: number;
  label: string;
  summary?: string;
  description?: string;
}

export interface StepIndicatorProps {
  steps: StepItem[];
  currentStep: number;
  onStepClick?: (stepId: number) => void;
  className?: string;
}

export function StepIndicator({
  steps,
  currentStep,
  onStepClick,
  className,
}: StepIndicatorProps) {
  return (
    <nav
      aria-label="Project initialization steps"
      className={cn("w-full", className)}
    >
      <ol className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2">
        {steps.map((step) => {
          const isCompleted = step.id < currentStep;
          const isCurrent = step.id === currentStep;
          const isUpcoming = step.id > currentStep;
          const isClickable = isCompleted && onStepClick;

          return (
            <li key={step.id}>
              <button
                type="button"
                onClick={() => isClickable && onStepClick(step.id)}
                disabled={!isClickable}
                aria-current={isCurrent ? "step" : undefined}
                className={cn(
                  "w-full text-left p-3 rounded-lg border transition-all text-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500",
                  isCurrent &&
                    "border-sky-500/80 bg-sky-950/20 text-zinc-100 shadow-[0_0_15px_rgba(14,165,233,0.08)]",
                  isCompleted &&
                    "border-zinc-800 bg-zinc-900/60 text-zinc-300 hover:border-zinc-700 cursor-pointer",
                  isUpcoming &&
                    "border-zinc-800/60 bg-zinc-950/40 text-zinc-500 cursor-not-allowed",
                )}
              >
                <div className="flex items-center gap-2 mb-1.5">
                  <span
                    className={cn(
                      "flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-mono font-medium",
                      isCompleted && "bg-sky-500 text-zinc-950 font-bold",
                      isCurrent &&
                        "bg-sky-500/20 text-sky-300 border border-sky-400/60 font-bold",
                      isUpcoming &&
                        "bg-zinc-800 text-zinc-500 border border-zinc-700/50",
                    )}
                  >
                    {isCompleted ? (
                      <Check className="h-3 w-3 stroke-3" />
                    ) : (
                      step.id
                    )}
                  </span>
                  <span className="font-medium text-xs truncate">
                    Level {step.id}
                  </span>
                </div>

                <div className="font-semibold text-zinc-200 truncate">
                  {step.label}
                </div>

                {step.summary ? (
                  <div className="mt-1 font-mono text-[11px] text-sky-400 truncate">
                    {step.summary}
                  </div>
                ) : step.description ? (
                  <div className="mt-1 text-[11px] text-zinc-500 truncate">
                    {step.description}
                  </div>
                ) : null}
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
