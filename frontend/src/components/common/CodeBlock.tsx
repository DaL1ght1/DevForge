import React from "react";
import { cn } from "@/lib/utils";
import { CopyButton } from "./CopyButton";

export interface CodeBlockProps {
  code: string;
  language?: string;
  className?: string;
  showCopy?: boolean;
}

export function CodeBlock({
  code,
  language,
  className,
  showCopy = true,
}: CodeBlockProps) {
  return (
    <div
      className={cn(
        "relative group bg-zinc-950 border border-zinc-800 rounded-md overflow-hidden",
        className
      )}
    >
      <div className="flex items-center justify-between px-3.5 py-1.5 bg-zinc-900/60 border-b border-zinc-850 text-xs font-mono text-zinc-400">
        <span>{language || "sh"}</span>
        {showCopy && <CopyButton text={code} />}
      </div>
      <pre className="p-3.5 text-xs font-mono text-zinc-200 overflow-x-auto selection:bg-blue-900/60">
        <code>{code}</code>
      </pre>
    </div>
  );
}
