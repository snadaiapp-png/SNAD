import type { ReactNode } from "react";
import { ExecutiveShell } from "@/components/shell";
import Link from "next/link";

export default function SystemHealthLayout({ children }: { children: ReactNode }) {
  return (
    <ExecutiveShell
      logoHref="/control-plane"
      logoAriaLabel="الذهاب إلى مركز الإدارة العليا"
    >
      <div style={{ marginBottom: "1rem" }}>
        <Link
          href="/control-plane"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "0.5rem",
            padding: "0.5rem 1rem",
            borderRadius: "0.5rem",
            background: "var(--snad-surface-2, #f3f4f6)",
            color: "var(--snad-text-primary, #111827)",
            textDecoration: "none",
            fontSize: "0.875rem",
            fontWeight: 500,
          }}
        >
          ← العودة إلى مركز الإدارة العليا
        </Link>
      </div>
      {children}
    </ExecutiveShell>
  );
}
