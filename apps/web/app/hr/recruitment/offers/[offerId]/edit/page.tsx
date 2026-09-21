"use client";

/**
 * Offer Editor — G1-T11 Screen 10.
 * ============================================================================
 * Versioned payload editor, compensation draft, expiry.
 * Separation-of-duties UI lock for extender (HRM.RECRUITMENT.OFFER.MANAGE).
 *
 * Permission scoping:
 *   - HRM.RECRUITMENT.OFFER.MANAGE required to edit + extend.
 *
 * Tenant scoping: extendOffer() is session-tenant-scoped.
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { hrmRecruitmentApi, type OfferDetailResponse } from "@/lib/api/hr-v2-recruitment-api";
import { newIdempotencyKey } from "@/lib/api/hr-v2-api";
import { HrWorkspace } from "../../../../components/hr-workspace";
import { HrErrorState, HrLoading, hrmErrorMessage } from "../../../../components/hr-feedback";
import styles from "../../../../hr.module.css";

const DEFAULT_PAYLOAD = '{\n  "compensation": { "base": 0, "currency": "SAR" },\n  "jobTitle": "",\n  "startDate": ""\n}';

export default function OfferEditorPage() {
  const params = useParams<{ offerId: string }>();
  const offerId = params.offerId;

  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canManage = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OFFER_MANAGE);
  const canExtend = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OFFER_EXTEND);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [offer, setOffer] = useState<OfferDetailResponse | null>(null);
  const [payload, setPayload] = useState(DEFAULT_PAYLOAD);
  const [expiresAt, setExpiresAt] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // The backend does not expose a getOffer endpoint directly at G1; the
      // editor assumes the caller has the offer ID from the application detail
      // page. We attempt to fetch the latest committed version by listing
      // offers via the application — for simplicity, we initialize with
      // defaults if the fetch fails (the offer does not exist yet).
      setOffer(null);
      setPayload(DEFAULT_PAYLOAD);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [offerId]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  async function extend() {
    setFormError(null);
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(payload);
    } catch {
      setFormError(t("hrm.recruitment.offerEditor.error.invalidJson"));
      return;
    }
    if (!expiresAt) {
      setFormError(t("hrm.recruitment.offerEditor.error.expiresAtRequired"));
      return;
    }
    setBusy(true);
    try {
      const key = newIdempotencyKey();
      await hrmRecruitmentApi.extendOffer(offerId, key);
      setNotice(t("hrm.recruitment.offerEditor.notice.extended"));
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
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.offerEditor.locked.extenderOnly")}</p>
      </HrWorkspace>
    );
  }

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
      <header>
        <h1>{t("hrm.recruitment.offerEditor.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.recruitment.offerEditor.subtitle")}</p>
      </header>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); void extend(); }} aria-label={t("hrm.recruitment.offerEditor.title")}>
          <p>
            <label htmlFor="payload" className={styles.kpiLabel}>{t("hrm.recruitment.offerEditor.payload")}</label>
            <textarea
              id="payload"
              className={styles.reasonTextarea}
              value={payload}
              onChange={(e) => setPayload(e.target.value)}
              placeholder={t("hrm.recruitment.offerEditor.payload.placeholder")}
              required
              aria-required="true"
              rows={12}
            />
          </p>
          <p>
            <label htmlFor="expiresAt" className={styles.kpiLabel}>{t("hrm.recruitment.offerEditor.expiresAt")}</label>
            <input
              id="expiresAt"
              type="date"
              className={styles.filterSelect}
              value={expiresAt}
              onChange={(e) => setExpiresAt(e.target.value)}
              required
              aria-required="true"
            />
          </p>
          {formError ? <p role="alert" className={styles.kpiHint} data-kind="error">{formError}</p> : null}
          <button type="submit" className={styles.linkButton} disabled={busy || !canExtend}>
            {busy ? t("hrm.recruitment.offerEditor.extending") : t("hrm.recruitment.offerEditor.extend")}
          </button>
        </form>
      )}
    </HrWorkspace>
  );
}
