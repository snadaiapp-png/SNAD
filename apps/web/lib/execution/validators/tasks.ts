/**
 * Tasks Validator
 * ---------------
 * Validates task integrity for execution groups.
 */

import type { ExecutionGroup } from "../types";
import type { ValidationResult } from "./progress";

/**
 * Validate task integrity for a group.
 *
 * Rules:
 * - Every task must have a unique ID
 * - Every task must have acceptance criteria
 * - Every task must reference a valid group code
 * - Task dependencies must reference valid task IDs
 *
 * @param group - The execution group
 * @returns Array of validation results
 */
export function validateTaskIntegrity(group: ExecutionGroup): ValidationResult[] {
  const results: ValidationResult[] = [];
  const taskIds = new Set(group.tasks.map((t) => t.id));

  // Rule: Every task must have a unique ID
  const uniqueIds = new Set(group.tasks.map((t) => t.id));
  results.push({
    rule: `Unique task IDs in ${group.code}`,
    passed: group.tasks.length === uniqueIds.size,
    message:
      group.tasks.length === uniqueIds.size
        ? `${group.code} all task IDs are unique`
        : `${group.code} has duplicate task IDs`,
  });

  // Rule: Every task must have acceptance criteria
  const allHaveCriteria = group.tasks.every(
    (t) => t.acceptanceCriteriaAr && t.acceptanceCriteriaAr.length > 0
  );
  results.push({
    rule: `Acceptance criteria for ${group.code}`,
    passed: allHaveCriteria,
    message: allHaveCriteria
      ? `${group.code} all tasks have acceptance criteria`
      : `${group.code} some tasks missing acceptance criteria`,
  });

  // Rule: Every task must reference the correct group code
  const allCorrectGroup = group.tasks.every((t) => t.groupCode === group.code);
  results.push({
    rule: `Group code references in ${group.code}`,
    passed: allCorrectGroup,
    message: allCorrectGroup
      ? `${group.code} all tasks reference correct group`
      : `${group.code} some tasks reference wrong group`,
  });

  // Rule: Task dependencies must reference valid task IDs within the group
  const allDepsValid = group.tasks.every((t) =>
    t.dependencies.every((dep) => taskIds.has(dep) || dep.startsWith("G"))
  );
  results.push({
    rule: `Task dependency references in ${group.code}`,
    passed: allDepsValid,
    message: allDepsValid
      ? `${group.code} all task dependencies are valid`
      : `${group.code} some tasks have invalid dependencies`,
  });

  return results;
}
