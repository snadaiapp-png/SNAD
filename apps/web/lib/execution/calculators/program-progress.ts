/**
 * Program Progress Calculator
 * ---------------------------
 * Calculates overall progress for an execution program.
 */

import type { ExecutionProgram, ExecutionProgress } from "../types";
import { calculateGroupProgress } from "./group-progress";

/**
 * Calculate overall progress for an execution program.
 *
 * Aggregates progress across all groups.
 *
 * @param program - The execution program to calculate progress for
 * @returns ExecutionProgress with aggregate metrics
 */
export function calculateProgramProgress(program: ExecutionProgram): ExecutionProgress {
  const allTasks = program.groups.flatMap((g) => g.tasks);
  const total = allTasks.length;

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

  const done = allTasks.filter((t) => t.status === "DONE").length;
  const approved = allTasks.filter((t) => t.status === "APPROVED").length;
  const inProgress = allTasks.filter((t) => t.status === "IN_PROGRESS").length;
  const blocked = allTasks.filter((t) => t.status === "BLOCKED").length;
  const notStarted = allTasks.filter((t) => t.status === "NOT_STARTED").length;
  const needsReview = allTasks.filter((t) => t.status === "NEEDS_REVIEW").length;

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

/**
 * Calculate progress for each group in a program.
 *
 * @param program - The execution program
 * @returns Map of group code to progress
 */
export function calculateGroupProgressMap(
  program: ExecutionProgram
): Map<string, ExecutionProgress> {
  const map = new Map<string, ExecutionProgress>();
  for (const group of program.groups) {
    map.set(group.code, calculateGroupProgress(group));
  }
  return map;
}
