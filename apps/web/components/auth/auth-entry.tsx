"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import { AuthLoadingState } from "./auth-loading-state";
import { LoginScreen } from "./login-screen";
import { TenantPicker } from "./tenant-picker";
import { CredentialRotationForm } from "./credential-rotation-form";

export function AuthEntry() {
  const auth = useAuth();
  const router = useRouter();
  const [pendingTenantIds, setPendingTenantIds] = useState<string[]>([]);

  useEffect(() => {
    if (auth.state === "AUTHENTICATED") router.replace("/workspace");
  }, [auth.state, router]);

  const selectTenant = async (tenantId: string) => {
    setPendingTenantIds(auth.ambiguousTenantIds);
    await auth.loginWithTenant(tenantId);
  };

  const dismissTenantPicker = () => {
    setPendingTenantIds([]);
    auth.dismissAmbiguousTenant();
  };

  if (["INITIALIZING", "REFRESHING", "LOGGING_OUT"].includes(auth.state)) {
    return <AuthLoadingState />;
  }

  if (auth.state === "AUTHENTICATING" && pendingTenantIds.length > 0) {
    return (
      <TenantPicker
        tenantIds={pendingTenantIds}
        onSelect={selectTenant}
        onDismiss={dismissTenantPicker}
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
        tenantIds={auth.ambiguousTenantIds}
        onSelect={selectTenant}
        onDismiss={dismissTenantPicker}
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
