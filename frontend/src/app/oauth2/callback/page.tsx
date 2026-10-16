"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { Button } from "@/components/ui/Button";
import { useRouter } from "next/navigation";

const ERROR_MESSAGES: Record<string, string> = {
  invalid_state: "Your sign-in session expired. Please try again.",
  missing_code: "The identity provider did not return an authorization code.",
  token_exchange_failed:
    "Could not complete sign-in with the identity provider.",
  account_exists: "An account with this email already exists.",
  email_required:
    "Your provider did not share an email address. Make your email public or use another sign-in method.",
  access_denied: "Sign-in was cancelled.",
  server_error: "Something went wrong while signing you in.",
  email_not_verified: "Your email address is not verified by the provider.",
};

function CallbackContent() {
  const params = useSearchParams();
  const router = useRouter();
  const { completeOAuthLogin } = useAuth();
  const [asyncError, setAsyncError] = useState<string | null>(null);
  const started = useRef(false);
  const [initial] = useState(() => ({
    error: params.get("error"),
    ticket: params.get("ticket"),
    signup: params.get("signup"),
  }));

  const urlError = initial.error
    ? (ERROR_MESSAGES[initial.error] ?? `Sign-in failed (${initial.error}).`)
    : !initial.ticket && !initial.signup
      ? "Missing sign-in ticket."
      : null;

  const error = urlError ?? asyncError;

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    window.history.replaceState(null, "", window.location.pathname);

    if (urlError) return;

    if (initial.signup) {
      sessionStorage.setItem("devforge_signup_token", initial.signup);
      router.replace("/complete-signup");
      return;
    }

    completeOAuthLogin(initial.ticket!).catch((err: unknown) => {
      setAsyncError(
        err instanceof Error ? err.message : "Unable to complete sign-in.",
      );
    });
  }, [initial, urlError, router, completeOAuthLogin]);

  if (error) {
    return (
      <div className="w-full max-w-sm space-y-4">
        <ErrorAlert error={error} title="Sign-in failed" />
        <Link href="/login">
          <Button variant="outline" className="w-full">
            Back to sign in
          </Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-3 text-zinc-400">
      <Loader2 className="h-8 w-8 animate-spin text-sky-400" />
      <p className="text-xs">Completing sign-in...</p>
    </div>
  );
}

export default function OAuth2CallbackPage() {
  return (
    <div className="min-h-screen bg-zinc-950 flex items-center justify-center px-4">
      <Suspense fallback={null}>
        <CallbackContent />
      </Suspense>
    </div>
  );
}
