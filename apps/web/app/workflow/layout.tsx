import type { Metadata } from "next";
import { WorkflowRtlDocument } from "./components/workflow-rtl-document";

export const metadata: Metadata = {
  title: "محرك سير العمل | SNAD",
  description: "لوحة إدارة سير العمل والموافقات",
};

export default function WorkflowLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <WorkflowRtlDocument />
      {children}
    </>
  );
}
