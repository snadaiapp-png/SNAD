"use client";

/**
 * Offer Approval — G1-T11 Screen 11.
 * ============================================================================
 * Y2 work-item view, masked compensation summary, approve/reject.
 * Reason mandatory on reject; sensitive-read noted.
 *
 * Permission scoping:
 *   - HRM.RECRUITMENT.OFFER.ACCEPT required to accept.
 *   - HRM.RECRUITMENT.OFFER.DECLINE required to decline.
 *   - HRM.RECRUITMENT.OFFER.MANAGE required to withdraw.
 *
 * Tenant scoping: accept/decline/withdraw are session-tenant-scoped.
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
import { HrStateBadge, toneForState } from "../../../../components/hr-state-badge";
import { formatArabicDate } from "../../../../hr-labels";
import styles from "../../../../hr.module.css";

export default function OfferApprovalPage() {
  const params = useParams<{ offerId: string }>();
  const offerId = params.offerId;

  const { state, me } = useAuth();
  const { t } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canAccept = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OFFER_ACCEPT);
  const canDecline = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OFFER_DECLINE);
  const canManage = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OFFER_MANAGE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [offer, setOffer] = useState<OfferDetailResponse | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // For G1, the offer detail fetch is not directly exposed; the editor
      // surface assumes the offer data is referenced from the application
      // detail page. We leave offer = null and rely on the action buttons.
      setOffer(null);
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

  async function decide(kind: "accept" | "decline" | "withdraw") {
    if (kind === "decline" && !reason.trim()) {
      setFormError(t("hrm.recruitment.offerApproval.reasonRequired"));
      return;
    }
    setBusy(true);
    setFormError(null);
    try {
      const key = newIdempotencyKey();
      if (kind === "accept") await hrmRecruitmentApi.acceptOffer(offerId, key);
      else if (kind === "decline") await hrmRecruitmentApi.declineOffer(offerId, key, reason);
      else if (kind === "withdraw") await hrmRecruitmentApi.withdrawOffer(offerId, key, reason || "withdrawn via ui");
      setNotice(t("hrm.recruitment.offerApproval.notice." + (kind === "accept" ? "accepted" : kind === "decline" ? "declined" : "withdrawn")));
      setReason("");
    } catch (err) {
      setFormError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canAccept && !canDecline && !canManage) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
        <p role="alert" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
      </HrWorkspace>
    );
  }

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
      <header>
        <h1>{t("hrm.recruitment.offerApproval.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.recruitment.offerApproval.subtitle")}</p>
      </header>

      <p role="status" className={styles.kpiHint}>
        {t("hrm.recruitment.offerApproval.sensitiveRead")}
      </p>

      {notice ? <p role="status" className={styles.kpiHint}>{notice}</p> : null}

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <>
          <dl>
            <dt>{t("hrm.recruitment.offerApproval.compensationSummary")}</dt>
            <dd>••••••</dd>
            <dt>{t("hrm.recruitment.offerApproval.expiresAt")}</dt>
            <dd>{offer?.expiresAt ? formatArabicDate(offer.expiresAt) : "—"}</dd>
          </dl>

          <div className={styles.actionRow}>
            {canAccept ? (
              <button type="button" className={styles.linkButton} onClick={() => void decide("accept")} disabled={busy}>
                {t("hrm.recruitment.offerApproval.approve")}
              </button>
            ) : null}
            {canDecline ? (
              <button type="button" className={styles.linkButton} onClick={() => void decide("decline")} disabled={busy}>
                {t("hrm.recruitment.offerApproval.reject")}
              </button>
            ) : null}
            {canManage ? (
              <button type="button" className={styles.linkButton} onClick={() => void decide("withdraw")} disabled={busy}>
                {t("hrm.recruitment.offerApproval.withdraw")}
              </button>
            ) : null}
          </div>

          <p>
            <label htmlFor="reason" className={styles.kpiLabel}>{t("hrm.recruitment.offerApproval.reasonRequired")}</label>
            <textarea
              id="reason"
              className={styles.reasonTextarea}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={t("hrm.recruitment.offerApproval.reasonPlaceholder")}
              aria-required="true"
            />
          </p>
          {formError ? <p role="alert" className={styles.kpiHint} data-kind="error">{formError}</p> : null}
        </>
      )}
    </HrWorkspace>
  );
}
