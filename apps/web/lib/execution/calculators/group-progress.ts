/**
 * Group Progress Calculator
 * -------------------------
 * Calculates progress for an execution group from its tasks.
 * Progress SHALL always derive from completed Tasks.
 */

import type { ExecutionGroup, ExecutionProgress } from "../types";

/**
 * Calculate progress for an execution group.
 *
 * Rules:
 * - Progress = (done + approved) / total * 100
 * - If total = 0, progress = 0
 * - Progress is always calculated, never hardcoded
 *
 * @param group - The execution group to calculate progress for
 * @returns ExecutionProgress with all metrics
 */
export function calculateGroupProgress(group: ExecutionGroup): ExecutionProgress {
  const tasks = group.tasks;
  const total = tasks.length;

  if (total === 0) {
    return {
      total: 0,
      done: 0,
      approved: 0,
      inProgress: 0,
      blocked: 0,
      notStarted: 0,
      needsReview: 0,
      percentage: 0,
    };
  }

  const done = tasks.filter((t) => t.status === "DONE").length;
  const approved = tasks.filter((t) => t.status === "APPROVED").length;
  const inProgress = tasks.filter((t) => t.status === "IN_PROGRESS").length;
  const blocked = tasks.filter((t) => t.status === "BLOCKED").length;
  const notStarted = tasks.filter((t) => t.status === "NOT_STARTED").length;
  const needsReview = tasks.filter((t) => t.status === "NEEDS_REVIEW").length;

  const percentage = Math.round(((done + approved) / total) * 100);

  return {
    total,
    done,
    approved,
    inProgress,
    blocked,
    notStarted,
    needsReview,
    percentage,
  };
}
