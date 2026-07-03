"use client";

import styles from "./auth.module.css";
import { AuthIntelligenceVisual } from "./auth-intelligence-visual";
import { LoginForm } from "./login-form";
import type { UserFacingError } from "@/lib/api/user-facing-errors";

interface LoginScreenProps {
  onLogin: (email: string, password: string) => Promise<void>;
  authenticating: boolean;
  error: UserFacingError | null;
  sessionExpired?: boolean;
}

export function LoginScreen({
  onLogin,
  authenticating,
  error,
  sessionExpired = false,
}: LoginScreenProps) {
  return (
    <div className={styles.authShell}>
      <AuthIntelligenceVisual />
      <div className={styles.loginPanel}>
        <LoginForm
          onLogin={onLogin}
          authenticating={authenticating}
          error={error}
          sessionExpired={sessionExpired}
        />
      </div>
    </div>
  );
}
