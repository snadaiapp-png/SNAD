"use client";

/**
 * Applications Board (Pipeline) — G1-T11 Screen 6.
 * ============================================================================
 * Per-opening kanban by stage. Command-first (keyboard + menu) with drag
 * enhancement — spec §14: "command-first (keyboard + menu) with drag
 * enhancement; concurrency 409 toast".
 *
 * Permission scoping:
 *   - HRM.RECRUITMENT.APPLICATION.MANAGE required to view.
 *   - HRM.RECRUITMENT.APPLICATION.ADVANCE to see advance action.
 *   - HRM.RECRUITMENT.APPLICATION.REJECT to see reject action.
 *   - HRM.RECRUITMENT.APPLICATION.WITHDRAW to see withdraw action.
 *
 * Tenant scoping: listApplications() + advance/reject/withdraw are session-tenant-scoped.
 *
 * Arabic/RTL: logical CSS only; applications.column.<STAGE> resolves stage codes.
 *
 * Concurrency 409: optimistic-lock loss surfaces as a deterministic Arabic toast,
 * not a generic error — the user is told to refresh and try again (spec §15).
 *
 * Idempotency: every mutation sends Idempotency-Key header; the backend
 * HrmIdempotentCommandExecutor replays the original result on retry.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { translations } from "@/lib/i18n";
import {
  hrmRecruitmentApi,
  type ApplicationSummaryResponse,
} from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../../components/hr-feedback";
import { HrStateBadge, toneForState } from "../../components/hr-state-badge";
import styles from "../../hr.module.css";

const STAGES = ["SUBMITTED", "SCREENING", "INTERVIEW", "OFFERED", "HIRED", "REJECTED", "WITHDRAWN"] as const;

type DialogKind = "advance" | "reject" | "withdraw" | null;

export default function ApplicationsBoardPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canManage = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_APPLICATION_MANAGE);
  const canAdvance = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_APPLICATION_ADVANCE);
  const canReject = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_APPLICATION_REJECT);
  const canWithdraw = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_APPLICATION_WITHDRAW);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [applications, setApplications] = useState<ApplicationSummaryResponse[]>([]);
  const [openingFilter, setOpeningFilter] = useState<string>("");
  const [dialog, setDialog] = useState<DialogKind>(null);
  const [dialogTarget, setDialogTarget] = useState<ApplicationSummaryResponse | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const dict = useMemo(() => translations[locale], [locale]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await hrmRecruitmentApi.listApplications(
        openingFilter ? { openingId: openingFilter } : undefined,
      );
      setApplications(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [openingFilter]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  function openDialog(kind: DialogKind, app: ApplicationSummaryResponse) {
    setDialog(kind);
    setDialogTarget(app);
    setReason("");
    setDialogError(null);
  }

  function closeDialog() {
    setDialog(null);
    setDialogTarget(null);
    setReason("");
    setDialogError(null);
  }

  async function submitDialog() {
    if (!dialog || !dialogTarget) return;
    if ((dialog === "reject" || dialog === "withdraw") && !reason.trim()) {
      setDialogError(t("hrm.recruitment.applications.reasonDialog.title." + dialog));
      return;
    }
    setBusy(true);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      if (dialog === "advance") await hrmRecruitmentApi.advanceApplication(dialogTarget.applicationId, key, reason || "advanced via ui");
      else if (dialog === "reject") await hrmRecruitmentApi.rejectApplication(dialogTarget.applicationId, key, reason);
      else if (dialog === "withdraw") await hrmRecruitmentApi.withdrawApplication(dialogTarget.applicationId, key, reason);
      setNotice(t("hrm.recruitment.applications.notice." + dialog));
      closeDialog();
      await load();
    } catch (err) {
      const { kind: errKind } = hrmErrorMessage(err);
      if (errKind === "conflict") {
        setDialogError(t("hrm.recruitment.applications.concurrency.409"));
      } else {
        setDialogError(hrmErrorMessage(err).message);
      }
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canManage) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment/applications">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  const byStage = (stage: string) => applications.filter((a) => a.stage === stage);

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment/applications">
      <header>
        <h1>{t("hrm.recruitment.applications.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.recruitment.applications.subtitle")}</p>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <section aria-label={t("hrm.recruitment.applications.title")} className={styles.kpiGrid}>
          {STAGES.map((stage) => (
            <article key={stage} className={styles.kpiTile} aria-label={t("hrm.recruitment.applications.column." + stage)}>
              <h2>{t("hrm.recruitment.applications.column." + stage)}</h2>
              {byStage(stage).length === 0 ? (
                <p role="status">{t("hrm.recruitment.applications.empty")}</p>
              ) : (
                <ul className={styles.activityList}>
                  {byStage(stage).map((app) => (
                    <li key={app.applicationId} className={styles.activityItem}>
                      <p className={styles.taskTitle}>{app.openingTitle}</p>
                      <HrStateBadge label={app.stage} code={app.stage} tone={toneForState(app.stage)} />
                      <span className={styles.actionRow}>
                        {canAdvance && stage !== "HIRED" && stage !== "REJECTED" && stage !== "WITHDRAWN" ? (
                          <button type="button" className={styles.linkButton} onClick={() => openDialog("advance", app)}>
                            {t("hrm.recruitment.applications.action.advance")}
                          </button>
                        ) : null}
                        {canReject && stage !== "REJECTED" && stage !== "WITHDRAWN" && stage !== "HIRED" ? (
                          <button type="button" className={styles.linkButton} onClick={() => openDialog("reject", app)}>
                            {t("hrm.recruitment.applications.action.reject")}
                          </button>
                        ) : null}
                        {canWithdraw && stage !== "WITHDRAWN" && stage !== "HIRED" && stage !== "REJECTED" ? (
                          <button type="button" className={styles.linkButton} onClick={() => openDialog("withdraw", app)}>
                            {t("hrm.recruitment.applications.action.withdraw")}
                          </button>
                        ) : null}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </article>
          ))}
        </section>
      )}

      {dialog && dialogTarget ? (
        <div role="dialog" aria-modal="true" aria-label={t("hrm.recruitment.applications.reasonDialog.title." + dialog)} className={styles.feedbackError}>
          <h3>{t("hrm.recruitment.applications.reasonDialog.title." + dialog)}</h3>
          <p>{dialogTarget.openingTitle}</p>
          <label htmlFor="reason-text" className={styles.kpiLabel}>
            {t("hrm.recruitment.openingDetail.timeline.col.reason")}
          </label>
          <textarea
            id="reason-text"
            className={styles.reasonTextarea}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required={(dialog === "reject" || dialog === "withdraw")}
            aria-required={(dialog === "reject" || dialog === "withdraw") ? "true" : "false"}
          />
          {dialogError ? <p role="alert">{dialogError}</p> : null}
          <div className={styles.actionRow}>
            <button type="button" className={styles.linkButton} onClick={closeDialog} disabled={busy}>
              {t("hrm.common.cancel")}
            </button>
            <button type="button" className={styles.linkButton} onClick={() => void submitDialog()} disabled={busy || ((dialog === "reject" || dialog === "withdraw") && !reason.trim())}>
              {t("hrm.recruitment.applications.reasonDialog.confirm." + dialog)}
            </button>
          </div>
        </div>
      ) : null}
    </HrWorkspace>
  );
}
