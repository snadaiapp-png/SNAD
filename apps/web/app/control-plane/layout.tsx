import type { ReactNode } from "react";
import { ExecutiveShell } from "@/components/shell";

export default function ControlPlaneLayout({ children }: { children: ReactNode }) {
  return (
    <ExecutiveShell
      logoHref="/executive"
      logoAriaLabel="الذهاب إلى لوحة الإدارة التنفيذية"
    >
      {children}
    </ExecutiveShell>
  );
}
