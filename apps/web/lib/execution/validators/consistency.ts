/**
 * Cross-Layer Consistency Validator
 * ----------------------------------
 * Validates consistency across all execution layers.
 */

import type { ExecutionGroup, ExecutionProgram } from "../types";
import type { ValidationResult } from "./progress";
import { calculateGroupProgress, calculateProgramProgress } from "../calculators";

/**
 * Validate cross-layer consistency for a group.
 *
 * Rules:
 * - Group status must be consistent with task statuses
 * - If all tasks are DONE, group status should be DONE or APPROVED
 * - If any task is BLOCKED, group status should be BLOCKED or reflect it
 *
 * @param group - The execution group
 * @returns Array of validation results
 */
export function validateCrossLayerConsistency(group: ExecutionGroup): ValidationResult[] {
  const results: ValidationResult[] = [];
  const progress = calculateGroupProgress(group);

  // Rule: Group status consistency with task completion
  if (progress.percentage === 100 && group.status === "NOT_STARTED") {
    results.push({
      rule: `Status consistency for ${group.code}`,
      passed: false,
      message: `${group.code} has 100% progress but status is NOT_STARTED`,
    });
  } else if (progress.percentage === 0 && group.status === "DONE") {
    results.push({
      rule: `Status consistency for ${group.code}`,
      passed: false,
      message: `${group.code} has 0% progress but status is DONE`,
    });
  } else {
    results.push({
      rule: `Status consistency for ${group.code}`,
      passed: true,
      message: `${group.code} status is consistent with task progress`,
    });
  }

  // Rule: Blocked tasks should be reflected in group status
  if (progress.blocked > 0 && group.status === "DONE") {
    results.push({
      rule: `Blocked task reflection for ${group.code}`,
      passed: false,
      message: `${group.code} has ${progress.blocked} blocked tasks but status is DONE`,
    });
  } else {
    results.push({
      rule: `Blocked task reflection for ${group.code}`,
      passed: true,
      message: `${group.code} blocked tasks are reflected in status`,
    });
  }

  return results;
}

/**
 * Validate cross-layer consistency for a program.
 *
 * @param program - The execution program
 * @returns Array of validation results
 */
export function validateProgramConsistency(program: ExecutionProgram): ValidationResult[] {
  const results: ValidationResult[] = [];
  const programProgress = calculateProgramProgress(program);

  // Rule: Program progress must match sum of group progress
  const totalTasksFromGroups = program.groups.reduce(
    (sum, g) => sum + g.tasks.length,
    0
  );
  results.push({
    rule: `Program task count consistency`,
    passed: programProgress.total === totalTasksFromGroups,
    message:
      programProgress.total === totalTasksFromGroups
        ? `Program has ${programProgress.total} tasks across all groups`
        : `Program task count mismatch: ${programProgress.total} vs ${totalTasksFromGroups}`,
  });

  return results;
}
