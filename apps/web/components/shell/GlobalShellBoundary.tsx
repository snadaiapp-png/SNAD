"use client";

import dynamic from "next/dynamic";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { useAuth } from "@/lib/auth/auth-provider";

const ExecutiveShell = dynamic(
  () => import("./ExecutiveShell").then((module) => module.ExecutiveShell),
  { ssr: false },
);

const PUBLIC_PREFIXES = ["/auth", "/api"];

export function GlobalShellBoundary({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { state } = useAuth();

  const isPublic =
    pathname === "/" ||
    PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix));

  if (isPublic || state !== "AUTHENTICATED") {
    return <>{children}</>;
  }

  return (
    <ExecutiveShell
      logoHref={pathname.startsWith("/control-plane")
        ? "/control-plane"
        : "/workspace"}
    >
      {children}
    </ExecutiveShell>
  );
}
