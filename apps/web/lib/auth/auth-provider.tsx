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
import {
  authApi,
  type AuthResponse,
  type AuthUser,
  type LoginRequest,
  type MeResponse,
  AmbiguousTenantError,
} from "@/lib/api/auth";
import { apiClient } from "@/lib/api/client";
import { toUserFacingError, type UserFacingError } from "@/lib/api/user-facing-errors";
import { SingleFlight } from "@/lib/auth/single-flight";

export type AuthState =
  | "INITIALIZING"
  | "ANONYMOUS"
  | "AUTHENTICATING"
  | "AUTHENTICATED"
  | "REFRESHING"
  | "EXPIRED"
  | "ERROR"
  | "AMBIGUOUS_TENANT"
  | "LOGGING_OUT"
  | "CREDENTIAL_ROTATION_REQUIRED";

interface AuthContextValue {
  state: AuthState;
  user: AuthUser | null;
  me: MeResponse | null;
  error: UserFacingError | null;
  ambiguousTenantIds: string[];
  lastLoginEmail: string;
  login: (req: LoginRequest) => Promise<void>;
  loginWithTenant: (tenantId: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  loadMe: () => Promise<void>;
  dismissAmbiguousTenant: () => void;
  changeCredential: (currentPassword: string, newPassword: string) => Promise<void>;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * In-memory session store — access tokens are NEVER written to localStorage,
 * sessionStorage, or cookies. They survive SPA navigation but not page reload.
 * On reload, session is restored via silent refresh using the HttpOnly refresh cookie.
 *
 * Expiry is kept in memory for proactive/anticipatory refresh decisions.
 */
function useInMemorySession() {
  const accessTokenRef = useRef<string | null>(null);
  const expiresAtRef = useRef<string | null>(null);

  const setSession = useCallback((accessToken: string, expiresAt: string) => {
    accessTokenRef.current = accessToken;
    expiresAtRef.current = expiresAt;
    apiClient.setDefaultHeader("Authorization", `Bearer ${accessToken}`);
  }, []);

  const clearSession = useCallback(() => {
    accessTokenRef.current = null;
    expiresAtRef.current = null;
    apiClient.removeDefaultHeader("Authorization");
  }, []);

  const getAccessToken = useCallback(() => accessTokenRef.current, []);
  const getExpiresAt = useCallback(() => expiresAtRef.current, []);

  return useMemo(
    () => ({
      setSession,
      clearSession,
      getAccessToken,
      getExpiresAt,
    }),
    [
      setSession,
      clearSession,
      getAccessToken,
      getExpiresAt,
    ],
  );
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>("INITIALIZING");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [me, setMe] = useState<MeResponse | null>(null);
  const [error, setError] = useState<UserFacingError | null>(null);
  const [ambiguousTenantIds, setAmbiguousTenantIds] = useState<string[]>([]);
  const [lastLoginEmail, setLastLoginEmail] = useState("");
  const lastLoginPasswordRef = useRef<string>("");
  const refreshFlightRef = useRef<SingleFlight<AuthResponse> | null>(null);
  const refreshEnabledRef = useRef(true);
  const sessionGenerationRef = useRef(0);
  const session = useInMemorySession();

  if (refreshFlightRef.current === null) {
    refreshFlightRef.current = new SingleFlight<AuthResponse>();
  }

  /**
   * Rotate the HttpOnly refresh session exactly once for all concurrent callers.
   * Refresh tokens are rotated by the backend; parallel refresh requests can
   * otherwise make one request look like a replay and incorrectly expire a
   * valid browser session.
   */
  const runRefresh = useCallback((): Promise<AuthResponse> => {
    if (!refreshEnabledRef.current) {
      return Promise.reject(new Error("Session refresh is disabled"));
    }

    const generation = sessionGenerationRef.current;
    const flight = refreshFlightRef.current;
    if (!flight) return Promise.reject(new Error("Session refresh is unavailable"));

    return flight.run(async () => {
      const response = await authApi.refresh();

      // A logout or a new login invalidates any older in-flight refresh result.
      // Never let a late response restore a session the user already ended.
      if (!refreshEnabledRef.current || generation !== sessionGenerationRef.current) {
        throw new Error("Stale session refresh result");
      }

      session.setSession(response.accessToken, response.expiresAt);
      setUser(response.user);
      return response;
    });
  }, [session]);

  // Register auto-refresh handler: when any API call receives HTTP 401, all
  // concurrent callers share one refresh rotation and retry only after it wins.
  useEffect(() => {
    apiClient.setUnauthorizedHandler(async () => {
      try {
        await runRefresh();
        setState((current) =>
          current === "CREDENTIAL_ROTATION_REQUIRED" ? current : "AUTHENTICATED",
        );
        return true;
      } catch {
        session.clearSession();
        setUser(null);
        setMe(null);
        setState("EXPIRED");
        return false;
      }
    });
    return () => { apiClient.setUnauthorizedHandler(null); };
  }, [runRefresh, session]);

  // Bootstrap: attempt silent refresh via the HttpOnly refresh cookie to restore session.
  // If refresh succeeds, load /me for full profile. If it fails, go to ANONYMOUS.
  useEffect(() => {
    if (state !== "INITIALIZING") return;

    let cancelled = false;

    runRefresh()
      .then(() => {
        if (cancelled) return undefined;
        return authApi.me();
      })
      .then((meData) => {
        if (cancelled || !meData) return;
        setMe(meData);
        if (meData.credentialRotationRequired) {
          setState("CREDENTIAL_ROTATION_REQUIRED");
        } else {
          setState("AUTHENTICATED");
        }
      })
      .catch(() => {
        if (cancelled) return;
        session.clearSession();
        setUser(null);
        setMe(null);
        setState("ANONYMOUS");
      });

    return () => { cancelled = true; };
  }, [state, runRefresh, session]);

  const login = useCallback(async (req: LoginRequest) => {
    refreshEnabledRef.current = true;
    sessionGenerationRef.current += 1;
    setState("AUTHENTICATING");
    setError(null);
    setAmbiguousTenantIds([]);
    setLastLoginEmail(req.email);
    lastLoginPasswordRef.current = req.password;
    try {
      const res = await authApi.login(req);
      session.setSession(res.accessToken, res.expiresAt);
      setUser(res.user);

      const meData = await authApi.me();
      setMe(meData);
      if (meData.credentialRotationRequired) {
        setState("CREDENTIAL_ROTATION_REQUIRED");
      } else {
        setState("AUTHENTICATED");
      }
      lastLoginPasswordRef.current = "";
    } catch (err) {
      if (err instanceof AmbiguousTenantError) {
        setAmbiguousTenantIds(err.tenantIds);
        setState("AMBIGUOUS_TENANT");
      } else {
        setError(toUserFacingError(err));
        setState("ERROR");
        lastLoginPasswordRef.current = "";
      }
    }
  }, [session]);

  /** Re-login with a specific tenantId after an ambiguous tenant 409. */
  const loginWithTenant = useCallback(async (tenantId: string) => {
    refreshEnabledRef.current = true;
    sessionGenerationRef.current += 1;
    setState("AUTHENTICATING");
    setError(null);
    try {
      const res = await authApi.login({
        email: lastLoginEmail,
        password: lastLoginPasswordRef.current,
        tenantId,
      });
      session.setSession(res.accessToken, res.expiresAt);
      setUser(res.user);

      const meData = await authApi.me();
      setMe(meData);
      if (meData.credentialRotationRequired) {
        setState("CREDENTIAL_ROTATION_REQUIRED");
      } else {
        setState("AUTHENTICATED");
      }
      lastLoginPasswordRef.current = "";
    } catch (err) {
      if (err instanceof AmbiguousTenantError) {
        setAmbiguousTenantIds(err.tenantIds);
        setState("AMBIGUOUS_TENANT");
      } else {
        setError(toUserFacingError(err));
        setState("ERROR");
        lastLoginPasswordRef.current = "";
      }
    }
  }, [lastLoginEmail, session]);

  /** Dismiss the tenant picker and go back to login form. */
  const dismissAmbiguousTenant = useCallback(() => {
    setAmbiguousTenantIds([]);
    setState("ANONYMOUS");
    lastLoginPasswordRef.current = "";
  }, []);

  const logout = useCallback(async () => {
    // Invalidate in-flight refresh work before the network request. The BFF also
    // clears the first-party cookie even when upstream revocation is unavailable.
    refreshEnabledRef.current = false;
    sessionGenerationRef.current += 1;
    setState("LOGGING_OUT");
    try {
      await authApi.logout();
    } catch {
      // Local logout remains authoritative. The operational synthetic reports
      // upstream revocation failures separately.
    }
    session.clearSession();
    setUser(null);
    setMe(null);
    setError(null);
    setState("ANONYMOUS");
    lastLoginPasswordRef.current = "";
  }, [session]);

  const refresh = useCallback(async () => {
    refreshEnabledRef.current = true;
    setState("REFRESHING");
    try {
      await runRefresh();
      setState("AUTHENTICATED");
    } catch (err) {
      session.clearSession();
      setUser(null);
      setMe(null);
      setError(toUserFacingError(err));
      setState("EXPIRED");
      throw err;
    }
  }, [runRefresh, session]);

  const loadMe = useCallback(async () => {
    try {
      const meData = await authApi.me();
      setMe(meData);
    } catch (err) {
      setError(toUserFacingError(err));
    }
  }, []);

  /** Change credential (self-service password rotation). */
  const changeCredential = useCallback(async (currentPassword: string, newPassword: string) => {
    setError(null);
    try {
      await authApi.changeCredential({
        currentCredential: currentPassword,
        newCredential: newPassword,
      });
      const meData = await authApi.me();
      setMe(meData);
      setState("AUTHENTICATED");
    } catch (err) {
      setError(toUserFacingError(err));
      throw err;
    }
  }, []);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const value = useMemo(
    () => ({ state, user, me, error, ambiguousTenantIds, lastLoginEmail, login, loginWithTenant, dismissAmbiguousTenant, logout, refresh, loadMe, changeCredential, clearError }),
    [state, user, me, error, ambiguousTenantIds, lastLoginEmail, login, loginWithTenant, dismissAmbiguousTenant, logout, refresh, loadMe, changeCredential, clearError],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
