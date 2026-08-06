"use client";

import { useEffect, useState, useMemo } from "react";
import type {
  ExecutionProgram,
  ExecutionGroup,
  ExecutionTask,
  ExecutionProgress,
  Certification,
  GroupStatus,
} from "@/lib/execution";
import {
  calculateGroupProgress,
  calculateProgramProgress,
  validateExecutionProgram,
  buildDependencyGraph,
  topologicalSort,
  GROUP_STATUS_LABELS_AR,
  GROUP_STATUS_LABELS_EN,
  STATUS_COLORS,
} from "@/lib/execution";
import styles from "./control-plane.module.css";

// ── Module Providers Registry ────────────────────────────────────────────

interface ModuleProvider {
  moduleId: string;
  moduleName: string;
  getPrograms: () => Promise<ExecutionProgram[]>;
}

// Registry of all module providers
const moduleProviders: ModuleProvider[] = [];

/**
 * Register a module provider for the dashboard.
 * All modules MUST register here to appear in the dashboard.
 */
export function registerModuleProvider(provider: ModuleProvider) {
  moduleProviders.push(provider);
}

// ── Dashboard Component ──────────────────────────────────────────────────

interface DashboardState {
  programs: ExecutionProgram[];
  certifications: Map<string, Certification>;
  loading: boolean;
  error: string | null;
}

/**
 * Platform Execution Dashboard
 * ----------------------------
 * Unified dashboard displaying execution status across all modules.
 * Every module MUST appear in this dashboard.
 */
export function ExecutionDashboard() {
  const [state, setState] = useState<DashboardState>({
    programs: [],
    certifications: new Map(),
    loading: true,
    error: null,
  });

  // Load all programs from registered providers
  useEffect(() => {
    async function loadPrograms() {
      try {
        const allPrograms: ExecutionProgram[] = [];
        for (const provider of moduleProviders) {
          const programs = await provider.getPrograms();
          allPrograms.push(...programs);
        }
        setState({
          programs: allPrograms,
          certifications: new Map(),
          loading: false,
          error: null,
        });
      } catch (err) {
        setState((prev) => ({
          ...prev,
          loading: false,
          error: err instanceof Error ? err.message : "Failed to load programs",
        }));
      }
    }

    loadPrograms();
  }, []);

  if (state.loading) {
    return <div className={styles.loading}>Loading execution data...</div>;
  }

  if (state.error) {
    return <div className={styles.error}>Error: {state.error}</div>;
  }

  return (
    <div className={styles.contentInner}>
      <header>
        <h1 className={styles.pageTitle}>Platform Execution Dashboard</h1>
        <p className={styles.pageDescription}>
          Unified view of execution status across all SANAD modules
        </p>
      </header>

      {/* Summary Cards */}
      <ProgramSummary programs={state.programs} />

      {/* Module Programs */}
      <section aria-label="Module Programs">
        <h2 className={styles.overviewSectionTitle}>Module Programs</h2>
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          {state.programs.map((program) => (
            <ProgramCard
              key={program.id}
              program={program}
              certifications={state.certifications}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

// ── Summary Component ────────────────────────────────────────────────────

function ProgramSummary({ programs }: { programs: ExecutionProgram[] }) {
  const summary = useMemo(() => {
    const totalPrograms = programs.length;
    const totalGroups = programs.reduce((sum, p) => sum + p.groups.length, 0);
    const totalTasks = programs.reduce(
      (sum, p) => sum + p.groups.reduce((sum, g) => sum + g.tasks.length, 0),
      0
    );
    const completedTasks = programs.reduce(
      (sum, p) =>
        sum +
        p.groups.reduce(
          (sum, g) =>
            sum +
            g.tasks.filter((t) => t.status === "DONE" || t.status === "APPROVED").length,
          0
        ),
      0
    );
    const overallPercentage =
      totalTasks > 0 ? Math.round((completedTasks / totalTasks) * 100) : 0;

    return {
      totalPrograms,
      totalGroups,
      totalTasks,
      completedTasks,
      overallPercentage,
    };
  }, [programs]);

  return (
    <section aria-label="Execution Summary">
      <div className={styles.boardSummary}>
        <div className={styles.boardSummaryCard}>
          <span className={styles.boardSummaryValue}>{summary.totalPrograms}</span>
          <span className={styles.boardSummaryLabel}>Programs</span>
        </div>
        <div className={styles.boardSummaryCard}>
          <span className={styles.boardSummaryValue}>{summary.totalGroups}</span>
          <span className={styles.boardSummaryLabel}>Groups</span>
        </div>
        <div className={styles.boardSummaryCard}>
          <span className={styles.boardSummaryValue}>{summary.totalTasks}</span>
          <span className={styles.boardSummaryLabel}>Tasks</span>
        </div>
        <div className={styles.boardSummaryCard}>
          <span className={styles.boardSummaryValue}>{summary.completedTasks}</span>
          <span className={styles.boardSummaryLabel}>Completed</span>
        </div>
        <div className={styles.boardSummaryCard}>
          <span className={styles.boardSummaryValue}>{summary.overallPercentage}%</span>
          <span className={styles.boardSummaryLabel}>Overall Progress</span>
        </div>
      </div>
    </section>
  );
}

// ── Program Card Component ───────────────────────────────────────────────

function ProgramCard({
  program,
  certifications,
}: {
  program: ExecutionProgram;
  certifications: Map<string, Certification>;
}) {
  const progress = useMemo(() => calculateProgramProgress(program), [program]);
  const validation = useMemo(
    () => validateExecutionProgram(program, certifications),
    [program, certifications]
  );
  const isValid = validation.every((r) => r.passed);

  return (
    <article className={styles.groupCard}>
      <header className={styles.groupHeader}>
        <span
          className={`${styles.groupCode} ${isValid ? styles.groupCodeApproved : styles.groupCodeBlocked}`}
        >
          {program.code}
        </span>

        <div className={styles.groupInfo}>
          <h3 className={styles.groupTitle}>{program.titleEn}</h3>
          <p className={styles.groupPurpose}>{program.descriptionEn}</p>
        </div>

        <div className={styles.groupMeta}>
          <span
            className={`${styles.statusBadge} ${
              isValid ? styles.statusApproved : styles.statusBlocked
            }`}
          >
            {isValid ? "VALID" : "INVALID"}
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
        </div>
      </header>

      <div className={styles.groupDetails}>
        <div className={styles.groupDetailsRow}>
          <div className={styles.groupDetailItem}>
            <span className={styles.groupDetailLabel}>Groups</span>
            <span className={styles.groupDetailValue}>{program.groups.length}</span>
          </div>
          <div className={styles.groupDetailItem}>
            <span className={styles.groupDetailLabel}>Tasks</span>
            <span className={styles.groupDetailValue}>{progress.total}</span>
          </div>
          <div className={styles.groupDetailItem}>
            <span className={styles.groupDetailLabel}>Completed</span>
            <span className={styles.groupDetailValue}>
              {progress.done + progress.approved}
            </span>
          </div>
          <div className={styles.groupDetailItem}>
            <span className={styles.groupDetailLabel}>Validation</span>
            <span className={styles.groupDetailValue}>
              {validation.filter((r) => r.passed).length}/{validation.length} passed
            </span>
          </div>
        </div>

        {/* Groups List */}
        <div style={{ marginTop: 12 }}>
          <h4 className={styles.groupDetailLabel}>Groups</h4>
          <div style={{ display: "flex", flexDirection: "column", gap: 8, marginTop: 8 }}>
            {program.groups.map((group) => (
              <GroupRow
                key={group.code}
                group={group}
                certifications={certifications}
              />
            ))}
          </div>
        </div>
      </div>
    </article>
  );
}

// ── Group Row Component ──────────────────────────────────────────────────

function GroupRow({
  group,
  certifications,
}: {
  group: ExecutionGroup;
  certifications: Map<string, Certification>;
}) {
  const progress = useMemo(() => calculateGroupProgress(group), [group]);
  const certification = certifications.get(group.code);

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 12,
        padding: "8px 12px",
        background: "rgba(255,255,255,0.05)",
        borderRadius: 6,
      }}
    >
      <span
        className={styles.groupCode}
        style={{ background: STATUS_COLORS[group.status], minWidth: 40 }}
      >
        {group.code}
      </span>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 14, fontWeight: 500 }}>{group.titleEn}</div>
        <div style={{ fontSize: 12, opacity: 0.7 }}>
          {progress.done}/{progress.total} tasks · {progress.percentage}%
        </div>
      </div>
      <span
        className={styles.statusBadge}
        style={{ background: STATUS_COLORS[group.status] }}
      >
        {GROUP_STATUS_LABELS_EN[group.status]}
      </span>
      {certification && (
        <span
          className={styles.statusBadge}
          style={{
            background:
              certification.status === "CERTIFIED"
                ? "#22c55e"
                : certification.status === "REJECTED"
                ? "#ef4444"
                : "#f59e0b",
          }}
        >
          {certification.status}
        </span>
      )}
    </div>
  );
}
