import React from "react";
import { cn } from "@/lib/utils";

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?:
    | "default"
    | "secondary"
    | "success"
    | "warning"
    | "danger"
    | "info"
    | "outline";
  size?: "sm" | "md";
  pulse?: boolean;
}

export function Badge({
  className,
  variant = "default",
  size = "md",
  pulse = false,
  children,
  ...props
}: BadgeProps) {
  const variants = {
    default: "bg-zinc-800 text-zinc-300 border-zinc-700",
    secondary: "bg-zinc-900 text-zinc-400 border-zinc-800",
    success: "bg-emerald-950/70 text-emerald-300 border-emerald-800/80",
    warning: "bg-amber-950/70 text-amber-300 border-amber-800/80",
    danger: "bg-rose-950/70 text-rose-300 border-rose-800/80",
    info: "bg-sky-950/70 text-sky-300 border-sky-800/80",
    outline: "bg-transparent text-zinc-400 border-zinc-700",
  };

  const sizes = {
    sm: "px-2 py-0.5 text-[10px] gap-1",
    md: "px-2.5 py-1 text-xs gap-1.5",
  };

  return (
    <span
      className={cn(
        "inline-flex items-center font-mono font-medium rounded-md border select-none leading-none",
        variants[variant],
        sizes[size],
        className
      )}
      {...props}
    >
      {pulse && (
        <span className="relative flex h-1.5 w-1.5">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-current opacity-75"></span>
          <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-current"></span>
        </span>
      )}
      {children}
    </span>
  );
}
