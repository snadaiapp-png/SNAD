import type { Metadata } from "next";
import { Noto_Sans, Noto_Sans_Arabic } from "next/font/google";
import "./globals.css";
import { Providers } from "./providers";

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
  title: "SNAD | سند — نظام تشغيل الأعمال",
  description:
    "SNAD Business Operating System — منصة سند العربية لإدارة المؤسسات والعضويات والمستخدمين.",
};

/**
 * Inline script that runs BEFORE React hydration to:
 *   1. Apply the stored theme (light/dark/system) to <html data-theme>.
 *   2. Apply the stored locale (ar/en) to <html lang dir>.
 *
 * This prevents:
 *   - Flash of Incorrect Theme (FOIT/FOUC) — the wrong theme would flash
 *     for ~1 frame before React mounts and ThemeProvider runs.
 *   - Flash of Incorrect Locale (FOIL) — the wrong lang/dir would flash.
 *   - Hydration mismatch — React's initial render matches what the script
 *     already applied, so suppressHydrationWarning covers the rest.
 *
 * The script is intentionally minimal and side-effect free: it only reads
 * localStorage and sets attributes on <html>. No PII, no tokens, no network.
 */
const NO_FLASH_SCRIPT = `
(function() {
  try {
    // === Theme ===
    var themeMode = 'system';
    try { themeMode = localStorage.getItem('snad.theme') || 'system'; } catch (e) {}
    var prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    var resolved = themeMode === 'system' ? (prefersDark ? 'dark' : 'light') : themeMode;
    document.documentElement.setAttribute('data-theme', resolved);
    document.documentElement.style.colorScheme = resolved;

    // === Locale ===
    var locale = 'ar';
    try { locale = localStorage.getItem('snad.locale') || 'ar'; } catch (e) {}
    if (locale !== 'ar' && locale !== 'en') locale = 'ar';
    document.documentElement.lang = locale;
    document.documentElement.dir = locale === 'en' ? 'ltr' : 'rtl';
  } catch (e) {
    // Fail safe: defaults are already ar/rtl/light via the static HTML.
  }
})();
`;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ar" dir="rtl" data-theme="light" suppressHydrationWarning>
      <head>
        <script
          dangerouslySetInnerHTML={{ __html: NO_FLASH_SCRIPT }}
        />
      </head>
      <body className={`${arabicFont.variable} ${latinFont.variable}`}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
