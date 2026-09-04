import type { ReactNode } from "react";
import { ExecutiveShell } from "@/components/shell";
import { ScpLayout } from "./_components/ScpLayout";

export default function ExecutiveLayout({ children }: { children: ReactNode }) {
  return (
    <ExecutiveShell
      logoHref="/executive"
      logoAriaLabel="الذهاب إلى لوحة الإدارة التنفيذية"
    >
      <ScpLayout>{children}</ScpLayout>
    </ExecutiveShell>
  );
}
