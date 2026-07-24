"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { crmApi, type CrmDashboard } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { formatDate, formatNumber } from "../../crm-view-utils";
import { CrmLoading } from "../../components/crm-loading";
import { CrmError } from "../../components/crm-error";
import { CrmEmpty } from "../../components/crm-empty";
import styles from "../../crm.module.css";

/** CRM overview dashboard and entry point to governed Workflow/AI operations. */
export default function CrmOverviewPage() {
  const { t, locale } = useI18n();
  const [dashboard, setDashboard] = useState<CrmDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const next = await crmApi.dashboard();
      setDashboard(next);
      setUpdatedAt(new Date());
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setDashboard(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  if (loading && !dashboard) {
    return <div className={styles.contentInner}><CrmLoading /></div>;
  }

  if (error && !dashboard) {
    return <div className={styles.contentInner}><CrmError message={error} onRetry={() => void reload()} /></div>;
  }

  if (!dashboard) {
    return <div className={styles.contentInner}><CrmEmpty /></div>;
  }

  const kpis = [
    { key: "crm.overview.kpi.accounts", value: String(dashboard.accounts) },
    { key: "crm.overview.kpi.contacts", value: String(dashboard.contacts) },
    { key: "crm.overview.kpi.openLeads", value: String(dashboard.openLeads) },
    { key: "crm.overview.kpi.openOpportunities", value: String(dashboard.openOpportunities) },
    { key: "crm.overview.kpi.weightedPipeline", value: formatNumber(dashboard.weightedPipeline) },
    { key: "crm.overview.kpi.overdueActivities", value: String(dashboard.overdueActivities) },
  ];
  const recentActivity = dashboard.recentActivity ?? [];

  return (
    <div className={styles.contentInner}>
      <div className={styles.rowHeader}>
        <div>
          <h1 className={styles.pageTitle}>{t("crm.overview.title")}</h1>
          <p className={styles.pageDescription}>{t("crm.overview.description")}</p>
        </div>
        <div className={styles.rowHeader}>
          <Link href="/crm/integrations">
            {locale === "ar" ? "تكاملات سير العمل والذكاء" : "Workflow & AI integrations"}
          </Link>
          <button type="button" onClick={() => void reload()} disabled={loading}>
            {loading ? t("crm.state.loading") : t("crm.shell.refresh")}
          </button>
        </div>
      </div>

      {error ? <div className={styles.error} role="alert">{error}</div> : null}

      <section aria-label={t("crm.overview.title")}>
        <div className={styles.kpiGrid}>
          {kpis.map((kpi) => (
            <article key={kpi.key} className={styles.kpiCard}>
              <span className={styles.kpiLabel}>{t(kpi.key)}</span>
              <span className={styles.kpiValue}>{kpi.value}</span>
            </article>
          ))}
        </div>
      </section>

      <section className={styles.overviewSection} aria-label={t("crm.overview.recentActivity")}>
        <div className={styles.rowHeader}>
          <h2 className={styles.overviewSectionTitle}>{t("crm.overview.recentActivity")}</h2>
          {updatedAt ? (
            <span className={styles.kpiHint}>
              {t("crm.overview.lastUpdated")}: {formatDate(updatedAt.toISOString())}
            </span>
          ) : null}
        </div>
        {recentActivity.length === 0 ? (
          <p className={styles.notice}>{t("crm.overview.noRecentActivity")}</p>
        ) : (
          <ol className={styles.timeline}>
            {recentActivity.map((event) => (
              <li key={event.id}>
                <strong>{event.summary}</strong>
                <span>{formatDate(event.occurred_at)}</span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}
