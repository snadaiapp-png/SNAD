"use client";

import type { ReactNode } from "react";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { TenantContextProvider } from "@/lib/auth/tenant-context";

export function Providers({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <TenantContextProvider>
        {children}
      </TenantContextProvider>
    </AuthProvider>
  );
}
