import { describe, expect, it } from "vitest";
import {
  buildTree,
  defaultFile,
  featureOf,
  formatBytes,
} from "../src/lib/previewTree";
import type { PreviewFileEntry } from "../src/types/api";

const file = (path: string, origin = "base"): PreviewFileEntry => ({
  path,
  origin,
  size: 10,
  binary: false,
  omitted: false,
});

describe("buildTree", () => {
  it("puts folders before files and collapses single-folder chains", () => {
    const tree = buildTree([
      file("pom.xml"),
      file("src/main/java/com/devforge/orders/App.java"),
      file("src/main/resources/application.yaml"),
      file(".gitignore"),
    ]);

    expect(tree.children.map((c) => c.name)).toEqual([
      "src/main",
      ".gitignore",
      "pom.xml",
    ]);
    const main = tree.children[0];
    expect(main.children.map((c) => c.name)).toEqual([
      "java/com/devforge/orders",
      "resources",
    ]);
    expect(main.children[0].children[0].file?.path).toBe(
      "src/main/java/com/devforge/orders/App.java",
    );
  });
});

describe("preview helpers", () => {
  it("extracts the feature from an origin", () => {
    expect(featureOf("feature:flyway")).toBe("flyway");
    expect(featureOf("base")).toBeNull();
  });

  it("prefers the README, then build files, then the first text file", () => {
    expect(defaultFile([file("pom.xml"), file("README.md")])).toBe("README.md");
    expect(defaultFile([file("src/a.ts"), file("package.json")])).toBe(
      "package.json",
    );
    expect(
      defaultFile([{ ...file("logo.png"), binary: true }, file("main.go")]),
    ).toBe("main.go");
    expect(defaultFile([])).toBeNull();
  });

  it("formats sizes", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(2048)).toBe("2.0 KB");
    expect(formatBytes(3 * 1024 * 1024)).toBe("3.0 MB");
  });
});
