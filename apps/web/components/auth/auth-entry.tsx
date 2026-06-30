"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
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
    login,
    loginWithTenant,
    dismissAmbiguousTenant,
    changeCredential,
  } = useAuth();
  const router = useRouter();

  // Redirect to /workspace when authenticated
  useEffect(() => {
    if (state === "AUTHENTICATED") {
      router.replace("/workspace");
    }
  }, [state, router]);

  if (state === "INITIALIZING" || state === "REFRESHING" || state === "LOGGING_OUT") {
    return <AuthLoadingState />;
  }

  if (state === "AUTHENTICATING") {
    return (
      <LoginScreen
        onLogin={(email, password) => login({ email, password })}
        authenticating={true}
        error={null}
      />
    );
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
        processing={false}
        error={error}
        userEmail={lastLoginEmail}
      />
    );
  }

  if (state === "EXPIRED") {
    return (
      <LoginScreen
        onLogin={(email, password) => login({ email, password })}
        authenticating={false}
        error={null}
        sessionExpired={true}
      />
    );
  }

  // ANONYMOUS or ERROR
  return (
    <LoginScreen
      onLogin={(email, password) => login({ email, password })}
      authenticating={false}
      error={error}
    />
  );
}
