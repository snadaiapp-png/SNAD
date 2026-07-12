"use client";

import { useCrmI18n } from "./crm-i18n";
import { getOverallProgress } from "./crm-execution-data";
import styles from "./crm-command-center.module.css";

/**
 * CRM Overview Page
 * -----------------
 * Shows KPI placeholders (value "—" because no live CRM data is connected yet)
 * and a project execution summary derived from the static execution registry.
 *
 * The execution summary pulls real progress numbers from crm-execution-data
 * so leadership can see at-a-glance how the CRM build is tracking.
 */
export function CrmOverview() {
  const { t } = useCrmI18n();
  const overall = getOverallProgress();

  // KPI cards — placeholders only, no mock numbers.
  // Value is "—" until live CRM data is connected in G3-G5.
  const kpis = [
    { key: "overview.kpi.leads", value: t("common.na"), hint: t("overview.underConstruction") },
    { key: "overview.kpi.customers", value: t("common.na"), hint: t("overview.underConstruction") },
    { key: "overview.kpi.opportunities", value: t("common.na"), hint: t("overview.underConstruction") },
    { key: "overview.kpi.pipelineValue", value: t("common.na"), hint: t("overview.underConstruction") },
  ];

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("overview.welcome")}</h1>
        <p className={styles.pageDescription}>{t("overview.description")}</p>
      </div>

      {/* KPI placeholders — value "—" because CRM data is not yet wired */}
      <section aria-label={t("overview.kpi.leads")}>
        <div className={styles.kpiGrid}>
          {kpis.map((kpi) => (
            <article key={kpi.key} className={styles.kpiCard}>
              <span className={styles.kpiLabel}>{t(kpi.key)}</span>
              <span className={styles.kpiValue}>{kpi.value}</span>
              <span className={styles.kpiHint}>
                <span className={styles.kpiHintDot} aria-hidden="true" />
                {kpi.hint}
              </span>
            </article>
          ))}
        </div>
      </section>

      {/* Execution summary — real numbers from the static execution registry */}
      <section className={styles.overviewSection} aria-label={t("overview.executionSummary")}>
        <h2 className={styles.overviewSectionTitle}>{t("overview.executionSummary")}</h2>

        <div className={styles.boardSummary}>
          <div className={styles.boardSummaryCard}>
            <span className={styles.boardSummaryValue}>{overall.totalGroups}</span>
            <span className={styles.boardSummaryLabel}>{t("overview.totalGroups")}</span>
          </div>
          <div className={styles.boardSummaryCard}>
            <span className={styles.boardSummaryValue}>{overall.totalTasks}</span>
            <span className={styles.boardSummaryLabel}>{t("overview.totalTasks")}</span>
          </div>
          <div className={styles.boardSummaryCard}>
            <span className={styles.boardSummaryValue}>{overall.completedTasks}</span>
            <span className={styles.boardSummaryLabel}>{t("overview.completedTasks")}</span>
          </div>
          <div className={styles.boardSummaryCard}>
            <span className={styles.boardSummaryValue}>{overall.blockedTasks}</span>
            <span className={styles.boardSummaryLabel}>{t("overview.blockedTasks")}</span>
          </div>
        </div>

        <div className={styles.overviewStats}>
          <div className={styles.overviewStat}>
            <span className={styles.overviewStatLabel}>{t("overview.overallProgress")}</span>
            <span className={styles.overviewStatValue}>{overall.overallPercentage}%</span>
            <div className={styles.progressTrack} aria-hidden="true">
              <div
                className={styles.progressFill}
                style={{ width: `${overall.overallPercentage}%` }}
              />
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
