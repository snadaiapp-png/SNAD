/**
 * Certification Validator
 * ----------------------
 * Validates certification integrity for execution groups.
 */

import type { ExecutionGroup, Certification } from "../types";
import type { ValidationResult } from "./progress";
import { calculateGroupProgress } from "../calculators";

/**
 * Validate certification integrity for a group.
 *
 * Rules:
 * - CERTIFIED group must have all tasks DONE/APPROVED
 * - CERTIFIED group must have progress = 100%
 * - CERTIFIED group must have all acceptance criteria passed
 * - CERTIFIED group must have at least one task
 *
 * @param group - The execution group
 * @param certification - The certification record
 * @returns Array of validation results
 */
export function validateCertificationIntegrity(
  group: ExecutionGroup,
  certification: Certification
): ValidationResult[] {
  const results: ValidationResult[] = [];
  const progress = calculateGroupProgress(group);

  // Rule: CERTIFIED group must have at least one task
  if (certification.status === "CERTIFIED") {
    results.push({
      rule: `CERTIFIED ${group.code} has tasks`,
      passed: group.tasks.length > 0,
      message:
        group.tasks.length > 0
          ? `${group.code} has ${group.tasks.length} tasks`
          : `${group.code} is CERTIFIED but has no tasks`,
    });
  }

  // Rule: CERTIFIED group must have all tasks DONE/APPROVED
  if (certification.status === "CERTIFIED") {
    const allComplete = group.tasks.every(
      (t) => t.status === "DONE" || t.status === "APPROVED"
    );
    results.push({
      rule: `CERTIFIED ${group.code} all tasks complete`,
      passed: allComplete,
      message: allComplete
        ? `${group.code} all tasks are DONE/APPROVED`
        : `${group.code} is CERTIFIED but not all tasks are complete`,
    });
  }

  // Rule: CERTIFIED group must have progress = 100%
  if (certification.status === "CERTIFIED") {
    results.push({
      rule: `CERTIFIED ${group.code} progress = 100%`,
      passed: progress.percentage === 100,
      message:
        progress.percentage === 100
          ? `${group.code} progress is 100%`
          : `${group.code} is CERTIFIED but progress is ${progress.percentage}%`,
    });
  }

  // Rule: CERTIFIED group must have all acceptance criteria passed
  if (certification.status === "CERTIFIED") {
    const allCriteriaPass = certification.acceptanceCriteria.every((c) => c.passed);
    results.push({
      rule: `CERTIFIED ${group.code} all criteria pass`,
      passed: allCriteriaPass,
      message: allCriteriaPass
        ? `${group.code} all acceptance criteria pass`
        : `${group.code} is CERTIFIED but not all criteria pass`,
    });
  }

  return results;
}
