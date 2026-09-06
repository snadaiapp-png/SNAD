import type { ReactNode } from "react";
import { ScpExecutiveShell } from "./_components/ScpExecutiveShell";
import { ScpLayout } from "./_components/ScpLayout";

export default function ExecutiveLayout({ children }: { children: ReactNode }) {
  return (
    <ScpExecutiveShell>
      <ScpLayout>{children}</ScpLayout>
    </ScpExecutiveShell>
  );
}
