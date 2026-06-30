"use client";

import styles from "./auth.module.css";
import type { UserFacingError } from "@/lib/api/user-facing-errors";

interface AuthErrorAlertProps {
  error: UserFacingError;
}

export function AuthErrorAlert({ error }: AuthErrorAlertProps) {
  return (
    <div className={styles.authError} role="alert" aria-live="assertive">
      <svg
        className={styles.authErrorIcon}
        viewBox="0 0 20 20"
        fill="currentColor"
        aria-hidden="true"
      >
        <path
          fillRule="evenodd"
          d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z"
          clipRule="evenodd"
        />
      </svg>
      <div className={styles.authErrorContent}>
        <p className={styles.authErrorTitle}>{error.title}</p>
        <p className={styles.authErrorMessage}>{error.message}</p>
      </div>
    </div>
  );
}
