import type { ReactNode } from "react";
import { ExecutiveHealthPanel } from "./executive-health-panel";

export default function ControlPlaneLayout({ children }: { children: ReactNode }) {
  return <>
    <ExecutiveHealthPanel />
    {children}
  </>;
}
