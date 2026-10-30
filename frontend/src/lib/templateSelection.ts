import type {
  TemplateFeatureGroup,
  TemplateFeatureOption,
  TemplateOptionsResponse,
} from "@/types/api";

export type FeatureSelection = Record<string, string[]>;

export function initialVariables(
  options: TemplateOptionsResponse,
): Record<string, string> {
  const values: Record<string, string> = {};
  for (const v of options.variables) {
    if (v.defaultValue != null) values[v.name] = v.defaultValue;
  }
  return values;
}

function selectedIds(features: FeatureSelection): Set<string> {
  const ids = new Set<string>();
  for (const [group, opts] of Object.entries(features)) {
    if (opts.length > 0) {
      ids.add(group);
      opts.forEach((o) => ids.add(o));
    }
  }
  return ids;
}

export function labelOf(options: TemplateOptionsResponse, id: string): string {
  for (const g of options.features) {
    if (g.id === id) return g.label;
    const o = g.options.find((opt) => opt.id === id);
    if (o) return o.label;
  }
  return id;
}

/**
 * Selects or deselects an option. Choosing in a CHOICE group replaces the previous option;
 * passing optionId null clears the group.
 */
export function toggleFeature(
  features: FeatureSelection,
  group: TemplateFeatureGroup,
  optionId: string | null,
): FeatureSelection {
  const current = features[group.id] ?? [];
  let next: string[];
  if (optionId == null) {
    next = [];
  } else if (group.kind === "CHOICE") {
    next = current.includes(optionId) && !group.required ? [] : [optionId];
  } else {
    next = current.includes(optionId)
      ? current.filter((id) => id !== optionId)
      : [...current, optionId];
  }
  const result = { ...features };
  if (next.length > 0) result[group.id] = next;
  else delete result[group.id];
  return result;
}

/**
 * Why an option cannot be selected right now, or null. Only conflicts block selection;
 * unmet requirements are reported by selectionErrors so users can satisfy them in any order.
 */
export function blockedReason(
  options: TemplateOptionsResponse,
  features: FeatureSelection,
  option: TemplateFeatureOption,
): string | null {
  const ids = selectedIds(features);
  const conflict = option.conflicts.find((c) => ids.has(c));
  if (conflict) return `Not available with ${labelOf(options, conflict)}`;
  for (const g of options.features) {
    for (const other of g.options) {
      if (ids.has(other.id) && other.conflicts.includes(option.id)) {
        return `Not available with ${other.label}`;
      }
    }
  }
  return null;
}

/** Client-side mirror of the backend's TemplateSelectionResolver checks. */
export function selectionErrors(
  options: TemplateOptionsResponse,
  variables: Record<string, string>,
  features: FeatureSelection,
): string[] {
  const errors: string[] = [];
  for (const v of options.variables) {
    const value = (variables[v.name] ?? "").trim();
    if (!value) {
      if (v.required) errors.push(`${v.label} is required`);
      continue;
    }
    if (v.options.length > 0 && !v.options.includes(value)) {
      errors.push(`${v.label} must be one of ${v.options.join(", ")}`);
    } else if (v.pattern && !new RegExp(`^(?:${v.pattern})$`).test(value)) {
      errors.push(`${v.label} has an invalid format`);
    }
  }
  const ids = selectedIds(features);
  for (const g of options.features) {
    const chosen = features[g.id] ?? [];
    if (g.required && chosen.length === 0)
      errors.push(`${g.label} is required`);
    for (const o of g.options) {
      if (!chosen.includes(o.id)) continue;
      for (const r of o.requires) {
        if (!ids.has(r))
          errors.push(`${o.label} requires ${labelOf(options, r)}`);
      }
      for (const c of o.conflicts) {
        if (ids.has(c))
          errors.push(
            `${o.label} cannot be combined with ${labelOf(options, c)}`,
          );
      }
    }
  }
  return errors;
}

/** Selected option labels in manifest order, for summaries. */
export function selectedLabels(
  options: TemplateOptionsResponse,
  features: FeatureSelection,
): { group: string; option: string }[] {
  const out: { group: string; option: string }[] = [];
  for (const g of options.features) {
    for (const o of g.options) {
      if ((features[g.id] ?? []).includes(o.id))
        out.push({ group: g.label, option: o.label });
    }
  }
  return out;
}
