"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  authApi,
  authResponseToMe,
  type AuthResponse,
  type AuthUser,
  type LoginRequest,
  type MeResponse,
  AmbiguousTenantError,
} from "@/lib/api/auth";
import { apiClient } from "@/lib/api/client";
import { ApiHttpError } from "@/lib/api/errors";
import { toUserFacingError, type UserFacingError } from "@/lib/api/user-facing-errors";
import { SingleFlight } from "@/lib/auth/single-flight";
import { hasSessionHint } from "@/lib/auth/session-hint";

export type AuthState =
  | "INITIALIZING"
  | "ANONYMOUS"
  | "CHECKING_SESSION"
  | "AUTHENTICATING"
  | "AUTHENTICATED"
  | "REFRESHING"
  | "REFRESHING_SESSION"
  | "LOADING_WORKSPACE"
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
  defaultDestination: string;
  availableDestinations: string[];
  canRetrySessionRestore: boolean;
  credentialProcessing: boolean;
  login: (req: LoginRequest) => Promise<void>;
  loginWithTenant: (tenantId: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  retrySessionRestore: () => Promise<void>;
  loadMe: () => Promise<void>;
  dismissAmbiguousTenant: () => void;
  changeCredential: (currentPassword: string, newPassword: string) => Promise<void>;
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

  const getAccessToken = useCallback(() => accessTokenRef.current, []);
  const getExpiresAt = useCallback(() => expiresAtRef.current, []);

  return useMemo(
    () => ({ setSession, clearSession, getAccessToken, getExpiresAt }),
    [setSession, clearSession, getAccessToken, getExpiresAt],
  );
}

function isTerminalSessionFailure(error: unknown): boolean {
  return error instanceof ApiHttpError && (error.status === 401 || error.status === 403);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>("INITIALIZING");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [me, setMe] = useState<MeResponse | null>(null);
  const [error, setError] = useState<UserFacingError | null>(null);
  const [ambiguousTenantIds, setAmbiguousTenantIds] = useState<string[]>([]);
  const [lastLoginEmail, setLastLoginEmail] = useState("");
  const [defaultDestination, setDefaultDestination] = useState("/workspace");
  const [availableDestinations, setAvailableDestinations] = useState<string[]>(["/workspace"]);
  const [canRetrySessionRestore, setCanRetrySessionRestore] = useState(false);
  const [credentialProcessing, setCredentialProcessing] = useState(false);
  const lastLoginPasswordRef = useRef<string>("");
  const refreshFlightRef = useRef<SingleFlight<AuthResponse> | null>(null);
  const refreshEnabledRef = useRef(true);
  const sessionGenerationRef = useRef(0);
  const bootstrapStartedRef = useRef(false);
  const session = useInMemorySession();

  if (refreshFlightRef.current === null) {
    refreshFlightRef.current = new SingleFlight<AuthResponse>();
  }

  const clearIdentity = useCallback(() => {
    session.clearSession();
    setUser(null);
    setMe(null);
    setDefaultDestination("/workspace");
    setAvailableDestinations(["/workspace"]);
  }, [session]);

  const applyAuthResponse = useCallback((response: AuthResponse) => {
    session.setSession(response.accessToken, response.expiresAt);
    setUser(response.user);
    setMe(authResponseToMe(response));
    setDefaultDestination(response.defaultDestination || "/workspace");
    setAvailableDestinations(
      response.availableDestinations?.length ? response.availableDestinations : ["/workspace"],
    );
    setCanRetrySessionRestore(false);
  }, [session]);

  const runRefresh = useCallback((): Promise<AuthResponse> => {
    if (!refreshEnabledRef.current) {
      return Promise.reject(new Error("Session refresh is disabled"));
    }

    const generation = sessionGenerationRef.current;
    const flight = refreshFlightRef.current;
    if (!flight) return Promise.reject(new Error("Session refresh is unavailable"));

    return flight.run(async () => {
      const response = await authApi.refresh();
      if (!refreshEnabledRef.current || generation !== sessionGenerationRef.current) {
        throw new Error("Stale session refresh result");
      }
      applyAuthResponse(response);
      return response;
    });
  }, [applyAuthResponse]);

  const restoreSession = useCallback(async () => {
    refreshEnabledRef.current = true;
    setState("CHECKING_SESSION");
    setError(null);
    setCanRetrySessionRestore(false);
    try {
      const response = await runRefresh();
      setState(response.credentialRotationRequired ? "CREDENTIAL_ROTATION_REQUIRED" : "AUTHENTICATED");
    } catch (err) {
      clearIdentity();
      if (isTerminalSessionFailure(err)) {
        setError(null);
        setCanRetrySessionRestore(false);
        setState("ANONYMOUS");
        return;
      }
      setError(toUserFacingError(err));
      setCanRetrySessionRestore(true);
      setState("ERROR");
    }
  }, [clearIdentity, runRefresh]);

  useEffect(() => {
    apiClient.setUnauthorizedHandler(async () => {
      try {
        const response = await runRefresh();
        setState(response.credentialRotationRequired ? "CREDENTIAL_ROTATION_REQUIRED" : "AUTHENTICATED");
        return true;
      } catch {
        clearIdentity();
        setCanRetrySessionRestore(false);
        setState("EXPIRED");
        return false;
      }
    });
    return () => { apiClient.setUnauthorizedHandler(null); };
  }, [clearIdentity, runRefresh]);

  // Resolve the non-sensitive hint before first paint. New visitors are never
  // blocked by a refresh request; returning users alone enter CHECKING_SESSION.
  useLayoutEffect(() => {
    if (bootstrapStartedRef.current) return;
    bootstrapStartedRef.current = true;
    if (!hasSessionHint()) {
      queueMicrotask(() => setState("ANONYMOUS"));
      return;
    }
    queueMicrotask(() => { void restoreSession(); });
  }, [restoreSession]);

  const login = useCallback(async (req: LoginRequest) => {
    refreshEnabledRef.current = true;
    sessionGenerationRef.current += 1;
    setState("AUTHENTICATING");
    setError(null);
    setCanRetrySessionRestore(false);
    setAmbiguousTenantIds([]);
    setLastLoginEmail(req.email);
    lastLoginPasswordRef.current = req.password;
    try {
      const response = await authApi.login(req);
      applyAuthResponse(response);
      setState(response.credentialRotationRequired ? "CREDENTIAL_ROTATION_REQUIRED" : "AUTHENTICATED");
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
  }, [applyAuthResponse]);

  const loginWithTenant = useCallback(async (tenantId: string) => {
    refreshEnabledRef.current = true;
    sessionGenerationRef.current += 1;
    setState("AUTHENTICATING");
    setError(null);
    try {
      const response = await authApi.login({
        email: lastLoginEmail,
        password: lastLoginPasswordRef.current,
        tenantId,
      });
      applyAuthResponse(response);
      setState(response.credentialRotationRequired ? "CREDENTIAL_ROTATION_REQUIRED" : "AUTHENTICATED");
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
  }, [applyAuthResponse, lastLoginEmail]);

  const dismissAmbiguousTenant = useCallback(() => {
    setAmbiguousTenantIds([]);
    setState("ANONYMOUS");
    lastLoginPasswordRef.current = "";
  }, []);

  const logout = useCallback(async () => {
    refreshEnabledRef.current = false;
    sessionGenerationRef.current += 1;
    setState("LOGGING_OUT");
    try {
      await authApi.logout();
    } catch {
      // Local logout is authoritative; the BFF clears first-party cookies.
    }
    clearIdentity();
    setError(null);
    setCanRetrySessionRestore(false);
    setState("ANONYMOUS");
    lastLoginPasswordRef.current = "";
  }, [clearIdentity]);

  const refresh = useCallback(async () => {
    refreshEnabledRef.current = true;
    setState("REFRESHING_SESSION");
    try {
      const response = await runRefresh();
      setState(response.credentialRotationRequired ? "CREDENTIAL_ROTATION_REQUIRED" : "AUTHENTICATED");
    } catch (err) {
      clearIdentity();
      setError(toUserFacingError(err));
      setState("EXPIRED");
      throw err;
    }
  }, [clearIdentity, runRefresh]);

  const retrySessionRestore = useCallback(async () => {
    if (!canRetrySessionRestore) return;
    await restoreSession();
  }, [canRetrySessionRestore, restoreSession]);

  const loadMe = useCallback(async () => {
    try {
      setMe(await authApi.me());
    } catch (err) {
      setError(toUserFacingError(err));
    }
  }, []);

  const changeCredential = useCallback(async (currentPassword: string, newPassword: string) => {
    const email = lastLoginEmail || user?.email || "";
    const tenantId = user?.tenantId;
    let credentialChanged = false;
    setCredentialProcessing(true);
    setError(null);
    try {
      await authApi.changeCredential({
        currentCredential: currentPassword,
        newCredential: newPassword,
      });
      credentialChanged = true;

      // Credential rotation revokes the old access/refresh session. Remove the
      // now-invalid in-memory token before establishing the replacement session.
      clearIdentity();
      sessionGenerationRef.current += 1;
      refreshEnabledRef.current = true;
      const response = await authApi.login({ email, password: newPassword, tenantId });
      applyAuthResponse(response);
      setState("AUTHENTICATED");
    } catch (err) {
      setError(toUserFacingError(err));
      // If the credential was already changed, the old authenticated session is
      // intentionally gone. Return to normal login rather than offering another
      // rotation attempt that can no longer be authorized.
      setState(credentialChanged ? "ERROR" : "CREDENTIAL_ROTATION_REQUIRED");
      throw err;
    } finally {
      setCredentialProcessing(false);
    }
  }, [applyAuthResponse, clearIdentity, lastLoginEmail, user]);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const value = useMemo(
    () => ({
      state,
      user,
      me,
      error,
      ambiguousTenantIds,
      lastLoginEmail,
      defaultDestination,
      availableDestinations,
      canRetrySessionRestore,
      credentialProcessing,
      login,
      loginWithTenant,
      dismissAmbiguousTenant,
      logout,
      refresh,
      retrySessionRestore,
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
      defaultDestination,
      availableDestinations,
      canRetrySessionRestore,
      credentialProcessing,
      login,
      loginWithTenant,
      dismissAmbiguousTenant,
      logout,
      refresh,
      retrySessionRestore,
      loadMe,
      changeCredential,
      clearError,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
