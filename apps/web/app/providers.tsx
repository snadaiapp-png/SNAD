"use client";

import type { ReactNode } from "react";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { TenantContextProvider } from "@/lib/auth/tenant-context";
import { GlobalShellBoundary } from "@/components/shell/GlobalShellBoundary";

export function Providers({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <TenantContextProvider>
        <GlobalShellBoundary>{children}</GlobalShellBoundary>
      </TenantContextProvider>
    </AuthProvider>
  );
}
