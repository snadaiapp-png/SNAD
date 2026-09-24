import type { ReactNode } from "react";
import { ExecutiveShell } from "@/components/shell";

export default function ExecutiveLayout({ children }: { children: ReactNode }) {
  return (
    <ExecutiveShell
      logoHref="/executive"
      logoAriaLabel="الذهاب إلى لوحة الإدارة التنفيذية"
    >
      {children}
    </ExecutiveShell>
  );
}
