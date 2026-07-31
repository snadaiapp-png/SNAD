"use client";

import { useCallback, useEffect, useState } from "react";
import { crmApi } from "@/lib/api/crm";
import type {
  CrmSalesPipelineReport,
  CrmLeadConversionReport,
  CrmActivitySummaryReport,
} from "@/lib/api/crm";
import { useCrmI18n } from "../crm-i18n";
import styles from "../crm-command-center.module.css";

/* ============================================================================
 *  Report type helpers
 * ============================================================================ */

type ReportView = "pipeline" | "leads" | "activities";

const REPORT_TABS: { key: ReportView; labelKey: string }[] = [
  { key: "pipeline", labelKey: "reports.pipeline" },
  { key: "leads", labelKey: "reports.leads" },
  { key: "activities", labelKey: "reports.activities" },
];

/* ============================================================================
 *  ReportsTab — main component
 * ============================================================================ */

export function ReportsTab() {
  const { t } = useCrmI18n();
  const [activeReport, setActiveReport] = useState<ReportView>("pipeline");
  const [pipeline, setPipeline] = useState<CrmSalesPipelineReport | null>(null);
  const [leads, setLeads] = useState<CrmLeadConversionReport | null>(null);
  const [activities, setActivities] = useState<CrmActivitySummaryReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /* ---------- data fetching ---------- */

  const loadReports = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, l, a] = await Promise.all([
        crmApi.salesPipeline(),
        crmApi.leadConversion(),
        crmApi.activitySummary(),
      ]);
      setPipeline(p);
      setLeads(l);
      setActivities(a);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load reports");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadReports();
  }, [loadReports]);

  /* ---------- render ---------- */

  return (
    <div className={styles.tabContainer}>
      {/* Header */}
      <div className={styles.tabHeader}>
        <h2 className={styles.tabTitle}>{t("tab.reports")}</h2>
      </div>

      {/* Report tabs */}
      <div className={styles.filterBar}>
        {REPORT_TABS.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveReport(tab.key)}
            className={`${styles.filterChip} ${activeReport === tab.key ? styles.filterChipActive : ""}`}
          >
            {t(tab.labelKey)}
          </button>
        ))}
      </div>

      {/* Content */}
      {loading ? (
        <div className={styles.loadingState}>Loading reports…</div>
      ) : error ? (
        <div className={styles.errorState}>
          {error}
          <button onClick={loadReports} className={styles.retryButton}>
            Retry
          </button>
        </div>
      ) : (
        <div>
          {activeReport === "pipeline" && pipeline && (
            <PipelineVelocityReport data={pipeline} />
          )}
          {activeReport === "leads" && leads && (
            <LeadConversionReport data={leads} />
          )}
          {activeReport === "activities" && activities && (
            <ActivityThroughputReport data={activities} />
          )}
        </div>
      )}
    </div>
  );
}

/* ============================================================================
 *  Pipeline Velocity Report
 * ============================================================================ */

function PipelineVelocityReport({ data }: { data: CrmSalesPipelineReport }) {
  return (
    <div>
      {/* Summary cards */}
      <div style={{ display: "flex", gap: "1rem", marginBottom: "1.5rem" }}>
        <SummaryCard label="Total Pipeline" value={`$${Number(data.total_pipeline_value).toLocaleString()}`} />
        <SummaryCard label="Total Opportunities" value={String(data.total_opportunities)} />
        <SummaryCard label="Weighted Value" value={`$${Number(data.weighted_pipeline_value).toLocaleString()}`} />
      </div>

      {/* Stages table */}
      <div className={styles.tableContainer}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Stage</th>
              <th>Opportunities</th>
              <th>Total Amount</th>
              <th>Avg Probability</th>
            </tr>
          </thead>
          <tbody>
            {data.stages.map((stage) => (
              <tr key={stage.stage_id} className={styles.tableRow}>
                <td className={styles.tableCellTitle}>{stage.stage_name}</td>
                <td>{stage.opportunity_count}</td>
                <td>${Number(stage.total_amount).toLocaleString()}</td>
                <td>{(Number(stage.avg_probability) * 100).toFixed(0)}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/* ============================================================================
 *  Lead Conversion Report
 * ============================================================================ */

function LeadConversionReport({ data }: { data: CrmLeadConversionReport }) {
  return (
    <div>
      {/* Summary cards */}
      <div style={{ display: "flex", gap: "1rem", marginBottom: "1.5rem" }}>
        <SummaryCard label="Total Leads" value={String(data.total_leads)} />
        <SummaryCard label="Converted" value={String(data.converted_leads)} />
        <SummaryCard label="Conversion Rate" value={`${(data.conversion_rate * 100).toFixed(1)}%`} />
        <SummaryCard label="Qualified" value={String(data.qualified_leads)} />
      </div>

      {/* Funnel visualization */}
      <div style={{ marginBottom: "1.5rem" }}>
        <h3 style={{ fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.75rem" }}>Lead Funnel</h3>
        <FunnelBar label="New" count={data.new_leads} total={data.total_leads} color="var(--snad-info, #3b82f6)" />
        <FunnelBar label="Qualified" count={data.qualified_leads} total={data.total_leads} color="var(--snad-warning, #f59e0b)" />
        <FunnelBar label="Converted" count={data.converted_leads} total={data.total_leads} color="var(--snad-success, #10b981)" />
        <FunnelBar label="Disqualified" count={data.disqualified_leads} total={data.total_leads} color="var(--snad-muted, #6b7280)" />
      </div>

      {/* By source table */}
      {data.by_source.length > 0 && (
        <div className={styles.tableContainer}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Source</th>
                <th>Count</th>
                <th>Converted</th>
                <th>Rate</th>
              </tr>
            </thead>
            <tbody>
              {data.by_source.map((source) => (
                <tr key={source.source} className={styles.tableRow}>
                  <td className={styles.tableCellTitle}>{source.source || "Unknown"}</td>
                  <td>{source.count}</td>
                  <td>{source.converted}</td>
                  <td>{source.count > 0 ? `${((source.converted / source.count) * 100).toFixed(0)}%` : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/* ============================================================================
 *  Activity Throughput Report
 * ============================================================================ */

function ActivityThroughputReport({ data }: { data: CrmActivitySummaryReport }) {
  return (
    <div>
      {/* Summary cards */}
      <div style={{ display: "flex", gap: "1rem", marginBottom: "1.5rem" }}>
        <SummaryCard label="Total Activities" value={String(data.total_activities)} />
        <SummaryCard label="Open" value={String(data.open_activities)} />
        <SummaryCard label="Completed" value={String(data.completed_activities)} />
        <SummaryCard label="Tasks" value={String(data.total_tasks)} />
      </div>

      {/* Tasks breakdown */}
      <div style={{ marginBottom: "1.5rem" }}>
        <h3 style={{ fontSize: "0.875rem", fontWeight: 600, marginBottom: "0.75rem" }}>Task Status</h3>
        <FunnelBar label="Open Tasks" count={data.open_tasks} total={data.total_tasks || 1} color="var(--snad-info, #3b82f6)" />
        <FunnelBar label="Completed Tasks" count={data.completed_tasks} total={data.total_tasks || 1} color="var(--snad-success, #10b981)" />
      </div>

      {/* By type table */}
      {data.activities_by_type.length > 0 && (
        <div className={styles.tableContainer}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Activity Type</th>
                <th>Total</th>
                <th>Open</th>
                <th>Completed</th>
              </tr>
            </thead>
            <tbody>
              {data.activities_by_type.map((activity) => (
                <tr key={activity.activity_type} className={styles.tableRow}>
                  <td className={styles.tableCellTitle}>{activity.activity_type}</td>
                  <td>{activity.count}</td>
                  <td>{activity.open_count}</td>
                  <td>{activity.count - activity.open_count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/* ============================================================================
 *  Shared Components
 * ============================================================================ */

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        flex: 1,
        padding: "1rem",
        border: "1px solid var(--snad-border, #e5e7eb)",
        borderRadius: "0.5rem",
        background: "var(--snad-surface, #fff)",
      }}
    >
      <div style={{ fontSize: "0.75rem", color: "var(--snad-muted, #6b7280)", marginBottom: "0.25rem" }}>
        {label}
      </div>
      <div style={{ fontSize: "1.25rem", fontWeight: 700 }}>{value}</div>
    </div>
  );
}

function FunnelBar({ label, count, total, color }: { label: string; count: number; total: number; color: string }) {
  const pct = total > 0 ? (count / total) * 100 : 0;
  return (
    <div style={{ marginBottom: "0.5rem" }}>
      <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.75rem", marginBottom: "0.25rem" }}>
        <span>{label}</span>
        <span>{count}</span>
      </div>
      <div style={{ height: "8px", background: "var(--snad-border, #e5e7eb)", borderRadius: "4px", overflow: "hidden" }}>
        <div style={{ height: "100%", width: `${pct}%`, background: color, borderRadius: "4px" }} />
      </div>
    </div>
  );
}
