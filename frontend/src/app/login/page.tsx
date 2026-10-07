"use client";

import Link from "next/link";
import React, { useState } from "react";
import {
  Eye,
  EyeOff,
  KeyRound,
  Terminal,
  User,
  ArrowRight,
  Shield,
} from "lucide-react";
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
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await login({ username, password });
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Invalid username or password";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 flex flex-col justify-center px-4 py-12 sm:px-6 lg:px-8 text-zinc-100">
      <div className="mx-auto w-full max-w-4xl grid gap-8 lg:grid-cols-[1.1fr_420px] items-center">
        <div className="space-y-6">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-zinc-700 bg-zinc-900 text-sky-400">
              <Terminal className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-mono text-sm font-bold tracking-wider text-zinc-100">
                  DEVFORGE
                </span>
                <span className="rounded bg-zinc-800 px-1.5 py-0.5 font-mono text-[10px] text-zinc-400 border border-zinc-700/60">
                  IDP CONSOLE
                </span>
              </div>
              <p className="text-xs text-zinc-400">
                Internal Developer Platform
              </p>
            </div>
          </div>

          <div className="space-y-3">
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-zinc-100">
              Engineering service scaffolding & governance.
            </h1>
            <p className="text-sm text-zinc-400 leading-relaxed max-w-lg">
              Direct authentication to your local and cluster DevForge instance.
              Scaffold microservices across Java, Go, Python, and TypeScript
              with pre-configured build pipelines.
            </p>
          </div>

          <div className="grid gap-3 sm:grid-cols-2 text-xs">
            <div className="p-3.5 rounded-lg border border-zinc-800 bg-zinc-900/60">
              <div className="text-[11px] font-mono text-sky-400 font-semibold mb-1">
                MULTI-LEVEL RUNTIMES
              </div>
              <div className="text-zinc-300">
                Tiered language, framework, and build tool configuration.
              </div>
            </div>

            <div className="p-3.5 rounded-lg border border-zinc-800 bg-zinc-900/60">
              <div className="text-[11px] font-mono text-zinc-400 font-semibold mb-1">
                AUTOMATED REPOSITORIES
              </div>
              <div className="text-zinc-300">
                Direct git repo provisioning with CI/CD manifests.
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2 text-xs text-zinc-500 pt-2 border-t border-zinc-850">
            <Shield className="h-3.5 w-3.5 text-zinc-400" />
            <span>
              Direct local session credentials without external redirects.
            </span>
          </div>
        </div>

        <Card className="border-zinc-800 bg-zinc-900/80 shadow-lg">
          <CardHeader className="space-y-1 pb-4">
            <CardTitle className="text-xl font-bold tracking-tight text-zinc-100">
              Sign In
            </CardTitle>
            <CardDescription className="text-xs text-zinc-400">
              Enter your credentials to access the developer platform
            </CardDescription>
          </CardHeader>

          <CardContent className="pt-2">
            <form onSubmit={handleSubmit} className="space-y-4">
              <ErrorAlert error={error} />

              <Input
                label="Username or Email"
                id="username"
                name="username"
                autoComplete="username"
                placeholder="Enter your username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                leftIcon={<User className="h-4 w-4" />}
                required
              />

              <div className="space-y-1.5">
                <Input
                  label="Password"
                  id="password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  placeholder="••••••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  leftIcon={<KeyRound className="h-4 w-4" />}
                  rightIcon={
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="text-zinc-400 hover:text-zinc-200 focus:outline-none"
                      tabIndex={-1}
                      aria-label={
                        showPassword ? "Hide password" : "Show password"
                      }
                    >
                      {showPassword ? (
                        <EyeOff className="h-4 w-4" />
                      ) : (
                        <Eye className="h-4 w-4" />
                      )}
                    </button>
                  }
                  required
                />
              </div>

              <Button
                type="submit"
                variant="primary"
                className="w-full mt-2 bg-sky-600 hover:bg-sky-500 text-white"
                isLoading={loading}
              >
                Sign In to Platform
              </Button>

              <div className="text-center text-xs text-zinc-500 pt-2 border-t border-zinc-800/80">
                New engineer?{" "}
                <Link
                  href="/register"
                  className="inline-flex items-center gap-1 text-sky-400 hover:text-sky-300 font-medium transition-colors"
                >
                  Create an account <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
