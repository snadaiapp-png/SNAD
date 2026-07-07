import type { Metadata } from "next";
import { cookies } from "next/headers";
import { Noto_Sans, Noto_Sans_Arabic } from "next/font/google";
import "./globals.css";
import { Providers, type Locale } from "./providers";
import type { ThemePreference } from "@/lib/hooks/useTheme";

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

const themeBootstrap = `
(() => {
  const root = document.documentElement;
  let preference = root.dataset.themePreference || "system";
  try {
    const stored = localStorage.getItem("snad-theme");
    if (stored === "light" || stored === "dark" || stored === "system") preference = stored;
  } catch {}
  const resolved = preference === "system"
    ? (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light")
    : preference;
  root.dataset.themePreference = preference;
  root.dataset.theme = resolved;
  root.style.colorScheme = resolved;
})();`;

export const metadata: Metadata = {
  title: "SNAD | سند — Business Operating System",
  description: "Bilingual business operating system for intelligent organizational operations.",
};

function localeValue(value: string | undefined): Locale {
  return value === "en" ? "en" : "ar";
}

function themeValue(value: string | undefined): ThemePreference {
  return value === "light" || value === "dark" || value === "system"
    ? value
    : "system";
}

export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const cookieStore = await cookies();
  const locale = localeValue(cookieStore.get("snad-locale")?.value);
  const themePreference = themeValue(cookieStore.get("snad-theme")?.value);

  return (
    <html
      lang={locale}
      dir={locale === "ar" ? "rtl" : "ltr"}
      data-locale={locale}
      data-theme-preference={themePreference}
      data-theme={themePreference === "system" ? undefined : themePreference}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeBootstrap }} />
      </head>
      <body className={`${arabicFont.variable} ${latinFont.variable}`}>
        <Providers initialLocale={locale}>{children}</Providers>
      </body>
    </html>
  );
}
