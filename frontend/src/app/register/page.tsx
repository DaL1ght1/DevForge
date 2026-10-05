"use client";

import Link from "next/link";
import React, { useState } from "react";
import { ArrowRight, Eye, EyeOff, KeyRound, Mail, Terminal, User, UserPlus } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/Card";
import { ErrorAlert } from "@/components/common/ErrorAlert";

export default function RegisterPage() {
  const { register } = useAuth();
  const [form, setForm] = useState({
    username: "",
    email: "",
    password: "",
    firstName: "",
    lastName: "",
  });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onChange = (key: keyof typeof form, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const handleSubmit = async (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);

    if (form.password.length < 8) {
      setError("Password must contain at least 8 characters");
      return;
    }

    setLoading(true);

    try {
      await register(form);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Unable to complete registration";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 flex flex-col justify-center px-4 py-12 sm:px-6 lg:px-8 text-zinc-100">
      <div className="mx-auto w-full max-w-lg">
        <div className="flex items-center justify-center gap-2.5 mb-6 text-center">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg border border-zinc-700 bg-zinc-900 text-sky-400">
            <Terminal className="h-4 w-4" />
          </div>
          <span className="font-mono text-sm font-bold tracking-wider text-zinc-100">
            DEVFORGE IDP
          </span>
        </div>

        <Card className="border-zinc-800 bg-zinc-900/80 shadow-lg">
          <CardHeader className="space-y-1 text-center pb-4">
            <CardTitle className="text-xl font-bold tracking-tight text-zinc-100">
              Create Developer Account
            </CardTitle>
            <CardDescription className="text-xs text-zinc-400">
              Register for direct access to service provisioning and deployment logs
            </CardDescription>
          </CardHeader>

          <CardContent className="pt-2">
            <form onSubmit={handleSubmit} className="space-y-4">
              <ErrorAlert error={error} />

              <div className="grid gap-4 sm:grid-cols-2">
                <Input
                  label="First Name"
                  id="firstName"
                  name="firstName"
                  value={form.firstName}
                  onChange={(e) => onChange("firstName", e.target.value)}
                  placeholder="Ada"
                  required
                />
                <Input
                  label="Last Name"
                  id="lastName"
                  name="lastName"
                  value={form.lastName}
                  onChange={(e) => onChange("lastName", e.target.value)}
                  placeholder="Lovelace"
                  required
                />
              </div>

              <Input
                label="Username"
                id="username"
                name="username"
                value={form.username}
                onChange={(e) => onChange("username", e.target.value)}
                placeholder="adalovelace"
                leftIcon={<User className="h-4 w-4" />}
                hint="Used for commit authors and service ownership"
                required
              />

              <Input
                label="Work Email"
                id="email"
                name="email"
                type="email"
                value={form.email}
                onChange={(e) => onChange("email", e.target.value)}
                placeholder="ada@company.org"
                leftIcon={<Mail className="h-4 w-4" />}
                required
              />

              <Input
                label="Password"
                id="password"
                name="password"
                type={showPassword ? "text" : "password"}
                value={form.password}
                onChange={(e) => onChange("password", e.target.value)}
                placeholder="At least 8 characters"
                leftIcon={<KeyRound className="h-4 w-4" />}
                rightIcon={
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="text-zinc-400 hover:text-zinc-200 focus:outline-none"
                    tabIndex={-1}
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    {showPassword ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </button>
                }
                hint="Minimum 8 characters"
                required
              />

              <Button
                type="submit"
                variant="primary"
                className="w-full mt-2 bg-sky-600 hover:bg-sky-500 text-white"
                isLoading={loading}
              >
                <UserPlus className="h-4 w-4 mr-2" />
                Register & Sign In
              </Button>

              <div className="text-center text-xs text-zinc-500 pt-3 border-t border-zinc-800/80">
                Already registered?{" "}
                <Link
                  href="/login"
                  className="inline-flex items-center gap-1 text-sky-400 hover:text-sky-300 font-medium transition-colors"
                >
                  Sign in here <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
