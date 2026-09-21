"use client";

/**
 * Hire Conversion — G1-T11 Screen 12.
 * ============================================================================
 * Pre-flight checklist + explicit confirm command (spec §7).
 * Idempotent replay banner when already converted; failure-matrix errors per §7.3.
 *
 * Permission scoping:
 *   - HRM.RECRUITMENT.HIRE.CONVERT required to convert.
 *
 * Tenant scoping: convertOfferToHire() is session-tenant-scoped.
 *
 * Idempotency: the conversion endpoint is the single atomic/idempotent
 * boundary in G1 (§7). Same Idempotency-Key replays the original result;
 * different keys produce independent attempts.
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { hrmRecruitmentApi } from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../../../components/hr-workspace";
import { hrmErrorMessage } from "../../../../components/hr-feedback";
import styles from "../../../../hr.module.css";

export default function HireConversionPage() {
  const params = useParams<{ offerId: string }>();
  const offerId = params.offerId;

  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canConvert = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_HIRE_CONVERT);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [result, setResult] = useState<{ employmentId: string; planId: string } | null>(null);
  const [replayBanner, setReplayBanner] = useState(false);

  async function convert() {
    setBusy(true);
    setError(null);
    try {
      const key = newIdempotencyKey();
      const res = await hrmRecruitmentApi.convertOfferToHire(offerId, key);
      setResult(res);
      setNotice(t("hrm.recruitment.hireConversion.notice.converted"));
    } catch (err) {
      const { kind } = hrmErrorMessage(err);
      if (kind === "conflict") {
        // Conversion 409 = already converted (idempotent replay)
        setReplayBanner(true);
        setNotice(t("hrm.recruitment.hireConversion.replayBanner"));
      } else {
        setError(t("hrm.recruitment.hireConversion.notice.failure") + ": " + hrmErrorMessage(err).message);
      }
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canConvert) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.hireConversion.notFound")}</p>
      </HrWorkspace>
    );
  }

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
      <header>
        <h1>{t("hrm.recruitment.hireConversion.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.recruitment.hireConversion.subtitle")}</p>
      </header>

      {replayBanner ? (
        <p role="status" className={styles.feedbackForbidden}>
          {t("hrm.recruitment.hireConversion.replayBanner")}
        </p>
      ) : null}

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}
      {error ? <p role="alert" className={styles.kpiHint} data-kind="error">{error}</p> : null}

      <section aria-label={t("hrm.recruitment.hireConversion.preflight.title")}>
        <h2>{t("hrm.recruitment.hireConversion.preflight.title")}</h2>
        <ul className={styles.taskList}>
          <li className={styles.taskItem}>
            <input type="checkbox" id="preflight-identity" className={styles.taskCheckbox} defaultChecked />
            <label htmlFor="preflight-identity" className={styles.taskTitle}>
              {t("hrm.recruitment.hireConversion.preflight.identityResolved")}
            </label>
          </li>
          <li className={styles.taskItem}>
            <input type="checkbox" id="preflight-position" className={styles.taskCheckbox} defaultChecked />
            <label htmlFor="preflight-position" className={styles.taskTitle}>
              {t("hrm.recruitment.hireConversion.preflight.positionAvailable")}
            </label>
          </li>
          <li className={styles.taskItem}>
            <input type="checkbox" id="preflight-offer" className={styles.taskCheckbox} defaultChecked />
            <label htmlFor="preflight-offer" className={styles.taskTitle}>
              {t("hrm.recruitment.hireConversion.preflight.offerAccepted")}
            </label>
          </li>
          <li className={styles.taskItem}>
            <input type="checkbox" id="preflight-template" className={styles.taskCheckbox} defaultChecked />
            <label htmlFor="preflight-template" className={styles.taskTitle}>
              {t("hrm.recruitment.hireConversion.preflight.templateValid")}
            </label>
          </li>
        </ul>
      </section>

      <button type="button" className={styles.linkButton} onClick={() => void convert()} disabled={busy}>
        {busy ? t("hrm.recruitment.hireConversion.converting") : t("hrm.recruitment.hireConversion.convert")}
      </button>

      {result ? (
        <section aria-label={t("hrm.recruitment.hireConversion.notice.converted")}>
          <h2>{t("hrm.recruitment.hireConversion.notice.converted")}</h2>
          <dl>
            <dt>{t("hrm.recruitment.hireConversion.result.employmentId")}</dt>
            <dd>{result.employmentId}</dd>
            <dt>{t("hrm.recruitment.hireConversion.result.planId")}</dt>
            <dd>{result.planId}</dd>
          </dl>
        </section>
      ) : null}
    </HrWorkspace>
  );
}
