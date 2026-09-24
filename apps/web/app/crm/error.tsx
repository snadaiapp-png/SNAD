"use client";

import { useEffect } from "react";

/**
 * CRM error boundary — catches unhandled errors in any CRM route.
 *
 * Provides CRM-specific recovery actions (back to overview) in addition
 * to the generic retry. This boundary sits below the root error boundary
 * and catches errors specific to the CRM module.
 */
export default function CrmError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("[SNAD] CRM route error:", error);
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
        CRM Error
      </h1>
      <p
        style={{
          fontSize: "0.875rem",
          color: "var(--snad-color-text-muted, #6b7280)",
          marginBottom: "1.5rem",
          maxWidth: "28rem",
        }}
      >
        An error occurred while loading CRM data. You can try again or return
        to the CRM overview.
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
      <div style={{ display: "flex", gap: "0.75rem" }}>
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
        <a
          href="/crm/overview"
          style={{
            padding: "0.5rem 1.5rem",
            borderRadius: "0.375rem",
            border: "1px solid var(--snad-color-border, #d1d5db)",
            background: "var(--snad-color-bg, #ffffff)",
            color: "var(--snad-color-text, #1a1a1a)",
            fontSize: "0.875rem",
            fontWeight: 500,
            textDecoration: "none",
          }}
        >
          Back to Overview
        </a>
      </div>
    </div>
  );
}
