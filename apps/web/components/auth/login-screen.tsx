"use client";

import { LoginForm } from "./login-form";
import { LoginV3Shell } from "./login-v3-shell";
import type { UserFacingError } from "@/lib/api/user-facing-errors";

interface LoginScreenProps {
  onLogin: (email: string, password: string) => Promise<void>;
  authenticating: boolean;
  error: UserFacingError | null;
  sessionExpired?: boolean;
  onRetrySession?: () => Promise<void>;
}

export function LoginScreen({
  onLogin,
  authenticating,
  error,
  sessionExpired = false,
  onRetrySession,
}: LoginScreenProps) {
  return (
    <LoginV3Shell>
      <LoginForm
        onLogin={onLogin}
        authenticating={authenticating}
        error={error}
        sessionExpired={sessionExpired}
        onRetrySession={onRetrySession}
      />
    </LoginV3Shell>
  );
}
