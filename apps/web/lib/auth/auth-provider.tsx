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
import {
  toUserFacingError,
  type UserFacingError,
} from "@/lib/api/user-facing-errors";
import {
  emitAuthMetric,
  nowMs,
} from "@/lib/observability/auth-performance";

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
  changeCredential: (
    currentPassword: string,
    newPassword: string,
  ) => Promise<void>;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

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

  return useMemo(
    () => ({
      setSession,
      clearSession,
      getAccessToken: () => accessTokenRef.current,
      getExpiresAt: () => expiresAtRef.current,
    }),
    [clearSession, setSession],
  );
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>("INITIALIZING");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [me, setMe] = useState<MeResponse | null>(null);
  const [error, setError] = useState<UserFacingError | null>(null);
  const [ambiguousTenantIds, setAmbiguousTenantIds] = useState<string[]>([]);
  const [lastLoginEmail, setLastLoginEmail] = useState("");
  const lastLoginPasswordRef = useRef("");
  const refreshPromiseRef = useRef<Promise<void> | null>(null);
  const session = useInMemorySession();

  const consumeAuthResponse = useCallback(
    async (response: AuthResponse): Promise<MeResponse> => {
      session.setSession(response.accessToken, response.expiresAt);
      setUser(response.user);

      const profile = response.profile ?? await authApi.me();
      setMe(profile);
      setState(
        profile.credentialRotationRequired
          ? "CREDENTIAL_ROTATION_REQUIRED"
          : "AUTHENTICATED",
      );
      return profile;
    },
    [session],
  );

  const performRefresh = useCallback(
    async (visibleState: boolean): Promise<void> => {
      if (refreshPromiseRef.current) return refreshPromiseRef.current;

      const startedAt = nowMs();
      if (visibleState) setState("REFRESHING");
      emitAuthMetric({ event: "session_restore_started" });

      const promise = authApi.refresh()
        .then(async (response) => {
          await consumeAuthResponse(response);
          emitAuthMetric({
            event: "session_restored",
            durationMs: Math.round(nowMs() - startedAt),
            outcome: "success",
          });
        })
        .catch((err) => {
          session.clearSession();
          setUser(null);
          setMe(null);
          if (visibleState) {
            setError(toUserFacingError(err));
            setState("EXPIRED");
          }
          emitAuthMetric({
            event: "session_restore_failed",
            durationMs: Math.round(nowMs() - startedAt),
            outcome: "failure",
          });
          throw err;
        })
        .finally(() => {
          refreshPromiseRef.current = null;
        });

      refreshPromiseRef.current = promise;
      return promise;
    },
    [consumeAuthResponse, session],
   );

  useEffect(() => {
    apiClient.setUrauthorizedHandler(async () => {
      try {
        await performRefresh(false);
        return true;
      } catch {
        session.clearSession();
        setUser(null);
        setMe(null);
        setState("EXPIRED");
        return false;
      }
    });
    return () => apiClient.setUnauthorizedHandler(null);
  }, [performRefresh, session]);

  useEffect(() => {
    if (state !== "INITIALIZING") return;
    let cancelled = false;

    performRefresh(false)
      .catch(() => {
        if (!cancelled) setState("ANONYMOUS");
      });

    return () => {
      cancelled = true;
    };
  }, [performRefresh, state]);

  const finishLogin = useCallback(
    async (request: LoginRequest) => {
      const startedAt = nowMs();
      setState("AUTHENTICATING");
      setError(null);
      setAmbiguousTenantIds([]);
      emitAuthMetric({ event: "login_submitted" });

      try {
        const response = await authApi.login(request);
        await consumeAuthResponse(response);
        lastLoginPasswordRef.current = "";
        emitAuthMetric({
          event: "authentication_succeeded",
          durationMs: Math.round(nowMs() - startedAt),
          outcome: "success",
        });
      } catch (err) {
        if (err instanceof AmbiguousTenantError) {
          setAmbiguousTenantIds(err.tenantIds);
          setState("AMBIGUOUS_TENANT");
        } else {
          setError(toUserFacingError(err));
          setState("ERROR");
          lastLoginPasswordRef.current = "";
        }
        emitAuthMetric({
          event: "authentication_failed",
          durationMs: Math.round(nowMs() - startedAt),
          outcome: "failure",
        });
      }
    },
    [consumeAuthResponse],
   );

  const login = useCallback(
    async (req: LoginRequest) => {
      setLastLoginEmail(req.email);
      lastLoginPasswordRef.current = req.password;
      await finishLogin(req);
    },
    [finishLogin],
  );

  const loginWithTenant = useCallback(
    async (tenantId: string) => {
      await finishLogin({
        email: lastLoginEmail,
        password: lastLoginPasswordRef.current,
        tenantId,
      });
    },
    [finishLogin, lastLoginEmail],
   );

  const dismissAmbiguousTenant = useCallback(() => {
    setAmbiguousTenantIds([]);
    setState("ANONYMOUS");
    lastLoginPasswordRef.current = "";
  }, []);

  const logout = useCallback(async () => {
    setState("LOGGING_OUT");
    try {
      await authApi.logout();
    } catch {
      // Local state is cleared even if the network request fails.
    }
    session.clearSession();
    setUser(null);
    setMe(null);
    setError(null);
    setState("ANONYMOUS");
    lastLoginPasswordRef.current = "";
  }, [session]);

  const refresh = useCallback(async () => {
    await performRefresh(true);
  }, [performRefresh]);

  const loadMe = useCallback(async () => {
    try {
      setMe(await authApi.me());
    } catch (err) {
      setError(toUserFacingError(err));
    }
  }, []);

  const changeCredential = useCallback(
    async (currentPassword: string, newPassword: string) => {
      setError(null);
      try {
        await authApi.changeCredential({
          currentCredential: currentPassword,
          newCredential: newPassword,
        });
        setMe(await authApi.me());
        setState("AUTHENTICATED");
      } catch (err) {
        setError(toUserFacingError(err));
        throw err;
      }
    },
    [],
  );

  const clearError = useCallback(() => setError(null), []);

  const value = useMemo(
    () => ({
      state,
      user,
      me,
      error,
      ambiguousTenantIds,
      lastLoginEmail,
      login,
      loginWithTenant,
      dismissAmbiguousTenant,
      logout,
      refresh,
      loadMe,
      changeCredential,
      clearError,
    }),
    [
      state,
      user,
      me,
      error,
      ambiguousTenantIds,
      lastLoginEmail,
      login,
      loginWithTenant,
      dismissAmbiguousTenant,
      logout,
      refresh,
      loadMe,
      changeCredential,
      clearError,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
