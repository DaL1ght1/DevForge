"use client";

import { useId } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useWorkspace } from "@/context/WorkspaceContext";
import { cn } from "@/lib/utils";

/**
 * Native select for the active workspace. On a workspace settings page, switching also navigates
 * to the newly selected workspace so the page and the switcher never disagree.
 */
export function WorkspaceSwitcher({ className }: { className?: string }) {
  const id = useId();
  const router = useRouter();
  const pathname = usePathname();
  const { workspaces, activeWorkspace, setActiveWorkspaceId } = useWorkspace();

  if (workspaces.length === 0) return null;

  const personal = workspaces.filter((w) => w.type === "PERSONAL");
  const organizations = workspaces.filter((w) => w.type === "ORGANIZATION");

  return (
    <div className={cn("flex items-center gap-2", className)}>
      <label
        htmlFor={id}
        className="text-[11px] font-mono uppercase tracking-wider text-zinc-500"
      >
        Workspace
      </label>
      <select
        id={id}
        name="workspace"
        value={activeWorkspace?.id ?? ""}
        onChange={(e) => {
          setActiveWorkspaceId(e.target.value);
          if (pathname.startsWith("/workspaces/")) {
            router.push(`/workspaces/${e.target.value}`);
          }
        }}
        className="max-w-48 rounded-md border border-zinc-800 bg-zinc-900 px-2 py-1 text-xs text-zinc-200 hover:border-zinc-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500"
      >
        <optgroup label="Personal">
          {personal.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name} (personal)
            </option>
          ))}
        </optgroup>
        {organizations.length > 0 && (
          <optgroup label="Organizations">
            {organizations.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name}
              </option>
            ))}
          </optgroup>
        )}
      </select>
    </div>
  );
}
