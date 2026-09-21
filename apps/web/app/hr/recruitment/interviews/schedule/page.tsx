"use client";

/**
 * Interview Scheduling — G1-T11 Screen 8.
 * ============================================================================
 * Planner (participants, mode, planned_at), conflict-free verification server-side.
 * Timezone explicit (the API requires ISO plannedAt with timezone offset).
 *
 * Permission scoping:
 *   - HRM.RECRUITMENT.INTERVIEW.SCHEDULE required to schedule.
 *
 * Tenant scoping: scheduleInterview() is session-tenant-scoped.
 *
 * Form validation:
 *   - mode: required (one of IN_PERSON, VIDEO, PHONE)
 *   - plannedAt: required, ISO format with timezone
 *   - participants: at least one UUID
 *
 * Note: useSearchParams() requires a Suspense boundary in Next.js 16 App
 * Router during static prerendering. The page splits into an outer
 * <Suspense> wrapper and an inner component that calls useSearchParams.
 */

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { hrmRecruitmentApi } from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../../components/hr-workspace";
import { hrmErrorMessage } from "../../../components/hr-feedback";
import styles from "../../../hr.module.css";

function InterviewScheduleForm() {
  const searchParams = useSearchParams();
  const applicationId = searchParams.get("applicationId") ?? "";

  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canSchedule = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_INTERVIEW_SCHEDULE);

  const [mode, setMode] = useState<"IN_PERSON" | "VIDEO" | "PHONE">("VIDEO");
  const [plannedAt, setPlannedAt] = useState("");
  const [participantsText, setParticipantsText] = useState("");
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function submit() {
    setError(null);
    if (!mode) {
      setError(t("hrm.recruitment.interviewSchedule.error.modeRequired"));
      return;
    }
    if (!plannedAt) {
      setError(t("hrm.recruitment.interviewSchedule.error.plannedAtRequired"));
      return;
    }
    const participants = participantsText
      .split("\n")
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
    if (participants.length === 0) {
      setError(t("hrm.recruitment.interviewSchedule.error.participantsRequired"));
      return;
    }
    setBusy(true);
    try {
      const key = newIdempotencyKey();
      await hrmRecruitmentApi.scheduleInterview(applicationId, { mode, plannedAt: new Date(plannedAt).toISOString(), participantIds: participants, notes }, key);
      setNotice(t("hrm.recruitment.interviewSchedule.notice.scheduled"));
    } catch (err) {
      const { kind } = hrmErrorMessage(err);
      if (kind === "conflict") {
        setError(t("hrm.recruitment.interviewSchedule.notice.conflict"));
      } else {
        setError(hrmErrorMessage(err).message);
      }
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canSchedule) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
      <header>
        <h1>{t("hrm.recruitment.interviewSchedule.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.recruitment.interviewSchedule.subtitle")}</p>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {error ? <p role="alert" className={styles.kpiHint} data-kind="error">{error}</p> : null}

      <form onSubmit={(e) => { e.preventDefault(); void submit(); }} aria-label={t("hrm.recruitment.interviewSchedule.title")}>
        <p>
          <label htmlFor="applicationId" className={styles.kpiLabel}>{t("hrm.recruitment.interviewSchedule.application")}</label>
          <input
            id="applicationId"
            type="text"
            value={applicationId}
            readOnly
            className={styles.filterSelect}
            aria-readonly="true"
          />
        </p>
        <p>
          <label htmlFor="mode" className={styles.kpiLabel}>{t("hrm.recruitment.interviewSchedule.mode")}</label>
          <select
            id="mode"
            className={styles.filterSelect}
            value={mode}
            onChange={(e) => setMode(e.target.value as "IN_PERSON" | "VIDEO" | "PHONE")}
            required
            aria-required="true"
          >
            <option value="IN_PERSON">{t("hrm.recruitment.interviewSchedule.mode.IN_PERSON")}</option>
            <option value="VIDEO">{t("hrm.recruitment.interviewSchedule.mode.VIDEO")}</option>
            <option value="PHONE">{t("hrm.recruitment.interviewSchedule.mode.PHONE")}</option>
          </select>
        </p>
        <p>
          <label htmlFor="plannedAt" className={styles.kpiLabel}>{t("hrm.recruitment.interviewSchedule.plannedAt")}</label>
          <input
            id="plannedAt"
            type="datetime-local"
            value={plannedAt}
            onChange={(e) => setPlannedAt(e.target.value)}
            required
            aria-required="true"
            className={styles.filterSelect}
          />
        </p>
        <p>
          <label htmlFor="participants" className={styles.kpiLabel}>{t("hrm.recruitment.interviewSchedule.participants")}</label>
          <textarea
            id="participants"
            className={styles.reasonTextarea}
            value={participantsText}
            onChange={(e) => setParticipantsText(e.target.value)}
            placeholder={t("hrm.recruitment.interviewSchedule.participants.placeholder")}
            required
            aria-required="true"
          />
        </p>
        <p>
          <label htmlFor="notes" className={styles.kpiLabel}>{t("hrm.recruitment.interviewSchedule.notes")}</label>
          <textarea
            id="notes"
            className={styles.reasonTextarea}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder={t("hrm.recruitment.interviewSchedule.notes.placeholder")}
          />
        </p>
        <button type="submit" className={styles.linkButton} disabled={busy}>
          {busy ? t("hrm.recruitment.interviewSchedule.submitting") : t("hrm.recruitment.interviewSchedule.submit")}
        </button>
      </form>
    </HrWorkspace>
  );
}

export default function InterviewSchedulePage() {
  return (
    <Suspense fallback={<AuthLoadingState phase="session" />}>
      <InterviewScheduleForm />
    </Suspense>
  );
}
