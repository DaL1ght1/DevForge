"use client";

import React from "react";
import { Check } from "lucide-react";
import { Input } from "@/components/ui/Input";
import { Select } from "@/components/ui/Select";
import { cn } from "@/lib/utils";
import {
  FeatureSelection,
  blockedReason,
  toggleFeature,
} from "@/lib/templateSelection";
import type {
  TemplateFeatureGroup,
  TemplateFeatureOption,
  TemplateOptionsResponse,
} from "@/types/api";

interface TemplateOptionsFormProps {
  options: TemplateOptionsResponse;
  variables: Record<string, string>;
  features: FeatureSelection;
  onVariablesChange: (variables: Record<string, string>) => void;
  onFeaturesChange: (features: FeatureSelection) => void;
}

/** Renders a template's manifest variables and feature groups as form controls. */
export function TemplateOptionsForm({
  options,
  variables,
  features,
  onVariablesChange,
  onFeaturesChange,
}: TemplateOptionsFormProps) {
  if (options.variables.length === 0 && options.features.length === 0) {
    return (
      <p className="text-xs text-zinc-500">
        This template has no configurable options.
      </p>
    );
  }

  return (
    <div className="space-y-6">
      {options.variables.length > 0 && (
        <div className="grid gap-5 md:grid-cols-2">
          {options.variables.map((v) => {
            const value = variables[v.name] ?? "";
            const set = (next: string) =>
              onVariablesChange({ ...variables, [v.name]: next });
            return v.options.length > 0 ? (
              <Select
                key={v.name}
                id={`var-${v.name}`}
                label={v.label}
                value={value}
                required={v.required}
                onChange={(e) => set(e.target.value)}
                options={[
                  ...(v.required || v.defaultValue
                    ? []
                    : [{ value: "", label: "Not set" }]),
                  ...v.options.map((o) => ({ value: o, label: o })),
                ]}
              />
            ) : (
              <Input
                key={v.name}
                id={`var-${v.name}`}
                label={v.label}
                value={value}
                required={v.required}
                onChange={(e) => set(e.target.value)}
              />
            );
          })}
        </div>
      )}

      {options.features.map((group) => (
        <FeatureGroupField
          key={group.id}
          options={options}
          group={group}
          features={features}
          onChange={onFeaturesChange}
        />
      ))}
    </div>
  );
}

function FeatureGroupField({
  options,
  group,
  features,
  onChange,
}: {
  options: TemplateOptionsResponse;
  group: TemplateFeatureGroup;
  features: FeatureSelection;
  onChange: (features: FeatureSelection) => void;
}) {
  const chosen = features[group.id] ?? [];
  const isChoice = group.kind === "CHOICE";

  return (
    <fieldset className="space-y-2">
      <legend className="text-xs font-medium text-zinc-300 mb-2">
        {group.label}
        {group.required && <span className="text-red-400 ml-1">*</span>}
        <span className="ml-2 text-[11px] font-normal text-zinc-500">
          {isChoice ? "Pick one" : "Pick any"}
        </span>
      </legend>
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        {isChoice && !group.required && (
          <OptionCard
            label="None"
            description={null}
            selected={chosen.length === 0}
            blocked={null}
            radio
            onSelect={() => onChange(toggleFeature(features, group, null))}
          />
        )}
        {group.options.map((option: TemplateFeatureOption) => {
          const selected = chosen.includes(option.id);
          const blocked = selected
            ? null
            : blockedReason(options, features, option);
          return (
            <OptionCard
              key={option.id}
              label={option.label}
              description={option.description}
              selected={selected}
              blocked={blocked}
              radio={isChoice}
              onSelect={() =>
                onChange(toggleFeature(features, group, option.id))
              }
            />
          );
        })}
      </div>
    </fieldset>
  );
}

function OptionCard({
  label,
  description,
  selected,
  blocked,
  radio,
  onSelect,
}: {
  label: string;
  description: string | null;
  selected: boolean;
  blocked: string | null;
  radio: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      role={radio ? "radio" : "checkbox"}
      aria-checked={selected}
      disabled={!!blocked}
      title={blocked ?? undefined}
      onClick={onSelect}
      className={cn(
        "flex items-start gap-3 text-left p-3 rounded-lg border transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500",
        selected
          ? "border-sky-500 bg-sky-950/20"
          : "border-zinc-800 bg-zinc-900/60 hover:border-zinc-700",
        blocked && "opacity-40 cursor-not-allowed hover:border-zinc-800",
      )}
    >
      <span
        className={cn(
          "mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center border",
          radio ? "rounded-full" : "rounded",
          selected ? "border-sky-500 bg-sky-500" : "border-zinc-600",
        )}
      >
        {selected && <Check className="h-3 w-3 text-zinc-950 stroke-3" />}
      </span>
      <span className="min-w-0">
        <span className="block text-sm font-medium text-zinc-100">{label}</span>
        {(blocked || description) && (
          <span className="block text-[11px] text-zinc-500 mt-0.5">
            {blocked ?? description}
          </span>
        )}
      </span>
    </button>
  );
}
