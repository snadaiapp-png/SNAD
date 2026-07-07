import type { ReactNode } from "react";
import { ExecutiveHealthPanel } from "./executive-health-panel";
import { ExecutiveShell } from "@/components/shell";

export default function ControlPlaneLayout({ children }: { children: ReactNode }) {
  return (
    <ExecutiveShell
      logoHref="/control-plane"
      logoAriaLabel="الذهاب إلى مركز الإدارة العليا"
    >
      <ExecutiveHealthPanel />
      {children}
    </ExecutiveShell>
  );
}
