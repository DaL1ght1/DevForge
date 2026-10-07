import React from "react";
import { cn } from "@/lib/utils";

export interface SelectOption {
  value: string;
  label: string;
  description?: string;
}

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  hint?: string;
  options?: SelectOption[];
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className, label, error, hint, id, options, children, ...props }, ref) => {
    const selectId = id || props.name;

    return (
      <div className="w-full space-y-1.5">
        {label && (
          <label
            htmlFor={selectId}
            className="block text-xs font-medium text-zinc-300 select-none"
          >
            {label}
            {props.required && <span className="text-red-400 ml-1">*</span>}
          </label>
        )}
        <select
          id={selectId}
          ref={ref}
          className={cn(
            "w-full bg-zinc-900 border border-zinc-800 text-zinc-100 text-sm rounded-md px-3 py-2 transition-colors",
            "focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500",
            "disabled:opacity-50 disabled:bg-zinc-950 disabled:cursor-not-allowed",
            error
              ? "focus:ring-red-500 focus:border-red-500"
              : "border-zinc-800 hover:border-zinc-700",
            className,
          )}
          {...props}
        >
          {options
            ? options.map((opt) => (
                <option
                  key={opt.value}
                  value={opt.value}
                  className="bg-zinc-900"
                >
                  {opt.label}
                </option>
              ))
            : children}
        </select>
        {error && <p className="text-xs text-red-400 mt-1">{error}</p>}
        {hint && !error && <p className="text-xs text-zinc-500 mt-1">{hint}</p>}
      </div>
    );
  },
);

Select.displayName = "Select";
