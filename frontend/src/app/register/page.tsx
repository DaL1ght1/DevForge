"use client";

import Link from "next/link";
import React, { useState } from "react";
import { ArrowRight, UserPlus } from "lucide-react";
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

export default function RegisterPage() {
  const { register } = useAuth();
  const [form, setForm] = useState({
    username: "",
    email: "",
    password: "",
    firstName: "",
    lastName: "",
  });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onChange = (key: keyof typeof form, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      await register(form);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Unable to create your account";
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-950 p-4">
      <Card className="w-full max-w-xl border-zinc-800 bg-zinc-900/80">
        <CardHeader className="space-y-1 text-center">
          <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-lg border border-zinc-700 bg-zinc-950 text-blue-300">
            <UserPlus className="h-5 w-5" />
          </div>
          <CardTitle className="text-2xl font-bold tracking-tight text-zinc-100">
            Create account
          </CardTitle>
          <CardDescription className="text-xs text-zinc-400">
            Register to access DevForge service provisioning and governance.
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <ErrorAlert error={error} />

            <div className="grid gap-4 md:grid-cols-2">
              <Input
                label="First name"
                value={form.firstName}
                onChange={(e) => onChange("firstName", e.target.value)}
                placeholder="Ada"
                required
              />
              <Input
                label="Last name"
                value={form.lastName}
                onChange={(e) => onChange("lastName", e.target.value)}
                placeholder="Lovelace"
                required
              />
            </div>

            <Input
              label="Username"
              value={form.username}
              onChange={(e) => onChange("username", e.target.value)}
              placeholder="adalovelace"
              required
            />

            <Input
              label="Email"
              type="email"
              value={form.email}
              onChange={(e) => onChange("email", e.target.value)}
              placeholder="ada@devforge.local"
              required
            />

            <Input
              label="Password"
              type="password"
              value={form.password}
              onChange={(e) => onChange("password", e.target.value)}
              placeholder="At least 8 characters"
              required
            />

            <Button type="submit" variant="primary" className="w-full" isLoading={loading}>
              Create account
            </Button>

            <div className="text-center text-xs text-zinc-500">
              Already have an account? {" "}
              <Link href="/login" className="inline-flex items-center gap-1 text-blue-400 hover:text-blue-300">
                Sign in <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
