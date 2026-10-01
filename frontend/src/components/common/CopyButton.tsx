"use client";

import React, { useState } from "react";
import { copyToClipboard, cn } from "@/lib/utils";
import { Check, Copy } from "lucide-react";

export interface CopyButtonProps {
  text: string;
  className?: string;
  label?: string;
}

export function CopyButton({ text, className, label }: CopyButtonProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    const success = await copyToClipboard(text);
    if (success) {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <button
      onClick={handleCopy}
      type="button"
      className={cn(
        "inline-flex items-center gap-1.5 px-2 py-1 text-xs font-mono text-zinc-400 hover:text-zinc-200",
        "bg-zinc-800/80 hover:bg-zinc-800 border border-zinc-700/60 rounded transition-colors",
        className
      )}
      title="Copy to clipboard"
    >
      {copied ? (
        <>
          <Check className="w-3.5 h-3.5 text-emerald-400" />
          <span className="text-emerald-400">{label || "Copied"}</span>
        </>
      ) : (
        <>
          <Copy className="w-3.5 h-3.5 text-zinc-400" />
          {label && <span>{label}</span>}
        </>
      )}
    </button>
  );
}
