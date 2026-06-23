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

const TOKEN_STORAGE_KEY = "sanad_access_token";
const TOKEN_EXPIRY_KEY = "sanad_access_token_expires_at";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => {
    // Check for stored token during initialization (not in effect)
    if (typeof window === "undefined") return "INITIALIZING";
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    const expiresAt = localStorage.getItem(TOKEN_EXPIRY_KEY);
    if (!token || !expiresAt || new Date(expiresAt) <= new Date()) {
      return "ANONYMOUS";
    }
    return "INITIALIZING"; // Will be resolved by the bootstrap effect
  });
  const [user, setUser] = useState<AuthUser | null>(null);
  const [me, setMe] = useState<MeResponse | null>(null);
  const [error, setError] = useState<UserFacingError | null>(null);
  const [ambiguousTenantIds, setAmbiguousTenantIds] = useState<string[]>([]);
  const [lastLoginEmail, setLastLoginEmail] = useState("");
  const lastLoginPasswordRef = useRef<string>("");
  const refreshPromiseRef = useRef<Promise<void> | null>(null);

  // Bootstrap: if we have a stored token, verify it by loading /me
  useEffect(() => {
    if (state !== "INITIALIZING") return;

    const token = typeof window !== "undefined" ? localStorage.getItem(TOKEN_STORAGE_KEY) : null;
    if (!token) return;

    apiClient.setDefaultHeader("Authorization", `Bearer ${token}`);

    let cancelled = false;
    authApi
      .me()
      .then((meData) => {
        if (cancelled) return;
        setMe(meData);
        setUser({
          id: meData.id,
          tenantId: meData.tenantId,
          email: meData.email,
          displayName: meData.displayName,
          status: meData.status,
        });
        // If credential rotation is required, go to rotation state instead of authenticated
        if (meData.credentialRotationRequired) {
          setState("CREDENTIAL_ROTATION_REQUIRED");
        } else {
          setState("AUTHENTICATED");
        }
      })
      .catch(() => {
        if (cancelled) return;
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        localStorage.removeItem(TOKEN_EXPIRY_KEY);
        apiClient.removeDefaultHeader("Authorization");
        setState("ANONYMOUS");
      });

    return () => { cancelled = true; };
  }, [state]);

  const login = useCallback(async (req: LoginRequest) => {
    setState("AUTHENTICATING");
    setError(null);
    setAmbiguousTenantIds([]);
    setLastLoginEmail(req.email);
    lastLoginPasswordRef.current = req.password;
    try {
      const res = await authApi.login(req);
      localStorage.setItem(TOKEN_STORAGE_KEY, res.accessToken);
      localStorage.setItem(TOKEN_EXPIRY_KEY, res.expiresAt);
      apiClient.setDefaultHeader("Authorization", `Bearer ${res.accessToken}`);
      setUser(res.user);

      const meData = await authApi.me();
      setMe(meData);
      // Check if credential rotation is required after login
      if (meData.credentialRotationRequired) {
        setState("CREDENTIAL_ROTATION_REQUIRED");
      } else {
        setState("AUTHENTICATED");
      }
    } catch (err) {
      if (err instanceof AmbiguousTenantError) {
        setAmbiguousTenantIds(err.tenantIds);
        setState("AMBIGUOUS_TENANT");
      } else {
        setError(toUserFacingError(err));
        setState("ERROR");
      }
    }
  }, []);

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
      localStorage.setItem(TOKEN_STORAGE_KEY, res.accessToken);
      localStorage.setItem(TOKEN_EXPIRY_KEY, res.expiresAt);
      apiClient.setDefaultHeader("Authorization", `Bearer ${res.accessToken}`);
      setUser(res.user);

      const meData = await authApi.me();
      setMe(meData);
      if (meData.credentialRotationRequired) {
        setState("CREDENTIAL_ROTATION_REQUIRED");
      } else {
        setState("AUTHENTICATED");
      }
    } catch (err) {
      if (err instanceof AmbiguousTenantError) {
        setAmbiguousTenantIds(err.tenantIds);
        setState("AMBIGUOUS_TENANT");
      } else {
        setError(toUserFacingError(err));
        setState("ERROR");
      }
    }
  }, [lastLoginEmail]);

  /** Dismiss the tenant picker and go back to login form. */
  const dismissAmbiguousTenant = useCallback(() => {
    setAmbiguousTenantIds([]);
    setState("ANONYMOUS");
  }, []);

  const logout = useCallback(async () => {
    setState("LOGGING_OUT");
    try {
      await authApi.logout();
    } catch {
      // Ignore
    }
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(TOKEN_EXPIRY_KEY);
    apiClient.removeDefaultHeader("Authorization");
    setUser(null);
    setMe(null);
    setError(null);
    setState("ANONYMOUS");
  }, []);

  const refresh = useCallback(async () => {
    if (refreshPromiseRef.current) return refreshPromiseRef.current;

    setState("REFRESHING");
    const promise = authApi
      .refresh()
      .then((res) => {
        localStorage.setItem(TOKEN_STORAGE_KEY, res.accessToken);
        localStorage.setItem(TOKEN_EXPIRY_KEY, res.expiresAt);
        apiClient.setDefaultHeader("Authorization", `Bearer ${res.accessToken}`);
        setUser(res.user);
        setState("AUTHENTICATED");
      })
      .catch((err) => {
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        localStorage.removeItem(TOKEN_EXPIRY_KEY);
        apiClient.removeDefaultHeader("Authorization");
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
  }, []);

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
