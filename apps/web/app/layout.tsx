import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "SANAD | Business Operating System",
  description: "SANAD multi-tenant business operations workspace.",
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
