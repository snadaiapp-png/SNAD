import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "مركز القيادة التنفيذية | SNAD",
  description: "لوحة القيادة التنفيذية للإدارة العليا",
};

export default function ManagementLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
