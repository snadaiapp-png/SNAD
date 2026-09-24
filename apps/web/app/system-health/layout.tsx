import type { ReactNode } from "react";
import { ExecutiveShell } from "@/components/shell";

export default function SystemHealthLayout({ children }: { children: ReactNode }) {
  return (
    <ExecutiveShell
      logoHref="/system-health"
      logoAriaLabel="الذهاب إلى لوحة صحة النظام"
    >
      {children}
    </ExecutiveShell>
  );
}
