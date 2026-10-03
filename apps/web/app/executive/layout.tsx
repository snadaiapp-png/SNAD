import type { ReactNode } from "react";
import { ScpLayout } from "./_components/ScpLayout";

export default function ExecutiveLayout({ children }: { children: ReactNode }) {
  return <ScpLayout>{children}</ScpLayout>;
}
