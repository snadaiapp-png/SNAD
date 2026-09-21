"use client";

/**
 * Candidate Profile — G1-T11 Screen 5.
 * ============================================================================
 * Permissioned contact (masked by default), applications history, archive action.
 * Duplicate-warning panel. No PII in page title/meta (spec §10).
 *
 * Permission scoping:
 *   - HRM.RECRUITMENT.CANDIDATE.VIEW required to view.
 *   - HRM.RECRUITMENT.CANDIDATE.MANAGE to see archive action.
 *   - HRM.PII.VIEW required for unmasked reads (audit-logged server-side;
 *     this UI never displays unmasked PII — the masked DTO is the only data
 *     the client receives).
 *
 * Tenant scoping: getCandidate() is session-tenant-scoped.
 *
 * Idempotency: archiveCandidate() sends Idempotency-Key header.
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
  type CandidateDetailResponse,
} from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../../components/hr-workspace";
import { HrErrorState, HrLoading, HrEmptyState, hrmErrorMessage } from "../../../components/hr-feedback";
import { HrStateBadge, toneForState } from "../../../components/hr-state-badge";
import { formatArabicDate, candidatePoolStateLabel } from "../../../hr-labels";
import styles from "../../../hr.module.css";

export default function CandidateProfilePage() {
  const params = useParams<{ candidateId: string }>();
  const candidateId = params.candidateId;

  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canView = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_CANDIDATE_VIEW);
  const canManage = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_CANDIDATE_MANAGE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [candidate, setCandidate] = useState<CandidateDetailResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const dict = useMemo(() => translations[locale], [locale]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await hrmRecruitmentApi.getCandidate(candidateId);
      setCandidate(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [candidateId]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function archive() {
    if (!candidate) return;
    setBusy(true);
    setDialogError(null);
    try {
      const key = newIdempotencyKey();
      await hrmRecruitmentApi.archiveCandidate(candidate.candidateId, key);
      setNotice(t("hrm.recruitment.candidateProfile.archive.notice"));
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
      <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment/candidates">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment/candidates">
      <header>
        {/* No PII in page title — displayName is already masked server-side */}
        <h1>{t("hrm.recruitment.candidateProfile.title")}</h1>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {dialogError ? (
        <p role="alert" className={styles.kpiHint} data-kind="error">{dialogError}</p>
      ) : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : candidate ? (
        <>
          <section aria-label={candidate.displayName}>
            <h2>{candidate.displayName}</h2>
            <p>
              <HrStateBadge
                label={candidatePoolStateLabel(dict, candidate.poolState)}
                code={candidate.poolState}
                tone={toneForState(candidate.poolState)}
              />
            </p>
            {candidate.duplicateWarning ? (
              <p role="alert" className={styles.feedbackForbidden}>
                {t("hrm.recruitment.candidateProfile.duplicateWarning")}
              </p>
            ) : null}
            <dl>
              <dt>{t("hrm.recruitment.candidateProfile.contact.email")}</dt>
              <dd>{candidate.emailMasked}</dd>
              <dt>{t("hrm.recruitment.candidateProfile.contact.phone")}</dt>
              <dd>{candidate.phoneMasked ?? t("hrm.recruitment.candidateProfile.contact.phoneUnavailable")}</dd>
            </dl>
            {canManage && candidate.poolState !== "ARCHIVED" ? (
              <button type="button" className={styles.linkButton} onClick={() => void archive()} disabled={busy}>
                {t("hrm.recruitment.candidateProfile.archive.button")}
              </button>
            ) : null}
          </section>

          <section aria-label={t("hrm.recruitment.candidateProfile.applications.title")}>
            <h2>{t("hrm.recruitment.candidateProfile.applications.title")}</h2>
            {candidate.applications.length === 0 ? (
              <HrEmptyState title={t("hrm.recruitment.candidateProfile.applications.empty")} />
            ) : (
              <table className={styles.hrTable}>
                <caption>{t("hrm.recruitment.candidateProfile.applications.title")}</caption>
                <thead>
                  <tr>
                    <th scope="col">{t("hrm.recruitment.candidateProfile.col.opening")}</th>
                    <th scope="col">{t("hrm.recruitment.candidateProfile.col.stage")}</th>
                    <th scope="col">{t("hrm.recruitment.candidateProfile.col.state")}</th>
                    <th scope="col">{t("hrm.recruitment.candidateProfile.col.updated")}</th>
                  </tr>
                </thead>
                <tbody>
                  {candidate.applications.map((app) => (
                    <tr key={app.applicationId}>
                      <td>{app.openingTitle}</td>
                      <td><HrStateBadge label={app.stage} code={app.stage} tone={toneForState(app.stage)} /></td>
                      <td><HrStateBadge label={app.state} code={app.state} tone={toneForState(app.state)} /></td>
                      <td>{formatArabicDate(app.updatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      ) : (
        <HrErrorState error={{ status: 404, message: t("hrm.recruitment.candidateProfile.notFound") }} />
      )}
    </HrWorkspace>
  );
}
