"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { scpApi, type AccessCheckV2 } from "@/lib/api/scp-api";

/**
 * Unified control-plane authorization source (R0C-12 Blocker C).
 *
 * One provider per console session fetches GET /api/v1/executive/access-check/v2
 * and exposes the capability map to every page through {@link useScpAccess}.
 * Components must not re-implement access-check ad hoc — a single fetch is
 * shared by the navigation and every mutation gate.
 *
 * FAIL-CLOSED CONTRACT (mission Blocker C):
 *   - `checking`   (request in flight)      → has() === false
 *   - `unauthorized` (authenticated=false)  → has() === false
 *   - `degraded`   (network/API failure)    → has() === false
 *   - missing capability key                → has() === false
 * A capability is granted ONLY when the backend answered, authenticated is
 * exactly true, and the map value is exactly true. Server-side authorization
 * remains authoritative regardless of what the UI renders; a hidden control
 * is a UX courtesy, never the security boundary.
 */

export type ScpAccessPhase = "checking" | "authorized" | "unauthorized" | "degraded";

export interface ScpAccessValue {
  phase: ScpAccessPhase;
  /** Raw capability map — empty unless phase === "authorized" (fail-closed). */
  capabilities: Record<string, boolean>;
  /** Convenience: phase === "authorized" (backend said authenticated=true). */
  authenticated: boolean;
  /** True while the access-check request is in flight. */
  loading: boolean;
  /** True when the last access-check attempt failed (degraded). */
  error: boolean;
  /** Fail-closed capability test. */
  has(capability: string): boolean;
  /** Fail-closed conjunction — every listed capability must be granted. */
  hasAll(capabilities: string[]): boolean;
  /** Fail-closed disjunction — at least one listed capability must be granted. */
  hasAny(capabilities: string[]): boolean;
  /** Re-run the access check (retry after degraded). */
  refresh(): void;
}

const ScpAccessContext = createContext<ScpAccessValue | null>(null);

const FAIL_CLOSED: ScpAccessValue = {
  phase: "checking",
  capabilities: {},
  authenticated: false,
  loading: true,
  error: false,
  has: () => false,
  hasAll: () => false,
  hasAny: () => false,
  refresh: () => undefined,
};

export function ScpAccessProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<
    | { phase: "checking" }
    | { phase: "authorized"; access: AccessCheckV2 }
    | { phase: "unauthorized" }
    | { phase: "degraded" }
  >({ phase: "checking" });
  const [attempt, setAttempt] = useState(0);
  const mountedRef = useRef(true);

  const check = useCallback(async () => {
    setState({ phase: "checking" });
    try {
      const result = await scpApi.accessCheckV2();
      if (!mountedRef.current) return;
      if (!result.authenticated) {
        setState({ phase: "unauthorized" });
        return;
      }
      setState({ phase: "authorized", access: result });
    } catch {
      // An unavailable capability service must never read as "allowed".
      if (mountedRef.current) setState({ phase: "degraded" });
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    void check();
    return () => {
      mountedRef.current = false;
    };
  }, [check, attempt]);

  const refresh = useCallback(() => setAttempt((value) => value + 1), []);

  const value = useMemo<ScpAccessValue>(() => {
    if (state.phase !== "authorized") {
      return {
        ...FAIL_CLOSED,
        phase: state.phase,
        loading: state.phase === "checking",
        error: state.phase === "degraded",
        refresh,
      };
    }
    const capabilities = state.access.capabilities;
    const has = (capability: string): boolean => capabilities[capability] === true;
    return {
      phase: "authorized",
      capabilities,
      authenticated: true,
      loading: false,
      error: false,
      has,
      hasAll: (list: string[]) => list.every(has),
      hasAny: (list: string[]) => list.some(has),
      refresh,
    };
  }, [state, refresh]);

  return <ScpAccessContext.Provider value={value}>{children}</ScpAccessContext.Provider>;
}

/**
 * Access the unified capability state. Outside a provider this returns the
 * FAIL-CLOSED default (checking → every has() false) so a missing wrapper can
 * never enable a mutation control by accident.
 */
export function useScpAccess(): ScpAccessValue {
  return useContext(ScpAccessContext) ?? FAIL_CLOSED;
}
