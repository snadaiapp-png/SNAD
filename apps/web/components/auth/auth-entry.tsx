"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import { AuthLoadingState } from "./auth-loading-state";
import { LoginScreen } from "./login-screen";
import { TenantPicker } from "./tenant-picker";
import { CredentialRotationForm } from "./credential-rotation-form";

export function AuthEntry() {
  const auth = useAuth();
  const router = useRouter();
  const tenantCache = useRef<string[]>([]);

  if (auth.ambiguousTenantIds.length > 0) {
    tenantCache.current = auth.ambiguousTenantIds;
  }
  const selectableTenants = auth.ambiguousTenantIds.length > 0
    ? auth.ambiguousTenantIds
    : tenantCache.current;

  useEffect(() => {
    if (auth.state === "AUTHENTICATED") router.replace("/workspace");
  }, [auth.state, router]);

  if (["INITIALIZING", "REFRESHING", "LOGGING_OUT"].includes(auth.state)) {
    return <AuthLoadingState />;
  }

  if (auth.state === "AUTHENTICATING" && selectableTenants.length > 0) {
    return (
      <TenantPicker
        tenantIds={selectableTenants}
        onSelect={auth.loginWithTenant}
        onDismiss={auth.dismissAmbiguousTenant}
        authenticating={true}
      />
    );
  }

  if (auth.state === "AUTHENTICATING") {
    return (
      <LoginScreen
        onLogin={(email, password) => auth.login({ email, password })}
        authenticating={true}
        error={null}
      />
    );
  }

  if (auth.state === "AMBIGUOUS_TENANT") {
    return (
      <TenantPicker
        tenantIds={selectableTenants}
        onSelect={auth.loginWithTenant}
        onDismiss={auth.dismissAmbiguousTenant}
        authenticating={false}
      />
    );
  }

  if (auth.state === "CREDENTIAL_ROTATION_REQUIRED") {
    return (
      <CredentialRotationForm
        onChangeCredential={auth.changeCredential}
        processing={false}
        error={auth.error}
        userEmail={auth.lastLoginEmail}
      />
    );
  }

  if (auth.state === "EXPIRED") {
    return (
      <LoginScreen
        onLogin={(email, password) => auth.login({ email, password })}
        authenticating={false}
        error={null}
        sessionExpired={true}
      />
    );
  }

  return (
    <LoginScreen
      onLogin={(email, password) => auth.login({ email, password })}
      authenticating={false}
      error={auth.error}
    />
  );
}
