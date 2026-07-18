"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  AUTH_PREFETCH_DESTINATIONS,
  readReturnUrl,
  resolvePostLoginDestination,
  safeReturnUrl,
} from "@/lib/auth/destination";
import { AuthLoadingState } from "./auth-loading-state";
import { LoginScreen } from "./login-screen";
import { TenantPicker } from "./tenant-picker";
import { CredentialRotationForm } from "./credential-rotation-form";

export function AuthEntry() {
  const {
    state,
    error,
    ambiguousTenantIds,
    lastLoginEmail,
    defaultDestination,
    availableDestinations,
    canRetrySessionRestore,
    credentialProcessing,
    login,
    loginWithTenant,
    retrySessionRestore,
    dismissAmbiguousTenant,
    changeCredential,
  } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const destination = resolvePostLoginDestination({
      returnUrl: readReturnUrl(),
      defaultDestination,
      availableDestinations,
    });
    router.prefetch(destination);
    router.replace(destination);
  }, [availableDestinations, defaultDestination, router, state]);

  const handleLogin = async (email: string, password: string) => {
    const requested = safeReturnUrl(readReturnUrl(), AUTH_PREFETCH_DESTINATIONS);
    router.prefetch(requested ?? "/crm");
    await login({ email, password });
  };

  if (state === "CHECKING_SESSION") return <AuthLoadingState phase="session" />;
  if (state === "REFRESHING_SESSION" || state === "REFRESHING") return <AuthLoadingState phase="refresh" />;
  if (state === "LOGGING_OUT") return <AuthLoadingState phase="logout" />;
  if (state === "AUTHENTICATED" || state === "LOADING_WORKSPACE") return <AuthLoadingState phase="workspace" />;

  if (state === "AUTHENTICATING" && ambiguousTenantIds.length > 0) {
    return (
      <TenantPicker
        tenantIds={ambiguousTenantIds}
        onSelect={loginWithTenant}
        onDismiss={dismissAmbiguousTenant}
        authenticating
      />
    );
  }

  if (state === "AUTHENTICATING") {
    return <LoginScreen onLogin={handleLogin} authenticating error={null} />;
  }

  if (state === "AMBIGUOUS_TENANT") {
    return (
      <TenantPicker
        tenantIds={ambiguousTenantIds}
        onSelect={loginWithTenant}
        onDismiss={dismissAmbiguousTenant}
        authenticating={false}
      />
    );
  }

  if (state === "CREDENTIAL_ROTATION_REQUIRED") {
    return (
      <CredentialRotationForm
        onChangeCredential={changeCredential}
        processing={credentialProcessing}
        error={error}
        userEmail={lastLoginEmail}
      />
    );
  }

  if (state === "EXPIRED") {
    return <LoginScreen onLogin={handleLogin} authenticating={false} error={null} sessionExpired />;
  }

  return (
    <LoginScreen
      onLogin={handleLogin}
      authenticating={false}
      error={error}
      onRetrySession={canRetrySessionRestore ? retrySessionRestore : undefined}
    />
  );
}
