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

// ── Module Providers ───────────────────────────────────────────────────────

import { CrmExecutionProvider } from "@/app/crm/crm-execution-provider";
import { NotificationsExecutionProvider } from "@/app/notifications/notifications-execution-provider";
import { LicensingExecutionProvider } from "@/app/licensing/licensing-execution-provider";
import { WorkflowExecutionProvider } from "@/app/workflow/workflow-execution-provider";
import { HrExecutionProvider } from "@/app/hr/hr-execution-provider";
import { IdentityExecutionProvider } from "@/app/identity/identity-execution-provider";
import { ErpExecutionProvider } from "@/app/erp/erp-execution-provider";
import { FinanceExecutionProvider } from "@/app/finance/finance-execution-provider";
import { InventoryExecutionProvider } from "@/app/inventory/inventory-execution-provider";
import { PosExecutionProvider } from "@/app/pos/pos-execution-provider";
import { AnalyticsExecutionProvider } from "@/app/analytics/analytics-execution-provider";
import { SubscriptionsExecutionProvider } from "@/app/subscriptions/subscriptions-execution-provider";
import { AiPlatformExecutionProvider } from "@/app/ai-platform/ai-platform-execution-provider";

/**
 * Register all module providers on import.
 * This ensures all modules appear in the dashboard.
 */
function registerAllProviders() {
  const providers = [
    new CrmExecutionProvider(),
    new NotificationsExecutionProvider(),
    new LicensingExecutionProvider(),
    new WorkflowExecutionProvider(),
    new HrExecutionProvider(),
    new IdentityExecutionProvider(),
    new ErpExecutionProvider(),
    new FinanceExecutionProvider(),
    new InventoryExecutionProvider(),
    new PosExecutionProvider(),
    new AnalyticsExecutionProvider(),
    new SubscriptionsExecutionProvider(),
    new AiPlatformExecutionProvider(),
  ];

  for (const provider of providers) {
    registerModuleProvider({
      moduleId: provider.moduleId,
      moduleName: provider.moduleName,
      getPrograms: () => provider.getPrograms(),
    });
  }
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
  }, [state, router]);

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
