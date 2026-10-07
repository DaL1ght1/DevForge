"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { Badge } from "@/components/ui/Badge";
import { templatesApi } from "@/lib/api/templates";
import {
  Boxes,
  Plus,
  LogOut,
  Loader2,
  Menu,
  X,
  Terminal,
  RefreshCw,
  UserRound,
} from "lucide-react";
import { cn } from "@/lib/utils";

export function Navbar() {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [isSyncingTemplates, setIsSyncingTemplates] = useState(false);
  const [templateSyncMessage, setTemplateSyncMessage] = useState<string | null>(
    null,
  );

  const handleLogout = async () => {
    setIsLoggingOut(true);
    try {
      await logout();
    } finally {
      setIsLoggingOut(false);
    }
  };

  const handleTemplateSync = async () => {
    setIsSyncingTemplates(true);
    setTemplateSyncMessage(null);
    try {
      const result = await templatesApi.sync();
      setTemplateSyncMessage(
        `${result.synchronizedTemplates} added, ${result.unchanged} unchanged, ${result.failed} failed`,
      );
    } catch (error) {
      setTemplateSyncMessage(
        error instanceof Error ? error.message : "Template sync failed",
      );
    } finally {
      setIsSyncingTemplates(false);
    }
  };

  const navLinks = [
    {
      href: "/services",
      label: "Service Catalog",
      icon: Boxes,
      active:
        pathname === "/services" ||
        (pathname.startsWith("/services/") && pathname !== "/services/new"),
    },
    {
      href: "/services/new",
      label: "New Service",
      icon: Plus,
      active: pathname === "/services/new",
    },
  ];

  return (
    <header className="sticky top-0 z-40 w-full border-b border-zinc-800/80 bg-zinc-950/90 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-7xl items-center justify-between px-4 sm:px-6">
        <div className="flex items-center gap-6">
          <Link
            href="/services"
            className="flex items-center gap-2.5 text-zinc-100 transition hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500 rounded-sm"
          >
            <div className="flex h-7 w-7 items-center justify-center rounded-md border border-zinc-700 bg-zinc-900 text-sky-400">
              <Terminal className="h-4 w-4" />
            </div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-sm font-bold tracking-wider text-zinc-100">
                DEVFORGE
              </span>
              <span className="rounded bg-zinc-800 px-1.5 py-0.5 font-mono text-[10px] text-zinc-400 border border-zinc-700/60">
                IDP
              </span>
            </div>
          </Link>

          <nav className="hidden md:flex items-center gap-1">
            {navLinks.map((link) => {
              const Icon = link.icon;
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className={cn(
                    "flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500",
                    link.active
                      ? "bg-zinc-800/80 text-zinc-100 border border-zinc-700/70"
                      : "text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900",
                  )}
                >
                  <Icon className="h-3.5 w-3.5" />
                  {link.label}
                </Link>
              );
            })}
          </nav>
        </div>

        <div className="hidden md:flex items-center gap-3">
          {user && (
            <Link
              href="/profile"
              className="flex items-center gap-2 rounded-md border border-zinc-800 bg-zinc-900/60 px-2.5 py-1 text-xs hover:border-zinc-700"
            >
              <UserRound className="h-3.5 w-3.5 text-zinc-500" />
              <span className="text-zinc-400 font-mono">{user.username}</span>
              <Badge variant="outline" size="sm">
                {user.role || "DEVELOPER"}
              </Badge>
            </Link>
          )}

          {user?.role === "ADMIN" && (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleTemplateSync}
                disabled={isSyncingTemplates}
                className="flex items-center gap-1.5 rounded-md border border-sky-900/70 bg-sky-950/40 px-2.5 py-1 text-xs text-sky-300 hover:border-sky-700 hover:text-sky-100 disabled:cursor-not-allowed disabled:opacity-50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500"
                aria-label="Synchronize templates"
              >
                <RefreshCw
                  className={cn(
                    "h-3.5 w-3.5",
                    isSyncingTemplates && "animate-spin",
                  )}
                />
                <span>
                  {isSyncingTemplates ? "Syncing..." : "Sync templates"}
                </span>
              </button>
              {templateSyncMessage && (
                <span className="sr-only" role="status" aria-live="polite">
                  {templateSyncMessage}
                </span>
              )}
            </div>
          )}

          <button
            onClick={handleLogout}
            disabled={isLoggingOut}
            className="flex items-center gap-1.5 rounded-md border border-zinc-800 bg-zinc-900/40 px-2.5 py-1 text-xs text-zinc-400 hover:border-zinc-700 hover:text-zinc-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500"
            title="Sign out of DevForge"
          >
            {isLoggingOut ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin text-zinc-300" />
            ) : (
              <LogOut className="h-3.5 w-3.5" />
            )}
            <span>{isLoggingOut ? "Signing out..." : "Sign out"}</span>
          </button>
        </div>

        <div className="flex md:hidden items-center gap-2">
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="p-1.5 text-zinc-400 hover:text-zinc-100 hover:bg-zinc-800 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-500"
            aria-label="Toggle navigation menu"
          >
            {mobileMenuOpen ? (
              <X className="h-5 w-5" />
            ) : (
              <Menu className="h-5 w-5" />
            )}
          </button>
        </div>
      </div>

      {mobileMenuOpen && (
        <div className="md:hidden border-t border-zinc-800 bg-zinc-950 px-4 py-3 space-y-3">
          <nav className="flex flex-col gap-1">
            {navLinks.map((link) => {
              const Icon = link.icon;
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  onClick={() => setMobileMenuOpen(false)}
                  className={cn(
                    "flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium transition-colors",
                    link.active
                      ? "bg-zinc-800 text-zinc-100"
                      : "text-zinc-400 hover:text-zinc-100 hover:bg-zinc-900",
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {link.label}
                </Link>
              );
            })}
          </nav>

          {user && (
            <div className="pt-2 border-t border-zinc-800/80 flex items-center justify-between text-xs">
              <div className="flex items-center gap-2">
                <Link
                  href="/profile"
                  onClick={() => setMobileMenuOpen(false)}
                  className="font-mono text-zinc-300 hover:text-zinc-100"
                >
                  {user.username}
                </Link>
                <Badge variant="outline" size="sm">
                  {user.role}
                </Badge>
              </div>
              {user.role === "ADMIN" && (
                <button
                  type="button"
                  onClick={handleTemplateSync}
                  disabled={isSyncingTemplates}
                  className="flex items-center gap-1 text-sky-300 hover:text-sky-100 disabled:cursor-not-allowed disabled:opacity-50"
                  aria-label="Synchronize templates"
                >
                  <RefreshCw
                    className={cn(
                      "h-3.5 w-3.5",
                      isSyncingTemplates && "animate-spin",
                    )}
                  />
                  {isSyncingTemplates ? "Syncing..." : "Sync templates"}
                </button>
              )}
              <button
                onClick={async () => {
                  setMobileMenuOpen(false);
                  await handleLogout();
                }}
                disabled={isLoggingOut}
                className="flex items-center gap-1 text-zinc-400 hover:text-zinc-200 disabled:opacity-50"
              >
                {isLoggingOut ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin text-zinc-300" />
                ) : (
                  <LogOut className="h-3.5 w-3.5" />
                )}
                {isLoggingOut ? "Signing out..." : "Sign out"}
              </button>
            </div>
          )}
          {templateSyncMessage && (
            <p
              className="text-xs text-zinc-400"
              role="status"
              aria-live="polite"
            >
              {templateSyncMessage}
            </p>
          )}
        </div>
      )}
    </header>
  );
}
