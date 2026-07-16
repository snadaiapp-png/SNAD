"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import { toUserFacingError } from "@/lib/api/user-facing-errors";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { CrmLoading } from "../../components/crm-loading";
import styles from "../../crm.module.css";

interface ReportsDashboard {
  salesPipeline: {
    stages: Array<{ stage_name: string; opportunity_count: number; total_amount: string; avg_probability: string }>;
    total_pipeline_value: string;
    total_opportunities: number;
    weighted_pipeline_value: string;
  };
  leadConversion: {
    total_leads: number;
    converted_leads: number;
    qualified_leads: number;
    new_leads: number;
    conversion_rate: number;
    by_source: Array<{ source: string; count: number; converted: number }>;
  };
  activitySummary: {
    total_activities: number;
    open_activities: number;
    completed_activities: number;
    total_tasks: number;
    open_tasks: number;
    completed_tasks: number;
  };
  accountGrowth: {
    total_accounts: number;
    active_accounts: number;
    new_this_month: number;
    monthly_growth: Array<{ month: string; new_accounts: number; cumulative: number }>;
  };
}

/**
 * CRM Reports route — /crm/reports
 *
 * Aggregated read-only dashboards:
 *   - Sales pipeline by stage
 *   - Lead conversion rates by source
 *   - Activity & task summary
 *   - Account growth (6-month trend)
 *
 * Branch: feature/crm-reports
 */
export default function CrmReportsPage() {
  const { t } = useI18n();
  const [data, setData] = useState<ReportsDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const reload = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const result = await crmApi.reports();
      setData(result as unknown as ReportsDashboard);
    } catch (reason) {
      setError(toUserFacingError(reason).message);
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void reload(), 0);
    return () => window.clearTimeout(timer);
  }, [reload]);

  if (loading) return (
    <div className={styles.contentInner}>
      <h1 className={styles.pageTitle}>{t("crm.reports.title")}</h1>
      <CrmLoading rows={6} />
    </div>
  );

  if (error) return (
    <div className={styles.contentInner}>
      <h1 className={styles.pageTitle}>{t("crm.reports.title")}</h1>
      <div className={styles.error} role="alert">{error}</div>
    </div>
  );

  if (!data) return null;

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("crm.reports.title")}</h1>
        <p className={styles.pageDescription}>{t("crm.reports.description")}</p>
      </div>

      {/* KPI Cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "1rem", marginBottom: "2rem" }}>
        <KpiCard label={t("crm.reports.kpi.totalAccounts")} value={String(data.accountGrowth.total_accounts)} />
        <KpiCard label={t("crm.reports.kpi.activeAccounts")} value={String(data.accountGrowth.active_accounts)} />
        <KpiCard label={t("crm.reports.kpi.pipelineValue")} value={formatCurrency(data.salesPipeline.total_pipeline_value)} />
        <KpiCard label={t("crm.reports.kpi.weightedPipeline")} value={formatCurrency(data.salesPipeline.weighted_pipeline_value)} />
        <KpiCard label={t("crm.reports.kpi.totalOpportunities")} value={String(data.salesPipeline.total_opportunities)} />
        <KpiCard label={t("crm.reports.kpi.conversionRate")} value={`${data.leadConversion.conversion_rate.toFixed(1)}%`} />
        <KpiCard label={t("crm.reports.kpi.openActivities")} value={String(data.activitySummary.open_activities)} />
        <KpiCard label={t("crm.reports.kpi.openTasks")} value={String(data.activitySummary.open_tasks)} />
      </div>

      <section className={styles.workspace}>
        {/* Sales Pipeline by Stage */}
        <div className={styles.listCard}>
          <h2 className={styles.sectionHeading}>{t("crm.reports.salesPipeline.title")}</h2>
          {data.salesPipeline.stages.length === 0 ? (
            <p style={{ opacity: 0.6 }}>{t("crm.reports.salesPipeline.empty")}</p>
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.reports.salesPipeline.stage")}</th>
                    <th>{t("crm.reports.salesPipeline.opportunities")}</th>
                    <th>{t("crm.reports.salesPipeline.totalAmount")}</th>
                    <th>{t("crm.reports.salesPipeline.avgProbability")}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.salesPipeline.stages.map((s, i) => (
                    <tr key={i}>
                      <td>{s.stage_name}</td>
                      <td>{s.opportunity_count}</td>
                      <td>{formatCurrency(s.total_amount)}</td>
                      <td>{Number(s.avg_probability).toFixed(1)}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Lead Conversion by Source */}
        <div className={styles.listCard}>
          <h2 className={styles.sectionHeading}>{t("crm.reports.leadConversion.title")}</h2>
          <div style={{ marginBottom: "1rem", display: "flex", gap: "1rem", flexWrap: "wrap" }}>
            <KpiCard label={t("crm.reports.leadConversion.total")} value={String(data.leadConversion.total_leads)} />
            <KpiCard label={t("crm.reports.leadConversion.converted")} value={String(data.leadConversion.converted_leads)} />
            <KpiCard label={t("crm.reports.leadConversion.newLeads")} value={String(data.leadConversion.new_leads)} />
          </div>
          {data.leadConversion.by_source.length === 0 ? (
            <p style={{ opacity: 0.6 }}>{t("crm.reports.leadConversion.empty")}</p>
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.reports.leadConversion.source")}</th>
                    <th>{t("crm.reports.leadConversion.count")}</th>
                    <th>{t("crm.reports.leadConversion.converted")}</th>
                    <th>{t("crm.reports.leadConversion.rate")}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.leadConversion.by_source.map((s, i) => (
                    <tr key={i}>
                      <td>{s.source}</td>
                      <td>{s.count}</td>
                      <td>{s.converted}</td>
                      <td>{s.count > 0 ? `${((s.converted / s.count) * 100).toFixed(1)}%` : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>

      {/* Account Growth */}
      <section className={styles.workspace} style={{ marginTop: "2rem" }}>
        <div className={styles.listCard}>
          <h2 className={styles.sectionHeading}>{t("crm.reports.accountGrowth.title")}</h2>
          <div style={{ marginBottom: "1rem", display: "flex", gap: "1rem", flexWrap: "wrap" }}>
            <KpiCard label={t("crm.reports.accountGrowth.newThisMonth")} value={String(data.accountGrowth.new_this_month)} />
          </div>
          {data.accountGrowth.monthly_growth.length === 0 ? (
            <p style={{ opacity: 0.6 }}>{t("crm.reports.accountGrowth.empty")}</p>
          ) : (
            <div className={styles.tableWrap}>
              <table>
                <thead>
                  <tr>
                    <th>{t("crm.reports.accountGrowth.month")}</th>
                    <th>{t("crm.reports.accountGrowth.newAccounts")}</th>
                    <th>{t("crm.reports.accountGrowth.cumulative")}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.accountGrowth.monthly_growth.map((g, i) => (
                    <tr key={i}>
                      <td>{g.month}</td>
                      <td>{g.new_accounts}</td>
                      <td>{g.cumulative}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Activity Summary */}
        <div className={styles.listCard}>
          <h2 className={styles.sectionHeading}>{t("crm.reports.activitySummary.title")}</h2>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: "0.5rem" }}>
            <KpiCard label={t("crm.reports.activitySummary.totalActivities")} value={String(data.activitySummary.total_activities)} />
            <KpiCard label={t("crm.reports.activitySummary.openActivities")} value={String(data.activitySummary.open_activities)} />
            <KpiCard label={t("crm.reports.activitySummary.completed")} value={String(data.activitySummary.completed_activities)} />
            <KpiCard label={t("crm.reports.activitySummary.totalTasks")} value={String(data.activitySummary.total_tasks)} />
            <KpiCard label={t("crm.reports.activitySummary.openTasks")} value={String(data.activitySummary.open_tasks)} />
            <KpiCard label={t("crm.reports.activitySummary.completedTasks")} value={String(data.activitySummary.completed_tasks)} />
          </div>
        </div>
      </section>
    </div>
  );
}

function KpiCard({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ padding: "1rem", borderRadius: "0.5rem", border: "1px solid currentColor", opacity: 0.9 }}>
      <div style={{ fontSize: "0.75rem", opacity: 0.7, marginBottom: "0.25rem" }}>{label}</div>
      <div style={{ fontSize: "1.5rem", fontWeight: 600 }}>{value}</div>
    </div>
  );
}

function formatCurrency(value: string | null | undefined): string {
  if (!value) return "0";
  const num = Number(value);
  if (isNaN(num)) return value;
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 }).format(num);
}
