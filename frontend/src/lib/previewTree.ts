import type { PreviewFileEntry } from "@/types/api";

export interface TreeNode {
  name: string;
  path: string;
  file?: PreviewFileEntry;
  children: TreeNode[];
}

export function buildTree(files: PreviewFileEntry[]): TreeNode {
  const root: TreeNode = { name: "", path: "", children: [] };
  for (const file of files) {
    const parts = file.path.split("/");
    let node = root;
    parts.forEach((part, index) => {
      const path = parts.slice(0, index + 1).join("/");
      let child = node.children.find((c) => c.name === part);
      if (!child) {
        child = { name: part, path, children: [] };
        node.children.push(child);
      }
      if (index === parts.length - 1) child.file = file;
      node = child;
    });
  }
  sortTree(root);
  return collapse(root);
}

function sortTree(node: TreeNode) {
  node.children.sort((a, b) => {
    const aDir = a.file ? 1 : 0;
    const bDir = b.file ? 1 : 0;
    return aDir - bDir || a.name.localeCompare(b.name);
  });
  node.children.forEach(sortTree);
}

function collapse(node: TreeNode): TreeNode {
  node.children = node.children.map(collapse);
  if (node.path && !node.file && node.children.length === 1) {
    const only = node.children[0];
    if (!only.file) {
      return {
        name: `${node.name}/${only.name}`,
        path: only.path,
        children: only.children,
      };
    }
  }
  return node;
}

export function featureOf(origin: string): string | null {
  return origin.startsWith("feature:") ? origin.slice("feature:".length) : null;
}

const PREFERRED_FIRST_FILES = [
  "README.md",
  "pom.xml",
  "build.gradle",
  "package.json",
  "pyproject.toml",
  "go.mod",
  "Cargo.toml",
];

export function defaultFile(files: PreviewFileEntry[]): string | null {
  for (const name of PREFERRED_FIRST_FILES) {
    if (files.some((f) => f.path === name)) return name;
  }
  return files.find((f) => !f.binary)?.path ?? files[0]?.path ?? null;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
