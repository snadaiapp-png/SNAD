/**
 * Tenant Context Provider — derives tenant context from authenticated session.
 *
 * Reads the authenticated user's tenantId from the AuthProvider and
 * automatically injects it into all tenant-scoped API calls. This
 * removes the need for manual Tenant UUID entry on any panel.
 *
 * Usage:
 *   const { tenantId } = useTenantContext();
 *   await organizationsApi.list(tenantId);
 */

"use client";

import { createContext, useContext, useMemo, type ReactNode } from "react";
import { useAuth } from "./auth-provider";

interface TenantContextValue {
  /** The authenticated user's tenant ID. null if not authenticated. */
  tenantId: string | null;
  /** True if tenant context is available. */
  isReady: boolean;
}

const TenantContext = createContext<TenantContextValue | null>(null);

export function TenantContextProvider({ children }: { children: ReactNode }) {
  const { user, state } = useAuth();

  const value = useMemo<TenantContextValue>(
    () => ({
      tenantId: user?.tenantId ?? null,
      isReady: state === "AUTHENTICATED" && !!user?.tenantId,
    }),
    [user, state]
  );

  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>;
}

export function useTenantContext(): TenantContextValue {
  const ctx = useContext(TenantContext);
  if (!ctx) throw new Error("useTenantContext must be used within TenantContextProvider");
  return ctx;
}
