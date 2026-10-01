import React from "react";
import { cn } from "@/lib/utils";

export interface TextareaProps
  extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  hint?: string;
}

export const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ className, label, error, hint, id, ...props }, ref) => {
    const textareaId = id || props.name;

    return (
      <div className="w-full space-y-1.5">
        {label && (
          <label
            htmlFor={textareaId}
            className="block text-xs font-medium text-zinc-300 select-none"
          >
            {label}
            {props.required && <span className="text-red-400 ml-1">*</span>}
          </label>
        )}
        <textarea
          id={textareaId}
          ref={ref}
          className={cn(
            "w-full bg-zinc-900 border border-zinc-800 text-zinc-100 placeholder:text-zinc-500 text-sm rounded-md px-3 py-2 transition-colors",
            "focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500 min-h-[80px]",
            "disabled:opacity-50 disabled:bg-zinc-950 disabled:cursor-not-allowed",
            error
              ? "border-red-500 focus:ring-red-500 focus:border-red-500"
              : "border-zinc-800 hover:border-zinc-700",
            className
          )}
          {...props}
        />
        {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
        {hint && !error && <p className="text-xs text-zinc-500 mt-1">{hint}</p>}
      </div>
    );
  }
);

Textarea.displayName = "Textarea";
