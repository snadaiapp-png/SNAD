import type { ReactNode } from "react";
import { ExecutiveShell } from "@/components/shell";

export default function ControlPlaneLayout({ children }: { children: ReactNode }) {
  return (
    <ExecutiveShell
      logoHref="/control-plane"
      logoAriaLabel="الذهاب إلى مركز الإدارة العليا"
    >
      {children}
    </ExecutiveShell>
  );
}
