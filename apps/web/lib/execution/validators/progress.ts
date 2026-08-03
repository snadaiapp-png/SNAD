/**
 * Progress Validator
 * ------------------
 * Validates that progress is correctly calculated from tasks.
 */

import type { ExecutionGroup, ExecutionProgress } from "../types";
import { calculateGroupProgress } from "../calculators";

export interface ValidationResult {
  rule: string;
  passed: boolean;
  message: string;
}

/**
 * Validate progress integrity for a group.
 *
 * Rules:
 * - Progress must equal (done + approved) / total * 100
 * - If total = 0, progress must be 0
 * - Progress must be between 0 and 100
 *
 * @param group - The execution group to validate
 * @returns Array of validation results
 */
export function validateProgressIntegrity(group: ExecutionGroup): ValidationResult[] {
  const results: ValidationResult[] = [];
  const calculated = calculateGroupProgress(group);
  const tasks = group.tasks;
  const total = tasks.length;

  // Rule: Progress calculation matches
  const expectedDone = tasks.filter((t) => t.status === "DONE").length;
  const expectedApproved = tasks.filter((t) => t.status === "APPROVED").length;
  const expectedPercentage =
    total > 0 ? Math.round(((expectedDone + expectedApproved) / total) * 100) : 0;

  results.push({
    rule: `Progress calculation for ${group.code}`,
    passed: calculated.percentage === expectedPercentage,
    message:
      calculated.percentage === expectedPercentage
        ? `${group.code} progress correctly calculated: ${calculated.percentage}%`
        : `${group.code} progress mismatch: expected ${expectedPercentage}%, got ${calculated.percentage}%`,
  });

  // Rule: Progress is between 0 and 100
  results.push({
    rule: `Progress range for ${group.code}`,
    passed: calculated.percentage >= 0 && calculated.percentage <= 100,
    message:
      calculated.percentage >= 0 && calculated.percentage <= 100
        ? `${group.code} progress in valid range: ${calculated.percentage}%`
        : `${group.code} progress out of range: ${calculated.percentage}%`,
  });

  // Rule: If 100%, all tasks must be DONE or APPROVED
  if (calculated.percentage === 100) {
    const allComplete = tasks.every(
      (t) => t.status === "DONE" || t.status === "APPROVED"
    );
    results.push({
      rule: `100% completion for ${group.code}`,
      passed: allComplete,
      message: allComplete
        ? `${group.code} all tasks complete at 100%`
        : `${group.code} claims 100% but not all tasks are DONE/APPROVED`,
    });
  }

  return results;
}
