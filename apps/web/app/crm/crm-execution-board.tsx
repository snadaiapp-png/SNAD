"use client";

import { useMemo, useState } from "react";
import { useCrmI18n } from "./crm-i18n";
import {
  EXECUTION_GROUPS,
  getGroupTasks,
  getGroupProgress,
  getOverallProgress,
  type ExecutionGroup,
  type GroupStatus,
} from "./crm-execution-data";
import styles from "./crm-command-center.module.css";

/**
 * Computes parallel execution waves by topological sort.
 * A group is placed in the earliest wave where all of its dependencies
 * have been completed in previous waves.
 */
function computeParallelWaves(groups: ExecutionGroup[]): string[][] {
  const waves: string[][] = [];
  const completed = new Set<string>();
  const remaining = new Set(groups.map((g) => g.code));

  while (remaining.size > 0) {
    const currentWave: string[] = [];
    for (const code of Array.from(remaining)) {
      const group = groups.find((g) => g.code === code);
      if (!group) continue;
      if (group.dependencies.every((dep) => completed.has(dep))) {
        currentWave.push(code);
      }
    }
    if (currentWave.length === 0) {
      // Cycle detection fallback — push remaining groups into a final wave.
      waves.push(Array.from(remaining));
      break;
    }
    waves.push(currentWave);
    for (const code of currentWave) {
      completed.add(code);
      remaining.delete(code);
    }
  }
  return waves;
}

function statusClass(status: GroupStatus): string {
  switch (status) {
    case "APPROVED":
      return styles.statusApproved;
    case "NEEDS_REVIEW":
      return styles.statusNeedsReview;
    case "IN_PROGRESS":
      return styles.statusInProgress;
    case "BLOCKED":
      return styles.statusBlocked;
    case "DONE":
      return styles.statusDone;
    case "REJECTED":
      return styles.statusRejected;
    case "NOT_STARTED":
    default:
      return styles.statusNotStarted;
  }
}

function codeClass(status: GroupStatus): string {
  if (status === "APPROVED" || status === "DONE") return styles.groupCodeApproved;
  if (status === "BLOCKED" || status === "REJECTED") return styles.groupCodeBlocked;
  if (status === "NOT_STARTED") return styles.groupCodeNotStarted;
  return "";
}

function ChevronIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polyline points="9 6 15 12 9 18" />
    </svg>
  );
}

interface GroupCardProps {
  group: ExecutionGroup;
  lang: "ar" | "en";
}

function GroupCard({ group, lang }: GroupCardProps) {
  const { t } = useCrmI18n();
  const [expanded, setExpanded] = useState(false);
  const progress = getGroupProgress(group.code);
  const tasks = getGroupTasks(group.code);

  const title = lang === "ar" ? group.titleAr : group.titleEn;
  const purpose = lang === "ar" ? group.purposeAr : group.purposeEn;
  const statusLabelKey = `status.${group.status}`;

  return (
    <article className={styles.groupCard}>
      <header className={styles.groupHeader}>
        <span className={`${styles.groupCode} ${codeClass(group.status)}`} aria-label={t("board.group")}>
          {group.code}
        </span>

        <div className={styles.groupInfo}>
          <h3 className={styles.groupTitle}>{title}</h3>
          <p className={styles.groupPurpose}>{purpose}</p>
        </div>

        <div className={styles.groupMeta}>
          <span className={`${styles.statusBadge} ${statusClass(group.status)}`}>
            {t(statusLabelKey)}
          </span>
          <div className={styles.groupProgress}>
            <span className={styles.groupProgressPct}>{progress.percentage}%</span>
            <div className={styles.groupProgressTrack} aria-hidden="true">
              <div
                className={styles.groupProgressFill}
                style={{ width: `${progress.percentage}%` }}
              />
            </div>
          </div>
          <button
            type="button"
            className={`${styles.groupToggle} ${expanded ? styles.groupToggleOpen : ""}`}
            onClick={() => setExpanded((v) => !v)}
            aria-expanded={expanded}
            aria-controls={`group-details-${group.code}`}
          >
            <ChevronIcon />
            <span>{expanded ? t("common.collapse") : t("common.expand")}</span>
          </button>
        </div>
      </header>

      {expanded ? (
        <div className={styles.groupDetails} id={`group-details-${group.code}`}>
          <div className={styles.groupDetailsRow}>
            <div className={styles.groupDetailItem}>
              <span className={styles.groupDetailLabel}>{t("board.dependencies")}</span>
              <span className={styles.groupDetailValue}>
                {group.dependencies.length === 0 ? (
                  <span className={`${styles.depChip} ${styles.depChipNone}`}>{t("board.noDependencies")}</span>
                ) : (
                  group.dependencies.map((dep) => (
                    <span key={dep} className={styles.depChip}>{dep}</span>
                  ))
                )}
              </span>
            </div>

            <div className={styles.groupDetailItem}>
              <span className={styles.groupDetailLabel}>{t("board.canParallelize")}</span>
              <span className={styles.groupDetailValue}>
                {group.canParallelizeWith.length === 0 ? (
                  <span className={`${styles.depChip} ${styles.depChipNone}`}>{t("board.noDependencies")}</span>
                ) : (
                  group.canParallelizeWith.map((dep) => (
                    <span key={dep} className={styles.depChip}>{dep}</span>
                  ))
                )}
              </span>
            </div>

            <div className={styles.groupDetailItem}>
              <span className={styles.groupDetailLabel}>{t("board.tasks")}</span>
              <span className={styles.groupDetailValue}>
                {progress.done}/{progress.total} {t("board.completed")} · {progress.blocked} {t("board.blocked")} · {progress.total - progress.done - progress.blocked} {t("board.remaining")}
              </span>
            </div>

            <div className={styles.groupDetailItem}>
              <span className={styles.groupDetailLabel}>{t("board.stageReport")}</span>
              <span className={styles.groupDetailValue}>
                {group.stageReport ?? t("board.noReport")}
              </span>
            </div>
          </div>

          {tasks.length > 0 ? (
            <div style={{ overflowX: "auto" }}>
              <table className={styles.taskTable}>
                <thead>
                  <tr>
                    <th scope="col">#</th>
                    <th scope="col">{t("board.tasks")}</th>
                    <th scope="col">{t("board.status")}</th>
                    <th scope="col">{t("board.purpose")}</th>
                  </tr>
                </thead>
                <tbody>
                  {tasks.map((task) => {
                    const taskName = lang === "ar" ? task.nameAr : task.nameEn;
                    const taskDesc = lang === "ar" ? task.descriptionAr : task.descriptionEn;
                    const taskStatusClass =
                      task.status === "DONE" ? styles.taskStatusDone
                      : task.status === "IN_PROGRESS" ? styles.taskStatusInProgress
                      : task.status === "BLOCKED" ? styles.taskStatusBlocked
                      : task.status === "NEEDS_REVIEW" ? styles.taskStatusNeedsReview
                      : task.status === "APPROVED" ? styles.taskStatusApproved
                      : styles.taskStatusNotStarted;
                    return (
                      <tr key={task.id}>
                        <td className={styles.taskNumber}>{task.number}</td>
                        <td>
                          <div className={styles.taskName}>{taskName}</div>
                          <div className={styles.taskDesc}>{taskDesc}</div>
                        </td>
                        <td className={taskStatusClass}>{t(`status.${task.status}`)}</td>
                        <td>
                          <div className={styles.taskCriteria}>{task.acceptanceCriteriaAr}</div>
                          {task.implementationNotesAr ? (
                            <div className={styles.taskNotes}>{task.implementationNotesAr}</div>
                          ) : null}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : (
            <p className={styles.taskDesc}>{t("empty.subtitle")}</p>
          )}
        </div>
      ) : null}
    </article>
  );
}

/**
 * CRM Execution Board
 * -------------------
 * Renders the full G0-G10 execution plan with:
 *   - Summary cards (totals, completed, blocked, overall progress)
 *   - Parallel execution plan (topological waves)
 *   - Expandable group cards with detailed task tables
 */
export function CrmExecutionBoard() {
  const { t, lang } = useCrmI18n();
  const overall = getOverallProgress();
  const waves = useMemo(() => computeParallelWaves(EXECUTION_GROUPS), []);

  return (
    <div className={styles.contentInner}>
      <div>
        <h1 className={styles.pageTitle}>{t("board.title")}</h1>
        <p className={styles.pageDescription}>{t("board.description")}</p>
      </div>

      {/* Summary cards */}
      <section aria-label={t("overview.executionSummary")}>
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
          <div className={styles.boardSummaryCard}>
            <span className={styles.boardSummaryValue}>{overall.overallPercentage}%</span>
            <span className={styles.boardSummaryLabel}>{t("overview.overallProgress")}</span>
          </div>
        </div>
      </section>

      {/* Parallel execution plan */}
      <section className={styles.parallelPlan} aria-label={t("board.parallelExecutionPlan")}>
        <h2 className={styles.overviewSectionTitle}>{t("board.parallelExecutionPlan")}</h2>
        <p className={styles.pageDescription}>{t("board.parallelPlanDesc")}</p>
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {waves.map((wave, idx) => (
            <div key={`wave-${idx}`} className={styles.parallelRow}>
              <span className={styles.parallelSeq}>
                {t("board.sequence")} {idx + 1}
              </span>
              <div className={styles.parallelGroups}>
                {wave.map((code) => (
                  <span key={code} className={styles.depChip}>{code}</span>
                ))}
              </div>
              {idx < waves.length - 1 ? (
                <span className={styles.parallelArrow} aria-hidden="true">→</span>
              ) : null}
            </div>
          ))}
        </div>
      </section>

      {/* Group cards */}
      <section style={{ display: "flex", flexDirection: "column", gap: 14 }} aria-label={t("board.title")}>
        {EXECUTION_GROUPS.map((group) => (
          <GroupCard key={group.code} group={group} lang={lang} />
        ))}
      </section>
    </div>
  );
}
