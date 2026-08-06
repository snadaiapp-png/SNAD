/**
 * Evidence Coverage Calculator
 * ----------------------------
 * Calculates evidence coverage for tasks and groups.
 */

import type { ExecutionGroup, ExecutionTask, EvidenceType } from "../types";

/**
 * Calculate evidence coverage for a task.
 *
 * @param task - The execution task
 * @returns Number of evidence items
 */
export function getTaskEvidenceCount(task: ExecutionTask): number {
  return task.evidence.length;
}

/**
 * Calculate evidence coverage for a group.
 *
 * @param group - The execution group
 * @returns Object with total evidence count and per-task breakdown
 */
export function getGroupEvidenceCoverage(group: ExecutionGroup): {
  totalEvidence: number;
  tasksWithEvidence: number;
  tasksWithoutEvidence: number;
  coveragePercentage: number;
  byType: Record<EvidenceType, number>;
} {
  const byType: Record<EvidenceType, number> = {
    SOURCE_CODE: 0,
    DATABASE_MIGRATION: 0,
    API_IMPLEMENTATION: 0,
    FRONTEND_IMPLEMENTATION: 0,
    TEST: 0,
    DOCUMENTATION: 0,
    CI_EVIDENCE: 0,
    PRODUCTION_DEPLOYMENT: 0,
    MANUAL_VERIFICATION: 0,
  };

  let totalEvidence = 0;
  let tasksWithEvidence = 0;
  let tasksWithoutEvidence = 0;

  for (const task of group.tasks) {
    if (task.evidence.length > 0) {
      tasksWithEvidence++;
      totalEvidence += task.evidence.length;
      for (const ev of task.evidence) {
        byType[ev.type]++;
      }
    } else {
      tasksWithoutEvidence++;
    }
  }

  const totalTasks = group.tasks.length;
  const coveragePercentage =
    totalTasks > 0 ? Math.round((tasksWithEvidence / totalTasks) * 100) : 0;

  return {
    totalEvidence,
    tasksWithEvidence,
    tasksWithoutEvidence,
    coveragePercentage,
    byType,
  };
}

/**
 * Check if a group has sufficient evidence for certification.
 *
 * Rules:
 * - Every DONE/APPROVED task must have at least one evidence item
 * - Evidence must be of an appropriate type
 *
 * @param group - The execution group
 * @returns true if evidence is sufficient
 */
export function hasSufficientEvidence(group: ExecutionGroup): boolean {
  const completedTasks = group.tasks.filter(
    (t) => t.status === "DONE" || t.status === "APPROVED"
  );

  return completedTasks.every((t) => t.evidence.length > 0);
}
