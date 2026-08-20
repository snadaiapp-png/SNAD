"use client";

import { CrmExecutionBoard } from "../../crm-execution-board";
import styles from "../../crm.module.css";
import { useAuth } from "@/lib/auth/auth-provider";
import { hasAnyCapability } from "@/lib/auth/capabilities";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useI18n } from "@/lib/i18n/I18nProvider";

/**
 * Capabilities that grant access to the Execution Board.
 *
 * ANY-OF: the user must hold at least one legitimate CRM operational READ
 * capability (the same set the CRM_SALES role template grants) OR be a CRM
 * administrator. This mirrors the EXECUTION_NAV sidebar predicate in
 * crm-shell.tsx so direct-URL access and sidebar discovery use the same rule.
 */
const EXECUTION_ACCESS_CAPABILITIES = [
  "CRM.ACCOUNT.READ",
  "CRM.CONTACT.READ",
  "CRM.LEAD.READ",
  "CRM.OPPORTUNITY.READ",
  "CRM.ACTIVITY.READ",
  "CRM.TASK.READ",
  "CRM.NOTE.READ",
  "CRM.TAG.READ",
  "CRM.ADMIN",
] as const;

/**
 * CRM Execution Board Page
 * ------------------------
 * Displays the full G0-G10 execution plan with parallel wave visualization,
 * group progress cards, and task-level details.
 *
 * This page was previously accessible via the CRM Command Center's
 * "Execution Board" tab. It was restored as a standalone route during
 * the G3 production release recovery phase.
 *
 * Authorization: the surrounding CrmShell already enforces authentication
 * (anonymous users are redirected to "/"). This page additionally enforces
 * the ANY-OF CRM operational READ capability policy on direct URL access,
 * so an authenticated user with no CRM operational capabilities is denied
 * rather than seeing an empty/broken board.
 */
export default function CrmExecutionPage() {
  const { state, me } = useAuth();
  const { t } = useI18n();

  if (
    state === "INITIALIZING" ||
    state === "REFRESHING" ||
    state === "LOGGING_OUT" ||
    state === "AUTHENTICATING"
  ) {
    return <AuthLoadingState subtitle={t("crm.shell.loading")} />;
  }

  if (state !== "AUTHENTICATED" || !hasAnyCapability(me, [...EXECUTION_ACCESS_CAPABILITIES])) {
    return (
      <div className={styles.contentInner} role="alert" aria-live="polite">
        <p>{t("error.forbidden")}</p>
      </div>
    );
  }

  return (
    <div className={styles.contentInner}>
      <CrmExecutionBoard />
    </div>
  );
}
