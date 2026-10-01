"use client";

import Link from "next/link";
import React, { useState } from "react";
import { ArrowRight, ShieldCheck, Terminal } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/Card";
import { ErrorAlert } from "@/components/common/ErrorAlert";

export default function LoginPage() {
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await login({ username, password });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Invalid username or password";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 px-4 py-10">
      <div className="mx-auto grid max-w-5xl gap-6 lg:grid-cols-[1.1fr_420px]">
        <div className="rounded-2xl border border-zinc-800 bg-zinc-900/60 p-8 shadow-[inset_0_1px_0_rgba(255,255,255,0.02)]">
          <div className="mb-8 flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-zinc-700 bg-zinc-950 text-blue-300">
              <Terminal className="h-5 w-5" />
            </div>
            <div>
              <div className="text-xs font-medium uppercase tracking-[0.24em] text-zinc-400">
                DevForge
              </div>
              <div className="text-sm text-zinc-300">Internal developer platform</div>
            </div>
          </div>

          <div className="space-y-6">
            <div>
              <p className="text-xs font-medium uppercase tracking-[0.2em] text-blue-400">
                Platform overview
              </p>
              <h1 className="mt-3 text-3xl font-semibold tracking-tight text-zinc-100">
                Provision software without leaving the workflow.
              </h1>
            </div>

            <div className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 p-4">
                <div className="text-xs uppercase tracking-[0.18em] text-zinc-500">Catalog</div>
                <div className="mt-2 text-xl font-semibold text-zinc-100">Service templates</div>
              </div>
              <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 p-4">
                <div className="text-xs uppercase tracking-[0.18em] text-zinc-500">Lifecycle</div>
                <div className="mt-2 text-xl font-semibold text-zinc-100">Provisioning</div>
              </div>
              <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 p-4">
                <div className="text-xs uppercase tracking-[0.18em] text-zinc-500">Access</div>
                <div className="mt-2 text-xl font-semibold text-zinc-100">Policy-aware</div>
              </div>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-950/60 p-4">
              <div className="flex items-center gap-2 text-sm text-zinc-200">
                <ShieldCheck className="h-4 w-4 text-emerald-400" />
                Built for engineers shipping internal services securely.
              </div>
            </div>
          </div>
        </div>

        <Card className="w-full bg-zinc-900 border-zinc-800">
          <CardHeader className="text-center space-y-1">
            <CardTitle className="text-2xl font-bold tracking-tight text-zinc-100">
              Sign in
            </CardTitle>
            <CardDescription className="text-xs text-zinc-400">
              Access the Internal Developer Platform
            </CardDescription>
          </CardHeader>

          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <ErrorAlert error={error} />

              <Input
                label="Username or Email"
                type="text"
                placeholder="devforge_admin"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />

              <Input
                label="Password"
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />

              <Button
                type="submit"
                variant="primary"
                className="w-full mt-2"
                isLoading={loading}
              >
                Sign In
              </Button>

              <div className="rounded-md border border-zinc-800 bg-zinc-950/55 px-3 py-2 text-xs text-zinc-400">
                Demo admin: <span className="font-mono text-zinc-200">devforge_admin</span> / <span className="font-mono text-zinc-200">adminPassword123!</span>
              </div>

              <div className="text-center text-xs text-zinc-500">
                Need an account? {" "}
                <Link href="/register" className="inline-flex items-center gap-1 text-blue-400 hover:text-blue-300">
                  Create one <ArrowRight className="h-3.5 w-3.5" />
                </Link>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}