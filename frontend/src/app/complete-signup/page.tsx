"use client";

import Link from "next/link";
import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Mail,
  Terminal,
  User,
  UserPlus,
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { authApi } from "@/lib/api/auth";
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

const KEY = "devforge_signup_token";

export default function CompleteSignupPage() {
  const router = useRouter();
  const { completeSocialSignup } = useAuth();
  const [form, setForm] = useState({
    email: "",
    username: "",
    firstName: "",
    lastName: "",
    password: "",
  });
  const [showPassword, setShowPassword] = useState(false);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const token = sessionStorage.getItem(KEY);
    if (!token) {
      router.replace("/login");
      return;
    }
    authApi
      .getPendingSignup(token)
      .then((p) => {
        setForm((f) => ({ ...f, ...p, password: "" }));
        setReady(true);
      })
      .catch(() => {
        sessionStorage.removeItem(KEY);
        router.replace("/login");
      });
  }, [router]);

  const onChange = (key: keyof typeof form, value: string) =>
    setForm((f) => ({ ...f, [key]: value }));

  const handleSubmit = async (e: React.SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    if (form.password.length < 8) {
      setError("Password must contain at least 8 characters");
      return;
    }
    const token = sessionStorage.getItem(KEY);
    if (!token) {
      router.replace("/login");
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.completeSignup({
        signupToken: token,
        username: form.username,
        firstName: form.firstName,
        lastName: form.lastName,
        password: form.password,
      });
      sessionStorage.removeItem(KEY);
      await completeSocialSignup(res);
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Unable to complete sign-up",
      );
    } finally {
      setLoading(false);
    }
  };

  if (!ready) {
    return (
      <div className="min-h-screen bg-zinc-950 flex items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-sky-400" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-950 flex flex-col justify-center px-4 py-12 text-zinc-100">
      <div className="mx-auto w-full max-w-lg">
        <div className="flex items-center justify-center gap-2.5 mb-6">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg border border-zinc-700 bg-zinc-900 text-sky-400">
            <Terminal className="h-4 w-4" />
          </div>
          <span className="font-mono text-sm font-bold tracking-wider">
            DEVFORGE IDP
          </span>
        </div>
        <Card className="border-zinc-800 bg-zinc-900/80 shadow-lg">
          <CardHeader className="space-y-1 text-center pb-4">
            <CardTitle className="text-xl font-bold">
              Complete your sign up
            </CardTitle>
            <CardDescription className="text-xs text-zinc-400">
              Choose a username and password to finish creating your account
            </CardDescription>
          </CardHeader>
          <CardContent className="pt-2">
            <form onSubmit={handleSubmit} className="space-y-4">
              <ErrorAlert error={error} />
              <Input
                label="Email"
                id="email"
                value={form.email}
                leftIcon={<Mail className="h-4 w-4" />}
                readOnly
                disabled
              />
              <div className="grid gap-4 sm:grid-cols-2">
                <Input
                  label="First Name"
                  id="firstName"
                  value={form.firstName}
                  onChange={(e) => onChange("firstName", e.target.value)}
                  required
                />
                <Input
                  label="Last Name"
                  id="lastName"
                  value={form.lastName}
                  onChange={(e) => onChange("lastName", e.target.value)}
                  required
                />
              </div>
              <Input
                label="Username"
                id="username"
                value={form.username}
                onChange={(e) => onChange("username", e.target.value)}
                leftIcon={<User className="h-4 w-4" />}
                required
              />
              <Input
                label="Password"
                id="password"
                type={showPassword ? "text" : "password"}
                value={form.password}
                onChange={(e) => onChange("password", e.target.value)}
                autoComplete="new-password"
                placeholder="At least 8 characters"
                leftIcon={<KeyRound className="h-4 w-4" />}
                rightIcon={
                  <button
                    type="button"
                    tabIndex={-1}
                    onClick={() => setShowPassword(!showPassword)}
                    className="text-zinc-400 hover:text-zinc-200"
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
              <Button
                type="submit"
                variant="primary"
                isLoading={loading}
                className="w-full mt-2 bg-sky-600 hover:bg-sky-500 text-white"
              >
                <UserPlus className="h-4 w-4 mr-2" /> Finish sign up
              </Button>
              <div className="text-center text-xs text-zinc-500 pt-3 border-t border-zinc-800/80">
                <Link href="/login" className="text-sky-400 hover:text-sky-300">
                  Cancel and go back
                </Link>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
