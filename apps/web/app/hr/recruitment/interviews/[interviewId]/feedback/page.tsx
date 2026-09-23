"use client";

/**
 * Interview Feedback — G1-T11 Screen 9.
 * ============================================================================
 * Per-participant scorecard form (schema-validated JSONB).
 * Autosave draft vs committed version; participant-only visibility.
 *
 * Permission scoping:
 *   - HRM.RECRUITMENT.INTERVIEW.MANAGE required to view + submit feedback.
 *
 * Tenant scoping: getInterviewFeedback() + putInterviewFeedback() are session-tenant-scoped.
 *
 * Form validation:
 *   - scorecard: required, must be valid JSON
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import {
  hrmRecruitmentApi,
  type InterviewFeedbackResponse,
} from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../../../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../../../../components/hr-feedback";
import styles from "../../../../hr.module.css";

const DEFAULT_SCORECARD = '{\n  "strengths": "",\n  "weaknesses": "",\n  "decision": "pass"\n}';

export default function InterviewFeedbackPage() {
  const params = useParams<{ interviewId: string }>();
  const interviewId = params.interviewId;

  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canManage = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_INTERVIEW_MANAGE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [previous, setPrevious] = useState<InterviewFeedbackResponse | null>(null);
  const [scorecard, setScorecard] = useState(DEFAULT_SCORECARD);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      try {
        const rows = await hrmRecruitmentApi.getInterviewFeedback(interviewId);
        const currentParticipant = rows.find((row) => row.participantId === me?.id) ?? null;
        setPrevious(currentParticipant);
        setScorecard(currentParticipant
          ? JSON.stringify(currentParticipant.scorecard, null, 2)
          : DEFAULT_SCORECARD);
      } catch (err: unknown) {
        // 404 means no previous scorecard — that's fine.
        const kind = hrmErrorMessage(err).kind;
        if (kind !== "notfound") throw err;
        setPrevious(null);
        setScorecard(DEFAULT_SCORECARD);
      }
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [interviewId, me?.id]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function save() {
    setFormError(null);
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(scorecard);
    } catch {
      setFormError(t("hrm.recruitment.interviewFeedback.error.invalidJson"));
      return;
    }
    setBusy(true);
    try {
      await hrmRecruitmentApi.putInterviewFeedback(
        interviewId,
        { scorecard: parsed },
        newIdempotencyKey(),
        previous?.version,
      );
      setNotice(t("hrm.recruitment.interviewFeedback.notice.saved"));
      await load();
    } catch (err) {
      setFormError(hrmErrorMessage(err).message);
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
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
      <header>
        <h1>{t("hrm.recruitment.interviewFeedback.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.recruitment.interviewFeedback.subtitle")}</p>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <>
          {previous ? (
            <section aria-label={t("hrm.recruitment.interviewFeedback.previousScorecard")}>
              <h2>{t("hrm.recruitment.interviewFeedback.previousScorecard")}</h2>
              <pre aria-live="polite">{JSON.stringify(previous.scorecard, null, 2)}</pre>
              <p className={styles.taskMeta}>
                {previous.submittedBy} — {previous.submittedAt}
              </p>
            </section>
          ) : (
            <p role="status" className={styles.kpiHint}>{t("hrm.recruitment.interviewFeedback.noPreviousScorecard")}</p>
          )}

          <form onSubmit={(e) => { e.preventDefault(); void save(); }} aria-label={t("hrm.recruitment.interviewFeedback.title")}>
            <p>
              <label htmlFor="scorecard" className={styles.kpiLabel}>{t("hrm.recruitment.interviewFeedback.scorecard")}</label>
              <textarea
                id="scorecard"
                className={styles.reasonTextarea}
                value={scorecard}
                onChange={(e) => setScorecard(e.target.value)}
                placeholder={t("hrm.recruitment.interviewFeedback.scorecard.placeholder")}
                required
                aria-required="true"
                rows={12}
              />
            </p>
            {formError ? <p role="alert" className={styles.kpiHint} data-kind="error">{formError}</p> : null}
            <button type="submit" className={styles.linkButton} disabled={busy}>
              {busy ? t("hrm.recruitment.interviewFeedback.saving") : t("hrm.recruitment.interviewFeedback.save")}
            </button>
          </form>
        </>
      )}
    </HrWorkspace>
  );
}
