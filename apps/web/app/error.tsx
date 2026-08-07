"use client";

import { useEffect } from "react";

/**
 * Root error boundary — catches unhandled errors in any route.
 *
 * Next.js calls this component when a rendering error escapes a page.
 * It receives the `error` object and a `reset` function to retry rendering.
 *
 * This is a client component ("use client") because error boundaries
 * must be interactive (reset button) and access browser APIs.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Log error to console in development for debugging.
    // In production, this could be wired to an APM service (Sentry, etc.).
    console.error("[SNAD] Unhandled route error:", error);
  }, [error]);

  return (
    <div
      role="alert"
      aria-live="assertive"
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "60vh",
        padding: "2rem",
        fontFamily: "var(--font-snad-latin), system-ui, sans-serif",
        textAlign: "center",
      }}
    >
      <h1
        style={{
          fontSize: "1.5rem",
          fontWeight: 600,
          marginBottom: "0.5rem",
          color: "var(--snad-color-text, #1a1a1a)",
        }}
      >
        Something went wrong
      </h1>
      <p
        style={{
          fontSize: "0.875rem",
          color: "var(--snad-color-text-muted, #6b7280)",
          marginBottom: "1.5rem",
          maxWidth: "28rem",
        }}
      >
        An unexpected error occurred. Please try again or contact support if the
        problem persists.
      </p>
      {error.digest && (
        <p
          style={{
            fontSize: "0.75rem",
            color: "var(--snad-color-text-muted, #9ca3af)",
            marginBottom: "1rem",
            fontFamily: "monospace",
          }}
        >
          Error ID: {error.digest}
        </p>
      )}
      <button
        onClick={() => reset()}
        style={{
          padding: "0.5rem 1.5rem",
          borderRadius: "0.375rem",
          border: "1px solid var(--snad-color-border, #d1d5db)",
          background: "var(--snad-color-bg, #ffffff)",
          color: "var(--snad-color-text, #1a1a1a)",
          fontSize: "0.875rem",
          fontWeight: 500,
          cursor: "pointer",
        }}
      >
        Try again
      </button>
    </div>
  );
}
