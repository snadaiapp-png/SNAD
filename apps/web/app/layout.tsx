import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "SANAD | نظام تشغيل الأعمال",
  description: "واجهة SANAD العربية لإدارة المؤسسات والعضويات والمستخدمين.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ar" dir="rtl">
      <body>{children}</body>
    </html>
  );
}
