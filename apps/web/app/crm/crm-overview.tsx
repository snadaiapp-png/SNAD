"use client";

import { useMemo } from "react";
import { useCrmI18n } from "./crm-i18n";
import { calculateProgramProgress, type ExecutionGroup, type ExecutionTask } from "../../lib/execution";
import { CRM_GROUP_DATA, CRM_TASKS } from "./crm-execution-data";
import styles from "./crm-shared-styles.module.css";

/**
 * CRM Overview Page
 * -----------------
 * Shows KPI placeholders (value "—" because no live CRM data is connected yet)
 * and a project execution summary derived from the static execution registry.
 *
 * The execution summary pulls real progress numbers from the shared framework
 * so leadership can see at-a-glance how the CRM build is tracking.
 */
export function CrmOverview() {
  const { t } = useCrmI18n();

  // Build program from business data
  const program = useMemo(() => {
    const groups: ExecutionGroup[] = CRM_GROUP_DATA.map((groupData) => ({
      id: `GROUP-${groupData.code}`,
      code: groupData.code,
      titleAr: groupData.titleAr,
      titleEn: groupData.titleEn,
      purposeAr: groupData.purposeAr,
      purposeEn: groupData.purposeEn,
      status: groupData.status,
      dependencies: groupData.dependencies,
      canParallelizeWith: groupData.canParallelizeWith,
      stageReport: groupData.stageReport,
      milestones: [],
      tasks: CRM_TASKS
        .filter((t) => t.groupCode === groupData.code)
        .map((task): ExecutionTask => ({
          id: task.id,
          number: task.number,
          nameAr: task.nameAr,
          nameEn: task.nameEn,
          groupCode: task.groupCode,
          descriptionAr: task.descriptionAr,
          descriptionEn: task.descriptionEn,
          type: task.type,
          priority: task.priority,
          status: task.status,
          dependencies: task.dependencies,
          acceptanceCriteriaAr: task.acceptanceCriteriaAr,
          implementationNotesAr: task.implementationNotesAr,
          evidence: [],
        })),
    }));

    return {
      id: "CRM-PROGRAM",
      code: "CRM",
      titleAr: "نظام إدارة علاقات العملاء",
      titleEn: "Customer Relationship Management",
      descriptionAr: "نظام CRM شامل لإدارة العملاء والفرص البيعية والتقارير",
      descriptionEn: "Comprehensive CRM system for managing customers, opportunities, and reports",
      status: "IN_PROGRESS" as const,
      groups,
    };
  }, []);

  // Calculate progress using the shared framework
  const overall = useMemo(() => {
    const progress = calculateProgramProgress(program);
    const totalGroups = program.groups.length;
    const completedTasks = progress.done + progress.approved;
    const blockedTasks = progress.blocked;
    const inProgressGroups = program.groups.filter((g) => g.status === "IN_PROGRESS").length;

    return {
      totalGroups,
      totalTasks: progress.total,
      completedTasks,
      blockedTasks,
      inProgressGroups,
      overallPercentage: progress.percentage,
    };
  }, [program]);

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

      {/* Execution summary — real numbers from the shared framework */}
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
