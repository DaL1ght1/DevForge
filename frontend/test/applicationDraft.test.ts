import { describe, expect, it } from "vitest";
import {
  ApplicationDraft,
  ServiceDraft,
  applicationErrors,
  cleanName,
  portOf,
  toCreationDto,
} from "../src/lib/applicationDraft";
import type { TemplateOptionsResponse } from "../src/types/api";

const options: TemplateOptionsResponse = {
  templateId: "t1",
  templateVersionId: "v1",
  version: "2.0.0",
  contentHash: "h",
  schemaVersion: 2,
  variables: [],
  features: [
    {
      id: "database",
      label: "Database",
      kind: "CHOICE",
      required: true,
      options: [
        {
          id: "postgresql",
          label: "PostgreSQL",
          description: null,
          requires: [],
          conflicts: [],
          dependencies: [],
        },
      ],
    },
  ],
};

const service = (name: string, extra: Partial<ServiceDraft> = {}) => ({
  key: name,
  name,
  description: "",
  templateId: "t1",
  variables: {},
  features: { database: ["postgresql"] },
  ...extra,
});

const draft = (services: ServiceDraft[], name = "shop"): ApplicationDraft => ({
  name,
  description: "",
  services,
});

describe("applicationErrors", () => {
  it("accepts a valid application", () => {
    expect(
      applicationErrors(draft([service("orders"), service("billing")]), {
        t1: options,
      }),
    ).toEqual([]);
  });

  it("reports invalid names, duplicates and template rules per service", () => {
    const errors = applicationErrors(
      draft(
        [
          service("orders"),
          { ...service("orders"), key: "second" },
          service("billing", { features: {} }),
        ],
        "X",
      ),
      { t1: options },
    );
    expect(errors).toEqual([
      "Application name must be 3-40 lowercase letters, numbers or hyphens",
      "orders: Another service is already named orders",
      "orders: Another service is already named orders",
      "billing: Database is required",
    ]);
  });

  it("requires a template and its loaded options", () => {
    expect(
      applicationErrors(draft([service("orders", { templateId: "" })]), {}),
    ).toEqual(["orders: Choose a template"]);
    expect(applicationErrors(draft([service("orders")]), {})).toEqual([
      "orders: Template options are still loading",
    ]);
    expect(applicationErrors(draft([]), {})).toEqual([
      "Add at least one service",
    ]);
  });
});

describe("draft helpers", () => {
  it("builds the creation request", () => {
    expect(toCreationDto(draft([service("orders")]), "ws")).toEqual({
      name: "shop",
      description: undefined,
      workspaceId: "ws",
      services: [
        {
          name: "orders",
          description: undefined,
          templateVersionId: "t1",
          variables: {},
          features: { database: ["postgresql"] },
        },
      ],
    });
  });

  it("cleans names and assigns ports in order", () => {
    expect(cleanName("My Shop_API!", 40)).toBe("myshopapi");
    expect(portOf(0)).toBe(8080);
    expect(portOf(2)).toBe(8082);
  });
});
