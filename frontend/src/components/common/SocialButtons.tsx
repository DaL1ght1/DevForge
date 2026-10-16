"use client";

import { Button } from "@/components/ui/Button";
import { authApi } from "@/lib/api/auth";
import { SocialProvider } from "@/types/api";

const GoogleIcon = () => (
  <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true">
    <path
      fill="#EA4335"
      d="M12 10.2v3.9h5.5c-.24 1.4-1.7 4.1-5.5 4.1a6.2 6.2 0 0 1 0-12.4c1.97 0 3.3.84 4.05 1.56l2.76-2.66C17.04 3.2 14.76 2.2 12 2.2a9.8 9.8 0 1 0 0 19.6c5.66 0 9.4-3.98 9.4-9.58 0-.64-.07-1.13-.16-1.62H12z"
    />
  </svg>
);

const GitHubIcon = () => (
  <svg viewBox="0 0 24 24" className="h-4 w-4 fill-current" aria-hidden="true">
    <path d="M12 .5a11.5 11.5 0 0 0-3.64 22.41c.58.1.79-.25.79-.56v-2c-3.2.7-3.88-1.54-3.88-1.54-.52-1.33-1.28-1.69-1.28-1.69-1.04-.71.08-.7.08-.7 1.15.08 1.76 1.18 1.76 1.18 1.03 1.76 2.7 1.25 3.35.96.1-.75.4-1.25.73-1.54-2.56-.29-5.25-1.28-5.25-5.7 0-1.26.45-2.29 1.18-3.1-.12-.29-.51-1.46.11-3.05 0 0 .97-.31 3.18 1.18a11 11 0 0 1 5.78 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.24 2.76.12 3.05.74.81 1.18 1.84 1.18 3.1 0 4.43-2.7 5.4-5.27 5.69.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.5 11.5 0 0 0 12 .5z" />
  </svg>
);

export function SocialButtons({ label = "" }: { label?: string }) {
  const start = (provider: SocialProvider) =>
    authApi.startSocialLogin(provider);

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3">
        <Button type="button" variant="outline" onClick={() => start("google")}>
          <GoogleIcon /> {label} Google
        </Button>
        <Button type="button" variant="outline" onClick={() => start("github")}>
          <GitHubIcon /> {label} GitHub
        </Button>
      </div>
      <div className="flex items-center gap-3 text-[11px] uppercase tracking-wider text-zinc-500">
        <span className="h-px flex-1 bg-zinc-800" />
        or
        <span className="h-px flex-1 bg-zinc-800" />
      </div>
    </div>
  );
}
