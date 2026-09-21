"use client";

/**
 * Job Opening Detail — G1-T11 Screen 3.
 * ============================================================================
 * State timeline, headcount, compliance decision badge, actions per §6.1.
 * Approval-in-progress banner; disabled-by-permission vs absent (capability-
 * driven: no capability → no control rendered).
 *
 * Permission scoping:
 *   - HRM.RECRUITMENT.OPENING.VIEW required to view.
 *   - HRM.RECRUITMENT.OPENING.MANAGE to see pause/resume/close.
 *   - HRM.RECRUITMENT.OPENING.PUBLISH to see submit/approve/reject.
 *
 * Tenant scoping: getOpening() is session-tenant-scoped.
 *
 * Arabic/RTL: logical CSS only; openingStatusLabel(dict, code) for backend
 * status codes; complianceDecisionLabel(dict, code) for compliance decisions.
 *
 * Idempotency: every mutation sends Idempotency-Key header.
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { translations } from "@/lib/i18n";
import {
  hrmRecruitmentApi,
  type OpeningDetailResponse,
} from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../../components/hr-workspace";
import { HrErrorState, HrLoading, HrEmptyState, hrmErrorMessage } from "../../../components/hr-feedback";
import { HrStateBadge, toneForState } from "../../../components/hr-state-badge";
import { formatArabicDate, openingStatusLabel, complianceDecisionLabel } from "../../../hr-labels";
import styles from "../../../hr.module.css";

export default function JobOpeningDetailPage() {
  const params = useParams<{ openingId: string }>();
  const openingId = params.openingId;

  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canView = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OPENING_VIEW);
  const canManage = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OPENING_MANAGE);
  const canPublish = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OPENING_PUBLISH);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [opening, setOpening] = useState<OpeningDetailResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const dict = useMemo(() => translations[locale], [locale]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await hrmRecruitmentApi.getOpening(openingId);
      setOpening(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [openingId]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function action(kind: "submit" | "approve" | "reject" | "pause" | "close") {
    if (!opening) return;
    setBusy(true);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      if (kind === "submit") await hrmRecruitmentApi.submitOpening(opening.openingId, key);
      else if (kind === "approve") await hrmRecruitmentApi.approveOpening(opening.openingId, key);
      else if (kind === "reject") await hrmRecruitmentApi.rejectOpening(opening.openingId, key, "rejected via ui");
      else if (kind === "pause") await hrmRecruitmentApi.pauseOpening(opening.openingId, key);
      else if (kind === "close") await hrmRecruitmentApi.closeOpening(opening.openingId, key);
      setNotice(t("hrm.recruitment.openingDetail.notice." + kind));
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

  if (!canView) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
      <header>
        <h1>{t("hrm.recruitment.openingDetail.title")}</h1>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {dialogError ? (
        <p role="alert" className={styles.kpiHint} data-kind="error">{dialogError}</p>
      ) : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : opening ? (
        <>
          <section aria-label={opening.title}>
            <h2>{opening.title}</h2>
            <p>
              <HrStateBadge
                label={openingStatusLabel(dict, opening.status)}
                code={opening.status}
                tone={toneForState(opening.status)}
              />
            </p>
            {opening.status === "IN_APPROVAL" ? (
              <p role="status" className={styles.feedbackForbidden}>
                {t("hrm.recruitment.openingDetail.approvalBanner")}
              </p>
            ) : null}
            <dl>
              <dt>{t("hrm.recruitment.openingDetail.headcount")}</dt>
              <dd>{opening.headcount}</dd>
              <dt>{t("hrm.recruitment.openingDetail.filled")}</dt>
              <dd>{opening.filledCount}</dd>
              <dt>{t("hrm.recruitment.openingDetail.compliance")}</dt>
              <dd>{complianceDecisionLabel(dict, opening.complianceDecision)}</dd>
            </dl>
          </section>

          {(canManage || canPublish) && opening.status !== "CLOSED" && opening.status !== "CANCELLED" ? (
            <section aria-label={t("hrm.recruitment.openingDetail.actions.title")}>
              <h2>{t("hrm.recruitment.openingDetail.actions.title")}</h2>
              <span className={styles.actionRow}>
                {canPublish && opening.status === "DRAFT" ? (
                  <button type="button" className={styles.linkButton} onClick={() => void action("submit")} disabled={busy}>
                    {t("hrm.recruitment.openings.action.submit")}
                  </button>
                ) : null}
                {canPublish && opening.status === "IN_APPROVAL" ? (
                  <>
                    <button type="button" className={styles.linkButton} onClick={() => void action("approve")} disabled={busy}>
                      {t("hrm.recruitment.openings.action.approve")}
                    </button>
                    <button type="button" className={styles.linkButton} onClick={() => void action("reject")} disabled={busy}>
                      {t("hrm.recruitment.openings.action.reject")}
                    </button>
                  </>
                ) : null}
                {canManage && opening.status === "OPEN" ? (
                  <button type="button" className={styles.linkButton} onClick={() => void action("pause")} disabled={busy}>
                    {t("hrm.recruitment.openings.action.pause")}
                  </button>
                ) : null}
                {canManage && (opening.status === "OPEN" || opening.status === "PAUSED") ? (
                  <button type="button" className={styles.linkButton} onClick={() => void action("close")} disabled={busy}>
                    {t("hrm.recruitment.openings.action.close")}
                  </button>
                ) : null}
              </span>
            </section>
          ) : null}

          <section aria-label={t("hrm.recruitment.openingDetail.timeline.title")}>
            <h2>{t("hrm.recruitment.openingDetail.timeline.title")}</h2>
            {opening.stateHistory.length === 0 ? (
              <HrEmptyState title={t("hrm.recruitment.openingDetail.timeline.empty")} />
            ) : (
              <table className={styles.hrTable}>
                <caption>{t("hrm.recruitment.openingDetail.timeline.title")}</caption>
                <thead>
                  <tr>
                    <th scope="col">{t("hrm.recruitment.openingDetail.timeline.col.from")}</th>
                    <th scope="col">{t("hrm.recruitment.openingDetail.timeline.col.to")}</th>
                    <th scope="col">{t("hrm.recruitment.openingDetail.timeline.col.actor")}</th>
                    <th scope="col">{t("hrm.recruitment.openingDetail.timeline.col.reason")}</th>
                    <th scope="col">{t("hrm.recruitment.openingDetail.timeline.col.at")}</th>
                  </tr>
                </thead>
                <tbody>
                  {opening.stateHistory.map((event, i) => (
                    <tr key={i}>
                      <td>{event.fromStatus ? openingStatusLabel(dict, event.fromStatus) : "—"}</td>
                      <td><HrStateBadge label={openingStatusLabel(dict, event.toStatus)} code={event.toStatus} tone={toneForState(event.toStatus)} /></td>
                      <td>{event.actor ?? "—"}</td>
                      <td>{event.reason ?? "—"}</td>
                      <td>{formatArabicDate(event.occurredAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      ) : (
        <HrErrorState error={{ status: 404, message: t("hrm.recruitment.openingDetail.notFound") }} />
      )}
    </HrWorkspace>
  );
}
