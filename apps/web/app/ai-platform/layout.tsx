import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "منصة الذكاء الاصطناعي | SNAD",
  description: "لوحة إدارة وكلاء الذكاء الاصطناعي",
};

export default function AiPlatformLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
