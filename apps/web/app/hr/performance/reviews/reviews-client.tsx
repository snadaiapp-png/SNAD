"use client";

/**
 * HRM G3 Task 6 — Performance review product surface.
 *
 * Built entirely on the SHARED SNAD module visual contract (components/sds/
 * module primitives through the HR wrappers): shared page header, KPI
 * anatomy, operational panels, status badges, states, and mobile records.
 *
 * Capabilities (UX gating only — backend @RequireCapability remains
 * authoritative, no legacy manager fallback):
 *   REVIEW.SELF_VIEW     — see own reviews
 *   REVIEW.SELF_SUBMIT   — create/submit/acknowledge own reviews
 *   REVIEW.TEAM_MANAGE   — manage direct-report reviews (create/cancel)
 *
 * The SELF and TEAM panels are strictly capability-separated. SELF identity
 * is derived by the backend from the session; the page never sends
 * tenant/user/employment authority ids (TEAM create carries employmentId in
 * the route path only, as a manager-scope route parameter).
 */

import { useCallback, useEffect, useState, type ReactNode } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { SnadStatusBadge, SnadSuccessNotice } from "@/components/sds/module";
import { hrG3Api, type G3PerformanceReview } from "@/lib/api/hr-g3-api";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { HrWorkspace } from "../../components/hr-workspace";
import { HrErrorState, HrLoading, HrEmptyState, hrmErrorMessage } from "../../components/hr-feedback";
import { HrDataTable, type HrColumn } from "../../components/hr-data-table";
import { HrStateBadge, toneForState } from "../../components/hr-state-badge";
import {
  HrActionBar,
  HrKpiCard,
  HrKpiGrid,
  HrMobileRecordList,
  HrOperationalPanel,
  HrProductHeader,
} from "../../components/hr-product-surface";
import { formatLocalizedDate } from "../../hr-labels";
import sdsStyles from "@/components/sds/module/snad-module.module.css";
import styles from "../../hr.module.css";

const AUTH_LOADING_STATES = new Set(["INITIALIZING", "CHECKING_SESSION", "REFRESHING"]);

type ReviewFormState = {
  cycle: string;
  periodStart: string;
  periodEnd: string;
  rating: string;
  comments: string;
};

const EMPTY_FORM: ReviewFormState = { cycle: "", periodStart: "", periodEnd: "", rating: "", comments: "" };

export default function ReviewsPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canViewSelf = capabilities.includes(HRM_CAPABILITIES.REVIEW_SELF_VIEW);
  const canSubmit = capabilities.includes(HRM_CAPABILITIES.REVIEW_SELF_SUBMIT);
  const canManageTeam = capabilities.includes(HRM_CAPABILITIES.REVIEW_TEAM_MANAGE);
  const canView = canViewSelf || canManageTeam;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [reviews, setReviews] = useState<G3PerformanceReview[]>([]);
  const [teamReviews, setTeamReviews] = useState<G3PerformanceReview[] | null>(null);
  const [teamError, setTeamError] = useState<unknown>(null);

  const [selfForm, setSelfForm] = useState<ReviewFormState>(EMPTY_FORM);
  const [selfFormError, setSelfFormError] = useState<string | null>(null);
  const [teamForm, setTeamForm] = useState<ReviewFormState>(EMPTY_FORM);
  const [teamEmploymentId, setTeamEmploymentId] = useState("");
  const [teamFormError, setTeamFormError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setTeamError(null);

    if (canViewSelf) {
      try {
        setReviews(await hrG3Api.listReviews());
      } catch (err) {
        setReviews([]);
        setError(err);
      }
    } else {
      setReviews([]);
    }

    if (canManageTeam) {
      try {
        setTeamReviews(await hrG3Api.listTeamReviews());
      } catch (err) {
        setTeamReviews([]);
        setTeamError(err);
      }
    } else {
      setTeamReviews(null);
    }

    setLoading(false);
  }, [canViewSelf, canManageTeam]);

  useEffect(() => {
    if (state !== "AUTHENTICATED" || !canView) return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, canView, load]);

  function validateReviewForm(form: ReviewFormState): string | null {
    if (form.cycle.trim() === "" || form.cycle.length > 80) {
      return t("hrm.g3.reviews.validation.cycle");
    }
    if (form.periodStart !== "" && form.periodEnd !== "" && form.periodEnd < form.periodStart) {
      return t("hrm.g3.reviews.validation.period");
    }
    const rating = Number(form.rating);
    if (form.rating === "" || !Number.isInteger(rating) || rating < 1 || rating > 5) {
      return t("hrm.g3.reviews.validation.rating");
    }
    return null;
  }

  async function runMutation(action: () => Promise<void>) {
    setSelfFormError(null);
    setTeamFormError(null);
    setMutationError(null);
    setNotice(null);
    setBusy(true);
    try {
      await action();
    } catch (err) {
      setMutationError(hrmErrorMessage(err).message);
    } finally {
      setBusy(false);
    }
  }

  function createSelfReview() {
    const validationError = validateReviewForm(selfForm);
    if (validationError) {
      setSelfFormError(validationError);
      return;
    }
    void runMutation(async () => {
      await hrG3Api.createSelfReview({
        cycle: selfForm.cycle,
        periodStart: selfForm.periodStart,
        periodEnd: selfForm.periodEnd,
        rating: Number(selfForm.rating),
        comments: selfForm.comments,
      });
      // No fake local-only success: reconcile from the backend response path.
      await load();
      setSelfForm(EMPTY_FORM);
      setNotice(t("hrm.g3.reviews.notice.created"));
    });
  }

  function createTeamReview() {
    const validationError = validateReviewForm(teamForm);
    if (validationError) {
      setTeamFormError(validationError);
      return;
    }
    if (teamEmploymentId.trim() === "") {
      setTeamFormError(t("hrm.g3.reviews.team.employmentId"));
      return;
    }
    void runMutation(async () => {
      await hrG3Api.createTeamReview(teamEmploymentId, {
        cycle: teamForm.cycle,
        periodStart: teamForm.periodStart,
        periodEnd: teamForm.periodEnd,
        rating: Number(teamForm.rating),
        comments: teamForm.comments,
      });
      await load();
      setTeamForm(EMPTY_FORM);
      setTeamEmploymentId("");
      setNotice(t("hrm.g3.reviews.notice.created"));
    });
  }

  function submitReview(reviewId: string) {
    void runMutation(async () => {
      await hrG3Api.submitReview(reviewId);
      await load();
      setNotice(t("hrm.g3.reviews.notice.submitted"));
    });
  }

  function acknowledgeReview(reviewId: string) {
    void runMutation(async () => {
      await hrG3Api.acknowledgeReview(reviewId);
      await load();
      setNotice(t("hrm.g3.reviews.notice.acknowledged"));
    });
  }

  function cancelTeamReview(reviewId: string) {
    void runMutation(async () => {
      await hrG3Api.cancelTeamReview(reviewId);
      await load();
      setNotice(t("hrm.g3.reviews.notice.cancelled"));
    });
  }

  if (AUTH_LOADING_STATES.has(state)) {
    return <AuthLoadingState phase="session" />;
  }

  if (!canView) {
    return (
      <HrWorkspace capabilities={capabilities} activeHref="/hr/performance/reviews" translate={t}>
        <p role="alert" className={styles.kpiHint} data-testid="reviews-capability-denied">
          {t("hrm.g3.reviews.forbidden")}
        </p>
      </HrWorkspace>
    );
  }

  const statusLabel = (code: string) => t(`hrm.g3.reviews.state.${code}`);
  const sourceLabel = (code: string) => t(`hrm.g3.reviews.source.${code}`);
  const periodLabel = (review: G3PerformanceReview) =>
    `${formatLocalizedDate(review.periodStart, locale)} – ${formatLocalizedDate(review.periodEnd, locale)}`;

  const statusBadge = (review: G3PerformanceReview) => (
    <HrStateBadge label={statusLabel(review.status)} code={review.status} tone={toneForState(review.status)} />
  );

  const sourceBadge = (review: G3PerformanceReview) => (
    <SnadStatusBadge label={sourceLabel(review.source)} source={review.source} />
  );

  const reviewColumns = (actions?: (review: G3PerformanceReview) => ReactNode): HrColumn<G3PerformanceReview>[] => [
    { key: "cycle", header: t("hrm.g3.reviews.col.cycle") },
    { key: "period", header: t("hrm.g3.reviews.col.period"), render: periodLabel },
    { key: "source", header: t("hrm.g3.reviews.col.source"), render: sourceBadge },
    { key: "status", header: t("hrm.g3.reviews.col.status"), render: statusBadge },
    {
      key: "rating",
      header: t("hrm.g3.reviews.col.rating"),
      align: "end",
      render: (review) => (review.rating === null ? "—" : String(review.rating)),
    },
    { key: "comments", header: t("hrm.g3.reviews.col.comments"), render: (review) => review.comments ?? "—" },
    { key: "updatedAt", header: t("hrm.g3.reviews.col.updated"), render: (review) => formatLocalizedDate(review.updatedAt.slice(0, 10), locale) },
    ...(actions ? [{ key: "actions", header: "", render: actions } as HrColumn<G3PerformanceReview>] : []),
  ];

  const totalReviews = reviews.length;
  const submittedCount = reviews.filter((review) => review.status === "SUBMITTED").length;
  const acknowledgedCount = reviews.filter((review) => review.status === "ACKNOWLEDGED").length;

  const renderReviewForm = (
    prefix: "reviews" | "reviews-team",
    form: ReviewFormState,
    onFormChange: (form: ReviewFormState) => void,
    formError: string | null,
    onSubmit: () => void,
  ) => (
    <form
      onSubmit={(event) => { event.preventDefault(); onSubmit(); }}
      aria-label={prefix === "reviews" ? t("hrm.g3.reviews.action.create") : t("hrm.g3.reviews.team.create")}
      data-testid={`${prefix}-create-form`}
      className={sdsStyles.formRow}
    >
      <div className={sdsStyles.formGroup}>
        <label htmlFor={`${prefix}-cycle-input`} className={sdsStyles.formLabel}>{t("hrm.g3.reviews.form.cycle")}</label>
        <input
          id={`${prefix}-cycle-input`}
          className={sdsStyles.formInput}
          value={form.cycle}
          onChange={(event) => onFormChange({ ...form, cycle: event.target.value })}
          disabled={busy}
          maxLength={80}
          aria-required="true"
          data-testid={`${prefix}-cycle-input`}
        />
      </div>
      <div className={sdsStyles.formGroup}>
        <label htmlFor={`${prefix}-period-start-input`} className={sdsStyles.formLabel}>{t("hrm.g3.reviews.form.periodStart")}</label>
        <input
          id={`${prefix}-period-start-input`}
          type="date"
          className={sdsStyles.formInput}
          value={form.periodStart}
          onChange={(event) => onFormChange({ ...form, periodStart: event.target.value })}
          disabled={busy}
          aria-required="true"
          data-testid={`${prefix}-period-start-input`}
        />
      </div>
      <div className={sdsStyles.formGroup}>
        <label htmlFor={`${prefix}-period-end-input`} className={sdsStyles.formLabel}>{t("hrm.g3.reviews.form.periodEnd")}</label>
        <input
          id={`${prefix}-period-end-input`}
          type="date"
          className={sdsStyles.formInput}
          value={form.periodEnd}
          onChange={(event) => onFormChange({ ...form, periodEnd: event.target.value })}
          disabled={busy}
          aria-required="true"
          data-testid={`${prefix}-period-end-input`}
        />
      </div>
      <div className={sdsStyles.formGroup}>
        <label htmlFor={`${prefix}-rating-select`} className={sdsStyles.formLabel}>{t("hrm.g3.reviews.form.rating")}</label>
        <select
          id={`${prefix}-rating-select`}
          className={sdsStyles.formInput}
          value={form.rating}
          onChange={(event) => onFormChange({ ...form, rating: event.target.value })}
          disabled={busy}
          aria-required="true"
          data-testid={`${prefix}-rating-select`}
        >
          <option value="">{t("hrm.g3.reviews.form.rating.none")}</option>
          {[1, 2, 3, 4, 5].map((value) => (
            <option key={value} value={value}>{value}</option>
          ))}
        </select>
      </div>
      <div className={sdsStyles.formGroup}>
        <label htmlFor={`${prefix}-comments-input`} className={sdsStyles.formLabel}>{t("hrm.g3.reviews.form.comments")}</label>
        <textarea
          id={`${prefix}-comments-input`}
          className={sdsStyles.formInput}
          value={form.comments}
          onChange={(event) => onFormChange({ ...form, comments: event.target.value })}
          disabled={busy}
          maxLength={4000}
          rows={3}
          data-testid={`${prefix}-comments-input`}
        />
      </div>
      {formError ? (
        <p role="alert" data-kind="error" className={sdsStyles.formError} data-testid={`${prefix}-form-error`}>
          {formError}
        </p>
      ) : null}
      <div>
        <button type="submit" className={sdsStyles.primaryButton} disabled={busy} data-testid={`${prefix}-create-save`}>
          {busy ? t("hrm.g3.reviews.action.saving") : prefix === "reviews" ? t("hrm.g3.reviews.action.create") : t("hrm.g3.reviews.team.create")}
        </button>
      </div>
    </form>
  );

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/performance/reviews" translate={t}>
      <div data-testid="reviews-product-surface">
        <HrProductHeader
          eyebrow={t("hrm.g3.reviews.eyebrow")}
          title={t("hrm.g3.reviews.title")}
          subtitle={t("hrm.g3.reviews.subtitle")}
        />

        {loading ? (
          <HrLoading label={t("hrm.g3.reviews.loading")} />
        ) : error ? (
          <HrErrorState error={error} onRetry={load} />
        ) : (
          <div data-testid="reviews-ready">
            {notice ? (
              <div data-testid="reviews-notice">
                <SnadSuccessNotice>{notice}</SnadSuccessNotice>
              </div>
            ) : null}

            {mutationError ? (
              <p role="alert" className={styles.kpiHint} data-kind="error" data-testid="reviews-mutation-error">
                {mutationError}
              </p>
            ) : null}

            <HrKpiGrid label={t("hrm.g3.reviews.title")}>
              <div data-testid="reviews-kpi-total">
                <HrKpiCard label={t("hrm.g3.reviews.kpi.total")} value={String(totalReviews)} hint={t("hrm.g3.reviews.col.cycle")} />
              </div>
              <div data-testid="reviews-kpi-submitted">
                <HrKpiCard label={t("hrm.g3.reviews.kpi.submitted")} value={String(submittedCount)} hint={t("hrm.g3.reviews.col.status")} tone="attention" />
              </div>
              <div data-testid="reviews-kpi-acknowledged">
                <HrKpiCard label={t("hrm.g3.reviews.kpi.acknowledged")} value={String(acknowledgedCount)} hint={t("hrm.g3.reviews.col.status")} tone="positive" />
              </div>
            </HrKpiGrid>

            {canViewSelf ? (
              <div data-testid="reviews-self-panel">
                <HrOperationalPanel
                  label={t("hrm.g3.reviews.title")}
                  title={t("hrm.g3.reviews.title")}
                  description={t("hrm.g3.reviews.subtitle")}
                >
                  {canSubmit ? (
                    <HrActionBar label={t("hrm.g3.reviews.action.create")}>
                      {renderReviewForm("reviews", selfForm, setSelfForm, selfFormError, createSelfReview)}
                    </HrActionBar>
                  ) : null}

                  {reviews.length === 0 ? (
                    <HrEmptyState
                      title={t("hrm.g3.reviews.empty.title")}
                      description={t("hrm.g3.reviews.empty.description")}
                    />
                  ) : (
                    <>
                      <HrDataTable<G3PerformanceReview>
                        caption={t("hrm.g3.reviews.title")}
                        columns={reviewColumns(canSubmit ? (review) => (
                          <>
                            {review.status === "DRAFT" ? (
                              <button
                                type="button"
                                className={sdsStyles.ghostButton}
                                disabled={busy}
                                onClick={() => submitReview(review.id)}
                                data-testid={`reviews-submit-${review.id}`}
                              >
                                {t("hrm.g3.reviews.action.submit")}
                              </button>
                            ) : null}
                            {review.status === "SUBMITTED" ? (
                              <button
                                type="button"
                                className={sdsStyles.primaryButton}
                                disabled={busy}
                                onClick={() => acknowledgeReview(review.id)}
                                data-testid={`reviews-acknowledge-${review.id}`}
                              >
                                {t("hrm.g3.reviews.action.acknowledge")}
                              </button>
                            ) : null}
                          </>
                        ) : undefined)}
                        rows={reviews}
                        rowKey={(review) => review.id}
                        emptyTitle={t("hrm.g3.reviews.empty.title")}
                        emptyDescription={t("hrm.g3.reviews.empty.description")}
                      />
                      <HrMobileRecordList label={t("hrm.g3.reviews.title")}>
                        {reviews.map((review) => (
                          <article key={review.id} className={styles.statCard}>
                            <strong>{review.cycle}</strong>
                            <span>{periodLabel(review)}</span>
                            <span>{sourceBadge(review)} {statusBadge(review)}</span>
                            <span>{t("hrm.g3.reviews.col.rating")}: {review.rating ?? "—"}</span>
                            <span>{review.comments ?? "—"}</span>
                          </article>
                        ))}
                      </HrMobileRecordList>
                    </>
                  )}
                </HrOperationalPanel>
              </div>
            ) : null}

            {canManageTeam ? (
              <div data-testid="reviews-team-panel">
                <HrOperationalPanel
                  label={t("hrm.g3.reviews.team.title")}
                  title={t("hrm.g3.reviews.team.title")}
                  description={t("hrm.g3.reviews.team.description")}
                >
                  {teamError ? (
                    <HrErrorState error={teamError} onRetry={load} />
                  ) : (
                    <>
                      <HrActionBar label={t("hrm.g3.reviews.team.create")}>
                        <div className={sdsStyles.formGroup}>
                          <label htmlFor="reviews-team-employment-input" className={sdsStyles.formLabel}>
                            {t("hrm.g3.reviews.team.employmentId")}
                          </label>
                          <input
                            id="reviews-team-employment-input"
                            className={sdsStyles.formInput}
                            value={teamEmploymentId}
                            onChange={(event) => setTeamEmploymentId(event.target.value)}
                            disabled={busy}
                            aria-required="true"
                            data-testid="reviews-team-employment-input"
                          />
                        </div>
                        {renderReviewForm("reviews-team", teamForm, setTeamForm, teamFormError, createTeamReview)}
                      </HrActionBar>

                      {teamReviews && teamReviews.length > 0 ? (
                        <HrDataTable<G3PerformanceReview>
                          caption={t("hrm.g3.reviews.team.title")}
                          columns={reviewColumns((review) => (
                            review.status === "DRAFT" ? (
                              <button
                                type="button"
                                className={sdsStyles.ghostButton}
                                disabled={busy}
                                onClick={() => cancelTeamReview(review.id)}
                                data-testid={`reviews-team-cancel-${review.id}`}
                              >
                                {t("hrm.g3.reviews.action.cancel")}
                              </button>
                            ) : null
                          ))}
                          rows={teamReviews}
                          rowKey={(review) => review.id}
                          emptyTitle={t("hrm.g3.reviews.empty.title")}
                          emptyDescription={t("hrm.g3.reviews.empty.description")}
                        />
                      ) : (
                        <HrEmptyState
                          title={t("hrm.g3.reviews.empty.title")}
                          description={t("hrm.g3.reviews.team.description")}
                        />
                      )}
                    </>
                  )}
                </HrOperationalPanel>
              </div>
            ) : null}
          </div>
        )}
      </div>
    </HrWorkspace>
  );
}
