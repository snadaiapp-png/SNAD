"use client";

import styles from "./auth.module.css";
import v2Styles from "./auth-login-v2.module.css";
import { AuthIntelligenceVisual } from "./auth-intelligence-visual";
import { LoginForm } from "./login-form";
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
    <div className={styles.authShell}>
      <AuthIntelligenceVisual />
      <div className={`${styles.loginPanel} ${v2Styles.loginPanel}`}>
        <LoginForm
          onLogin={onLogin}
          authenticating={authenticating}
          error={error}
          sessionExpired={sessionExpired}
          onRetrySession={onRetrySession}
        />
      </div>
    </div>
  );
}
