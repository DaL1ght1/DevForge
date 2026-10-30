import { describe, expect, it } from "vitest";
import {
  blockedReason,
  initialVariables,
  selectedLabels,
  selectionErrors,
  toggleFeature,
} from "../src/lib/templateSelection";
import type { TemplateOptionsResponse } from "../src/types/api";

const option = (
  id: string,
  extra: { requires?: string[]; conflicts?: string[] } = {},
) => ({
  id,
  label: id.toUpperCase(),
  description: null,
  requires: extra.requires ?? [],
  conflicts: extra.conflicts ?? [],
  dependencies: [],
});

const options: TemplateOptionsResponse = {
  templateId: "t",
  templateVersionId: "v",
  version: "2.0.0",
  contentHash: "h",
  schemaVersion: 2,
  variables: [
    {
      name: "JAVA_VERSION",
      label: "Java version",
      type: "ENUM",
      required: false,
      defaultValue: "21",
      options: ["17", "21"],
      pattern: null,
    },
  ],
  features: [
    {
      id: "database",
      label: "Database",
      kind: "CHOICE",
      required: false,
      options: [option("postgresql"), option("mongodb")],
    },
    {
      id: "extras",
      label: "Extras",
      kind: "MULTI",
      required: false,
      options: [
        option("openapi"),
        option("flyway", { requires: ["database"], conflicts: ["mongodb"] }),
      ],
    },
  ],
};

const [database, extras] = options.features;

describe("toggleFeature", () => {
  it("replaces the option of a choice group and clears it on reselect", () => {
    let f = toggleFeature({}, database, "postgresql");
    expect(f).toEqual({ database: ["postgresql"] });
    f = toggleFeature(f, database, "mongodb");
    expect(f).toEqual({ database: ["mongodb"] });
    expect(toggleFeature(f, database, "mongodb")).toEqual({});
    expect(toggleFeature(f, database, null)).toEqual({});
  });

  it("adds and removes options of a multi group", () => {
    let f = toggleFeature({}, extras, "openapi");
    f = toggleFeature(f, extras, "flyway");
    expect(f).toEqual({ extras: ["openapi", "flyway"] });
    expect(toggleFeature(f, extras, "openapi")).toEqual({
      extras: ["flyway"],
    });
  });
});

describe("selection rules", () => {
  it("starts from variable defaults", () => {
    expect(initialVariables(options)).toEqual({ JAVA_VERSION: "21" });
  });

  it("reports unmet requirements and invalid variables", () => {
    expect(
      selectionErrors(options, { JAVA_VERSION: "8" }, { extras: ["flyway"] }),
    ).toEqual([
      "Java version must be one of 17, 21",
      "FLYWAY requires Database",
    ]);
    expect(
      selectionErrors(
        options,
        { JAVA_VERSION: "21" },
        { database: ["postgresql"], extras: ["flyway"] },
      ),
    ).toEqual([]);
  });

  it("blocks conflicting options in both directions", () => {
    const flyway = extras.options[1];
    const mongodb = database.options[1];
    expect(blockedReason(options, { database: ["mongodb"] }, flyway)).toContain(
      "MONGODB",
    );
    expect(blockedReason(options, { extras: ["flyway"] }, mongodb)).toContain(
      "FLYWAY",
    );
    expect(blockedReason(options, {}, flyway)).toBeNull();
  });

  it("lists selected labels in manifest order", () => {
    expect(
      selectedLabels(options, {
        extras: ["openapi"],
        database: ["postgresql"],
      }),
    ).toEqual([
      { group: "Database", option: "POSTGRESQL" },
      { group: "Extras", option: "OPENAPI" },
    ]);
  });
});
