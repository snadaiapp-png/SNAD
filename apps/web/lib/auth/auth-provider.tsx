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
import { authApi, type AuthUser, type LoginRequest, type MeResponse, AmbiguousTenantError } from "@/lib/api/auth";
import { apiClient } from "@/lib/api/client";
import { toUserFacingError, type UserFacingError } from "@/lib/api/user-facing-errors";

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
  // On client, always start as INITIALIZING so the bootstrap effect can attempt
  // a silent refresh via the HttpOnly refresh cookie.
  const [state, setState] = useState<AuthState>("INITIALIZING");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [me, setMe] = useState<MeResponse | null>(null);
  const [error, setError] = useState<UserFacingError | null>(null);
  const [ambiguousTenantIds, setAmbiguousTenantIds] = useState<string[]>([]);
  const [lastLoginEmail, setLastLoginEmail] = useState("");
  const lastLoginPasswordRef = useRef<string>("");
  const refreshPromiseRef = useRef<Promise<void> | null>(null);
  const session = useInMemorySession();

  // Register auto-refresh handler: when any API call receives 401, attempt to
  // refresh the token. Return true if refresh succeeded (caller retries), false otherwise.
  // This prevents 401 errors when the 15-minute access token expires mid-session.
  useEffect(() => {
    apiClient.setUnauthorizedHandler(async () => {
      // If already refreshing, wait for that to complete.
      if (refreshPromiseRef.current) {
        try {
          await refreshPromiseRef.current;
          return true;
        } catch {
          return false;
        }
      }
      try {
        const res = await authApi.refresh();
        session.setSession(res.accessToken, res.expiresAt);
        setUser(res.user);
        setState("AUTHENTICATED");
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
  }, [session]);

  // Bootstrap: attempt silent refresh via HttpOnly refresh cookie to restore session.
  // If refresh succeeds, load /me for full profile. If it fails, go to ANONYMOUS.
  useEffect(() => {
    if (state !== "INITIALIZING") return;

    let cancelled = false;

    authApi
      .refresh()
      .then((res) => {
        if (cancelled) return;
        session.setSession(res.accessToken, res.expiresAt);
        setUser(res.user);

        // Load full profile (/me) after restoring token
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
  }, [state, session]);

  const login = useCallback(async (req: LoginRequest) => {
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
      // Check if credential rotation is required after login
      if (meData.credentialRotationRequired) {
        setState("CREDENTIAL_ROTATION_REQUIRED");
      } else {
        setState("AUTHENTICATED");
      }
      // Security: clear the in-memory password as soon as login succeeds.
      lastLoginPasswordRef.current = "";
    } catch (err) {
      if (err instanceof AmbiguousTenantError) {
        setAmbiguousTenantIds(err.tenantIds);
        setState("AMBIGUOUS_TENANT");
        // Keep lastLoginPasswordRef for loginWithTenant — cleared after tenant selection.
      } else {
        setError(toUserFacingError(err));
        setState("ERROR");
        // Security: clear the in-memory password on non-ambiguous failure.
        lastLoginPasswordRef.current = "";
      }
    }
  }, [session]);

  /** Re-login with a specific tenantId after an ambiguous tenant 409. */
  const loginWithTenant = useCallback(async (tenantId: string) => {
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
      // Security: clear the in-memory password after tenant-specific login succeeds.
      lastLoginPasswordRef.current = "";
    } catch (err) {
      if (err instanceof AmbiguousTenantError) {
        setAmbiguousTenantIds(err.tenantIds);
        setState("AMBIGUOUS_TENANT");
      } else {
        setError(toUserFacingError(err));
        setState("ERROR");
        // Security: clear the in-memory password on non-ambiguous failure.
        lastLoginPasswordRef.current = "";
      }
    }
  }, [lastLoginEmail, session]);

  /** Dismiss the tenant picker and go back to login form. */
  const dismissAmbiguousTenant = useCallback(() => {
    setAmbiguousTenantIds([]);
    setState("ANONYMOUS");
    // Security: clear the in-memory password when user dismisses tenant picker.
    lastLoginPasswordRef.current = "";
  }, []);

  const logout = useCallback(async () => {
    setState("LOGGING_OUT");
    try {
      await authApi.logout();
    } catch {
      // Ignore
    }
    session.clearSession();
    setUser(null);
    setMe(null);
    setError(null);
    setState("ANONYMOUS");
    // Security: clear the in-memory password on logout.
    lastLoginPasswordRef.current = "";
  }, [session]);

  const refresh = useCallback(async () => {
    if (refreshPromiseRef.current) return refreshPromiseRef.current;

    setState("REFRESHING");
    const promise = authApi
      .refresh()
      .then((res) => {
        session.setSession(res.accessToken, res.expiresAt);
        setUser(res.user);
        setState("AUTHENTICATED");
      })
      .catch((err) => {
        session.clearSession();
        setUser(null);
        setMe(null);
        setError(toUserFacingError(err));
        setState("EXPIRED");
        throw err;
      })
      .finally(() => {
        refreshPromiseRef.current = null;
      });

    refreshPromiseRef.current = promise;
    return promise;
  }, [session]);

  const loadMe = useCallback(async () => {
    try {
      const meData = await authApi.me();
      setMe(meData);
    } catch (err) {
      setError(toUserFacingError(err));
    }
  }, []);

  /** Change credential (self-service password rotation). Used for forced password change flow. */
  const changeCredential = useCallback(async (currentPassword: string, newPassword: string) => {
    setError(null);
    try {
      await authApi.changeCredential({
        currentCredential: currentPassword,
        newCredential: newPassword,
      });
      // After successful credential change, reload /me to get updated state
      const meData = await authApi.me();
      setMe(meData);
      setState("AUTHENTICATED");
    } catch (err) {
      setError(toUserFacingError(err));
      throw err;
    }
  }, []);

  /** Clear the current error state. */
  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const value = useMemo(
    () => ({ state, user, me, error, ambiguousTenantIds, lastLoginEmail, login, loginWithTenant, dismissAmbiguousTenant, logout, refresh, loadMe, changeCredential, clearError }),
    [state, user, me, error, ambiguousTenantIds, lastLoginEmail, login, loginWithTenant, dismissAmbiguousTenant, logout, refresh, loadMe, changeCredential, clearError]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
