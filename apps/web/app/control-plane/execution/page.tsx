"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/auth-provider";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { ExecutiveShell } from "@/components/shell";
import {
  ExecutionDashboard,
  registerModuleProvider,
} from "../execution-dashboard";
import { CrmExecutionProvider } from "@/app/crm/crm-execution-provider";

/**
 * Register all module providers on import.
 * This ensures all modules appear in the dashboard.
 */
function registerAllProviders() {
  const crmProvider = new CrmExecutionProvider();
  registerModuleProvider({
    moduleId: crmProvider.moduleId,
    moduleName: crmProvider.moduleName,
    getPrograms: () => crmProvider.getPrograms(),
  });

  // Future modules will be registered here:
  // registerModuleProvider(new ErpExecutionProvider());
  // registerModuleProvider(new FinanceExecutionProvider());
  // etc.
}

// Register providers once
registerAllProviders();

/**
 * Execution Dashboard Page
 * ------------------------
 * Displays the unified execution dashboard for all modules.
 */
export default function ExecutionDashboardPage() {
  const { state } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (["ANONYMOUS", "ERROR", "EXPIRED", "CREDENTIAL_ROTATION_REQUIRED"].includes(state)) {
      router.replace("/");
    }
  }, [router, state]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING", "REFRESHING_SESSION", "LOGGING_OUT"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }
  if (state !== "AUTHENTICATED") return <AuthLoadingState phase="workspace" />;

  return (
    <ExecutiveShell>
      <ExecutionDashboard />
    </ExecutiveShell>
  );
}
