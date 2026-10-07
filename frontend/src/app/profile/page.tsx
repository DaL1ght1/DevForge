"use client";

import Link from "next/link";
import { SubmitEvent, useEffect, useState } from "react";
import {
  ArrowLeft,
  CheckCircle2,
  KeyRound,
  Save,
  UserRound,
} from "lucide-react";
import { Navbar } from "@/components/layout/Navbar";
import { Button } from "@/components/ui/Button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { useAuth } from "@/context/AuthContext";
import { usersApi } from "@/lib/api/users";

export default function ProfilePage() {
  const { user, refreshProfile } = useAuth();
  const [form, setForm] = useState({
    username: "",
    email: "",
    firstName: "",
    lastName: "",
    password: "",
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!user) return;
    const nextForm = {
      username: user.username,
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      password: "",
    };
    queueMicrotask(() => setForm(nextForm));
  }, [user]);

  const update = (key: keyof typeof form, value: string) =>
    setForm((current) => ({ ...current, [key]: value }));

  const handleSubmit = async (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!user) return;
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      await usersApi.update(user.id, {
        ...form,
        password: form.password || undefined,
      });
      await refreshProfile();
      setForm((current) => ({ ...current, password: "" }));
      setSaved(true);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Unable to update your profile",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      <Navbar />
      <main className="mx-auto max-w-3xl px-4 py-8 sm:px-6">
        <div className="mb-6 flex items-center justify-between border-b border-zinc-800/80 pb-5">
          <div>
            <Link
              href="/services"
              className="mb-2 inline-flex items-center gap-1 text-xs text-zinc-400 hover:text-zinc-200"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Services
            </Link>
            <h1 className="text-2xl font-bold tracking-tight">
              Account settings
            </h1>
            <p className="mt-1 text-xs text-zinc-400">
              Update the profile used for service ownership and repository
              activity.
            </p>
          </div>
          <UserRound className="h-7 w-7 text-sky-400" />
        </div>
        <Card className="border-zinc-800 bg-zinc-900/60">
          <CardHeader>
            <CardTitle className="text-base">Profile</CardTitle>
            <CardDescription>
              Changes are saved to the DevForge backend.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-5">
              <ErrorAlert error={error} />
              {saved && (
                <p
                  className="flex items-center gap-2 text-xs text-emerald-400"
                  role="status"
                >
                  <CheckCircle2 className="h-4 w-4" /> Profile updated
                  successfully.
                </p>
              )}
              <div className="grid gap-4 sm:grid-cols-2">
                <Input
                  label="First name"
                  value={form.firstName}
                  onChange={(e) => update("firstName", e.target.value)}
                  required
                />
                <Input
                  label="Last name"
                  value={form.lastName}
                  onChange={(e) => update("lastName", e.target.value)}
                  required
                />
                <Input
                  label="Username"
                  value={form.username}
                  onChange={(e) => update("username", e.target.value)}
                  autoComplete="username"
                  required
                />
                <Input
                  label="Email"
                  type="email"
                  value={form.email}
                  onChange={(e) => update("email", e.target.value)}
                  autoComplete="email"
                  required
                />
              </div>
              <div className="border-t border-zinc-800 pt-5">
                <Input
                  label="New password"
                  type="password"
                  value={form.password}
                  onChange={(e) => update("password", e.target.value)}
                  autoComplete="new-password"
                  placeholder="Leave blank to keep your current password"
                  leftIcon={<KeyRound className="h-4 w-4" />}
                />
              </div>
              <div className="flex justify-end border-t border-zinc-800 pt-4">
                <Button
                  type="submit"
                  isLoading={saving}
                  className="bg-sky-600 hover:bg-sky-500"
                >
                  <Save className="h-4 w-4" /> Save changes
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
