"use client";

/**
 * Application Detail — G1-T11 Screen 7.
 * ============================================================================
 * Stage history timeline, interviews, offers, actions per §6.2.
 * Reject/withdraw reason dialogs (mandatory reason).
 *
 * Permission scoping:
 *   - HRM.RECRUITMENT.APPLICATION.MANAGE required to view.
 *   - HRM.RECRUITMENT.APPLICATION.ADVANCE / .REJECT / .WITHDRAW for actions.
 *
 * Tenant scoping: getApplication() is session-tenant-scoped.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { translations } from "@/lib/i18n";
import {
  hrmRecruitmentApi,
  type ApplicationDetailResponse,
} from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../../components/hr-workspace";
import { HrErrorState, HrLoading, HrEmptyState, hrmErrorMessage } from "../../../components/hr-feedback";
import { HrStateBadge, toneForState } from "../../../components/hr-state-badge";
import { formatArabicDate } from "../../../hr-labels";
import styles from "../../../hr.module.css";

type DialogKind = "advance" | "reject" | "withdraw" | null;

export default function ApplicationDetailPage() {
  const params = useParams<{ applicationId: string }>();
  const applicationId = params.applicationId;

  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canManage = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_APPLICATION_MANAGE);
  const canAdvance = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_APPLICATION_ADVANCE);
  const canReject = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_APPLICATION_REJECT);
  const canWithdraw = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_APPLICATION_WITHDRAW);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [application, setApplication] = useState<ApplicationDetailResponse | null>(null);
  const [dialog, setDialog] = useState<DialogKind>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const dict = useMemo(() => translations[locale], [locale]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await hrmRecruitmentApi.getApplication(applicationId);
      setApplication(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [applicationId]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  function openDialog(kind: DialogKind) {
    setDialog(kind);
    setReason("");
    setDialogError(null);
  }

  function closeDialog() {
    setDialog(null);
    setReason("");
    setDialogError(null);
  }

  async function submitDialog() {
    if (!application || !dialog) return;
    if ((dialog === "reject" || dialog === "withdraw") && !reason.trim()) {
      setDialogError(t("hrm.recruitment.applications.reasonDialog.title." + dialog));
      return;
    }
    setBusy(true);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      if (dialog === "advance") await hrmRecruitmentApi.advanceApplication(application.applicationId, key, reason || "advanced");
      else if (dialog === "reject") await hrmRecruitmentApi.rejectApplication(application.applicationId, key, reason);
      else if (dialog === "withdraw") await hrmRecruitmentApi.withdrawApplication(application.applicationId, key, reason);
      setNotice(t("hrm.recruitment.applicationDetail.notice." + dialog));
      closeDialog();
      await load();
    } catch (err) {
      setDialogError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canManage) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment/applications">
      <header>
        <h1>{t("hrm.recruitment.applicationDetail.title")}</h1>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : application ? (
        <>
          <section aria-label={application.openingTitle}>
            <h2>{application.openingTitle}</h2>
            <p>
              <HrStateBadge label={application.stage} code={application.stage} tone={toneForState(application.stage)} />
              {" — "}
              <HrStateBadge label={application.state} code={application.state} tone={toneForState(application.state)} />
            </p>
            <p className={styles.kpiHint}>{application.candidateDisplayName}</p>
          </section>

          {(canAdvance || canReject || canWithdraw) && application.state === "ACTIVE" ? (
            <section aria-label={t("hrm.recruitment.applicationDetail.actions")}>
              <h2>{t("hrm.recruitment.applicationDetail.actions")}</h2>
              <span className={styles.actionRow}>
                {canAdvance ? (
                  <button type="button" className={styles.linkButton} onClick={() => openDialog("advance")} disabled={busy}>
                    {t("hrm.recruitment.applicationDetail.action.advance")}
                  </button>
                ) : null}
                {canReject ? (
                  <button type="button" className={styles.linkButton} onClick={() => openDialog("reject")} disabled={busy}>
                    {t("hrm.recruitment.applicationDetail.action.reject")}
                  </button>
                ) : null}
                {canWithdraw ? (
                  <button type="button" className={styles.linkButton} onClick={() => openDialog("withdraw")} disabled={busy}>
                    {t("hrm.recruitment.applicationDetail.action.withdraw")}
                  </button>
                ) : null}
                <Link href={`/hr/recruitment/interviews/schedule?applicationId=${application.applicationId}`} className={styles.linkButton}>
                  {t("hrm.recruitment.applicationDetail.action.scheduleInterview")}
                </Link>
                <Link href={`/hr/recruitment/offers/new/edit?applicationId=${application.applicationId}`} className={styles.linkButton}>
                  {t("hrm.recruitment.applicationDetail.action.createOffer")}
                </Link>
              </span>
            </section>
          ) : null}

          <section aria-label={t("hrm.recruitment.applicationDetail.stageHistory")}>
            <h2>{t("hrm.recruitment.applicationDetail.stageHistory")}</h2>
            {application.stageHistory.length === 0 ? (
              <HrEmptyState title={t("hrm.recruitment.applicationDetail.stageHistory.empty")} />
            ) : (
              <table className={styles.hrTable}>
                <caption>{t("hrm.recruitment.applicationDetail.stageHistory")}</caption>
                <thead>
                  <tr>
                    <th scope="col">{t("hrm.recruitment.applicationDetail.timeline.col.from")}</th>
                    <th scope="col">{t("hrm.recruitment.applicationDetail.timeline.col.to")}</th>
                    <th scope="col">{t("hrm.recruitment.applicationDetail.timeline.col.reason")}</th>
                    <th scope="col">{t("hrm.recruitment.applicationDetail.timeline.col.at")}</th>
                  </tr>
                </thead>
                <tbody>
                  {application.stageHistory.map((event, i) => (
                    <tr key={i}>
                      <td>{event.fromStage ?? "—"}</td>
                      <td><HrStateBadge label={event.toStage} code={event.toStage} tone={toneForState(event.toStage)} /></td>
                      <td>{event.reason ?? "—"}</td>
                      <td>{formatArabicDate(event.occurredAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <section aria-label={t("hrm.recruitment.applicationDetail.interviews")}>
            <h2>{t("hrm.recruitment.applicationDetail.interviews")}</h2>
            {application.interviews.length === 0 ? (
              <HrEmptyState title={t("hrm.recruitment.applicationDetail.interviews.empty")} />
            ) : (
              <ul className={styles.activityList}>
                {application.interviews.map((iv) => (
                  <li key={iv.interviewId} className={styles.activityItem}>
                    <p className={styles.taskTitle}>{iv.mode} — {formatArabicDate(iv.plannedAt)}</p>
                    <HrStateBadge label={iv.status} code={iv.status} tone={toneForState(iv.status)} />
                    {iv.outcome ? <p className={styles.taskMeta}>{iv.outcome}</p> : null}
                    <Link href={`/hr/recruitment/interviews/${iv.interviewId}/feedback`} className={styles.linkButton}>
                      {t("hrm.recruitment.interviewFeedback.title")}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section aria-label={t("hrm.recruitment.applicationDetail.offers")}>
            <h2>{t("hrm.recruitment.applicationDetail.offers")}</h2>
            {application.offers.length === 0 ? (
              <HrEmptyState title={t("hrm.recruitment.applicationDetail.offers.empty")} />
            ) : (
              <ul className={styles.activityList}>
                {application.offers.map((offer) => (
                  <li key={offer.offerId} className={styles.activityItem}>
                    <p className={styles.taskTitle}>v{offer.version} — {offer.status}</p>
                    <HrStateBadge label={offer.status} code={offer.status} tone={toneForState(offer.status)} />
                    <Link href={`/hr/recruitment/offers/${offer.offerId}/edit`} className={styles.linkButton}>
                      {t("hrm.recruitment.offerEditor.title")}
                    </Link>
                    <Link href={`/hr/recruitment/offers/${offer.offerId}/approve`} className={styles.linkButton}>
                      {t("hrm.recruitment.offerApproval.title")}
                    </Link>
                    <Link href={`/hr/recruitment/offers/${offer.offerId}/convert`} className={styles.linkButton}>
                      {t("hrm.recruitment.hireConversion.title")}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      ) : (
        <HrErrorState error={{ status: 404, message: t("hrm.recruitment.applicationDetail.notFound") }} />
      )}

      {dialog ? (
        <div role="dialog" aria-modal="true" aria-label={t("hrm.recruitment.applications.reasonDialog.title." + dialog)} className={styles.feedbackError}>
          <h3>{t("hrm.recruitment.applications.reasonDialog.title." + dialog)}</h3>
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
