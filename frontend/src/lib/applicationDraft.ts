import type {
  ApplicationCreationDto,
  TemplateOptionsResponse,
} from "@/types/api";
import { FeatureSelection, selectionErrors } from "./templateSelection";

export const APPLICATION_NAME = /^[a-z0-9][a-z0-9-]{2,39}$/;
export const SERVICE_NAME = /^[a-z0-9-]{3,20}$/;
export const MAX_SERVICES = 10;
export const FIRST_PORT = 8080;

export interface ServiceDraft {
  key: string;
  name: string;
  description: string;
  templateId: string;
  variables: Record<string, string>;
  features: FeatureSelection;
}

export interface ApplicationDraft {
  name: string;
  description: string;
  services: ServiceDraft[];
}

export function cleanName(value: string, max: number): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9-]/g, "")
    .slice(0, max);
}

export function serviceErrors(
  service: ServiceDraft,
  others: ServiceDraft[],
  options: TemplateOptionsResponse | undefined,
): string[] {
  const errors: string[] = [];
  if (!SERVICE_NAME.test(service.name)) {
    errors.push("Name must be 3-20 lowercase letters, numbers or hyphens");
  } else if (
    others.some((o) => o.key !== service.key && o.name === service.name)
  ) {
    errors.push(`Another service is already named ${service.name}`);
  }
  if (!service.templateId) {
    errors.push("Choose a template");
  } else if (!options) {
    errors.push("Template options are still loading");
  } else {
    errors.push(
      ...selectionErrors(options, service.variables, service.features),
    );
  }
  return errors;
}

export function applicationErrors(
  draft: ApplicationDraft,
  options: Record<string, TemplateOptionsResponse>,
): string[] {
  const errors: string[] = [];
  if (!APPLICATION_NAME.test(draft.name)) {
    errors.push(
      "Application name must be 3-40 lowercase letters, numbers or hyphens",
    );
  }
  if (draft.services.length === 0) errors.push("Add at least one service");
  if (draft.services.length > MAX_SERVICES) {
    errors.push(`An application has at most ${MAX_SERVICES} services`);
  }
  draft.services.forEach((service) => {
    serviceErrors(service, draft.services, options[service.templateId]).forEach(
      (e) => errors.push(`${service.name || "Unnamed service"}: ${e}`),
    );
  });
  return errors;
}

export function toCreationDto(
  draft: ApplicationDraft,
  workspaceId: string | undefined,
): ApplicationCreationDto {
  return {
    name: draft.name,
    description: draft.description || undefined,
    workspaceId,
    services: draft.services.map((s) => ({
      name: s.name,
      description: s.description || undefined,
      templateVersionId: s.templateId,
      variables: s.variables,
      features: s.features,
    })),
  };
}

export function portOf(index: number): number {
  return FIRST_PORT + index;
}
