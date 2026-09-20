"use client";

/**
 * Recruitment Dashboard — G1-T11 Screen 1.
 * ============================================================================
 * KPI tiles for openings, candidates, interviews, offers, hires + recent
 * activity list. Permission-scoped: tiles that the user lacks capability
 * for are NOT rendered (spec §14: "no capability → no control rendered,
 * not merely disabled").
 *
 * Data sources:
 *   - hrmRecruitmentApi.listOpenings() — opening counts by status
 *   - hrmRecruitmentApi.listCandidates() — active candidate count
 *   - hrmRecruitmentApi.listApplications() — application funnel summary
 *   - hrmRecruitmentApi.listApplications({ stage: "OFFERED" }) — open offers
 *   - hrmRecruitmentApi.listApplications({ stage: "HIRED" }) — completed hires
 *
 * Permission scoping (capabilities come from /me; backend is authoritative):
 *   - HRM.RECRUITMENT.OPENING.VIEW — openings tile
 *   - HRM.RECRUITMENT.CANDIDATE.VIEW — candidates tile
 *   - HRM.RECRUITMENT.APPLICATION.MANAGE — applications/offers/hires tiles
 *
 * Tenant scoping: every fetch is tenant-scoped by session context (the JWT
 * in the BFF cookie; the backend JwtAuthenticationFilter enforces tenant_id
 * binding). The client never sends tenantId in the request body or query.
 *
 * Arabic/RTL: this screen uses logical CSS only (no left/right physical
 * properties). All strings come from i18n keys — no hard-coded literals.
 * Backend status codes are rendered through openingStatusLabel(dict, code)
 * which falls back to the raw code if the i18n key is missing (never invented).
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { AuthLoadingState } from "@/components/auth/auth-loading-state";
import { useAuth } from "@/lib/auth/auth-provider";
import { HRM_CAPABILITIES } from "@/lib/auth/capabilities";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { translations } from "@/lib/i18n";
import { hrmRecruitmentApi, type OpeningSummaryResponse } from "@/lib/api/hr-v2-recruitment-api";
import { HrWorkspace } from "../components/hr-workspace";
import { HrErrorState, HrLoading } from "../components/hr-feedback";
import { formatArabicDate, openingStatusLabel } from "../hr-labels";
import { toneForState } from "../components/hr-state-badge";
import styles from "../hr.module.css";

interface ActivityEntry {
  id: string;
  text: string;
  timestamp: string;
  tone: "info" | "success" | "warning" | "danger" | "neutral";
}

interface DashboardKpiState {
  openings: number;
  candidates: number;
  applications: number;
  offers: number;
  hires: number;
  recentActivity: ActivityEntry[];
}

function emptyKpi(): DashboardKpiState {
  return { openings: 0, candidates: 0, applications: 0, offers: 0, hires: 0, recentActivity: [] };
}

export default function RecruitmentDashboardPage() {
  const { state, me } = useAuth();
  const { t, locale } = useI18n();
  const capabilities = me?.capabilities ?? [];

  const canViewOpenings = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_OPENING_VIEW);
  const canViewCandidates = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_CANDIDATE_VIEW);
  const canManageApplications = capabilities.includes(HRM_CAPABILITIES.RECRUITMENT_APPLICATION_MANAGE);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [kpi, setKpi] = useState<DashboardKpiState>(emptyKpi());

  const dict = useMemo(() => translations[locale], [locale]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Fetch in parallel — each request is tenant-scoped via session context.
      const [openings, candidates, applications, offers, hires] = await Promise.all([
        canViewOpenings ? hrmRecruitmentApi.listOpenings() : Promise.resolve([] as OpeningSummaryResponse[]),
        canViewCandidates ? hrmRecruitmentApi.listCandidates() : Promise.resolve([]),
        canManageApplications ? hrmRecruitmentApi.listApplications() : Promise.resolve([]),
        canManageApplications ? hrmRecruitmentApi.listApplications({ stage: "OFFERED" }) : Promise.resolve([]),
        canManageApplications ? hrmRecruitmentApi.listApplications({ stage: "HIRED" }) : Promise.resolve([]),
      ]);

      // Activity feed — derived from openings + applications timestamps, newest first.
      // The backend does not expose a dedicated activity endpoint at G1; we derive
      // a minimal feed from what we have. No PII is rendered in the activity text.
      const activity: ActivityEntry[] = [];
      for (const o of openings.slice(0, 5)) {
        const label = openingStatusLabel(dict, o.status);
        activity.push({
          id: `opening-${o.openingId}`,
          text: `${label} — ${o.title}`,
          timestamp: o.updatedAt,
          tone: toneForState(o.status) === "danger" ? "danger"
            : toneForState(o.status) === "success" ? "success"
            : toneForState(o.status) === "warning" ? "warning" : "info",
        });
      }
      for (const a of applications.slice(0, 5)) {
        activity.push({
          id: `app-${a.applicationId}`,
          text: `${t("hrm.recruitment.dashboard.kpi.interviews")} — ${a.openingTitle}`,
          timestamp: a.updatedAt,
          tone: toneForState(a.stage) === "success" ? "success" : "info",
        });
      }
      activity.sort((x, y) => y.timestamp.localeCompare(x.timestamp));
      const top = activity.slice(0, 8);

      setKpi({
        openings: openings.length,
        candidates: candidates.length,
        applications: applications.length,
        offers: offers.length,
        hires: hires.length,
        recentActivity: top,
      });
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [canViewOpenings, canViewCandidates, canManageApplications, t, dict]);

  useEffect(() => {
    if (state !== "AUTHENTICATED") return;
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [state, load]);

  if (["INITIALIZING", "CHECKING_SESSION", "REFRESHING"].includes(state)) {
    return <AuthLoadingState phase="session" />;
  }

  return (
    <HrWorkspace capabilities={capabilities} activeHref="/hr/recruitment">
      <header>
        <h1>{t("hrm.recruitment.dashboard.title")}</h1>
        <p className={styles.kpiHint}>{t("hrm.recruitment.dashboard.subtitle")}</p>
      </header>

      {loading ? (
        <HrLoading />
      ) : error ? (
        <HrErrorState error={error} onRetry={load} />
      ) : (
        <>
          <section aria-label={t("hrm.recruitment.dashboard.title")} className={styles.kpiGrid}>
            {canViewOpenings ? (
              <article className={styles.kpiTile} aria-label={t("hrm.recruitment.dashboard.kpi.openings")}>
                <span className={styles.kpiValue} data-testid="kpi-openings">{kpi.openings}</span>
                <span className={styles.kpiLabel}>{t("hrm.recruitment.dashboard.kpi.openings")}</span>
              </article>
            ) : null}
            {canViewCandidates ? (
              <article className={styles.kpiTile} aria-label={t("hrm.recruitment.dashboard.kpi.candidates")}>
                <span className={styles.kpiValue} data-testid="kpi-candidates">{kpi.candidates}</span>
                <span className={styles.kpiLabel}>{t("hrm.recruitment.dashboard.kpi.candidates")}</span>
              </article>
            ) : null}
            {canManageApplications ? (
              <>
                <article className={styles.kpiTile} aria-label={t("hrm.recruitment.dashboard.kpi.interviews")}>
                  <span className={styles.kpiValue} data-testid="kpi-applications">{kpi.applications}</span>
                  <span className={styles.kpiLabel}>{t("hrm.recruitment.dashboard.kpi.interviews")}</span>
                </article>
                <article className={styles.kpiTile} aria-label={t("hrm.recruitment.dashboard.kpi.offers")}>
                  <span className={styles.kpiValue} data-testid="kpi-offers">{kpi.offers}</span>
                  <span className={styles.kpiLabel}>{t("hrm.recruitment.dashboard.kpi.offers")}</span>
                </article>
                <article className={styles.kpiTile} aria-label={t("hrm.recruitment.dashboard.kpi.hires")}>
                  <span className={styles.kpiValue} data-testid="kpi-hires">{kpi.hires}</span>
                  <span className={styles.kpiLabel}>{t("hrm.recruitment.dashboard.kpi.hires")}</span>
                </article>
              </>
            ) : null}
          </section>

          {!canViewOpenings && !canViewCandidates && !canManageApplications ? (
            <p role="status" className={styles.kpiHint}>{t("hrm.recruitment.dashboard.permissionHint")}</p>
          ) : null}

          <section aria-label={t("hrm.recruitment.dashboard.recentActivity")}>
            <h2>{t("hrm.recruitment.dashboard.recentActivity")}</h2>
            {kpi.recentActivity.length === 0 ? (
              <p role="status">{t("hrm.recruitment.dashboard.noActivity")}</p>
            ) : (
              <ol className={styles.activityList}>
                {kpi.recentActivity.map((item) => (
                  <li key={item.id} className={styles.activityItem}>
                    <span>{item.text}</span>
                    <span className={styles.activityTimestamp}>{formatArabicDate(item.timestamp)}</span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </>
      )}
    </HrWorkspace>
  );
}
