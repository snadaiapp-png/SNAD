import type { Metadata } from "next";
import { Noto_Sans, Noto_Sans_Arabic } from "next/font/google";
import "./globals.css";

const arabicFont = Noto_Sans_Arabic({
  subsets: ["arabic"],
  display: "swap",
  variable: "--font-snad-arabic",
  weight: ["400", "500", "600", "700", "800"],
});

const latinFont = Noto_Sans({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-snad-latin",
  weight: ["400", "500", "600", "700", "800"],
});

export const metadata: Metadata = {
  title: "SNAD | نظام تشغيل الأعمال",
  description: "واجهة سند العربية لإدارة المؤسسات والعضويات والمستخدمين.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ar" dir="rtl" suppressHydrationWarning>
      <body className={`${arabicFont.variable} ${latinFont.variable}`}>{children}</body>
    </html>
  );
}
