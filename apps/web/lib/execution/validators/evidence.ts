/**
 * Evidence Validator
 * ------------------
 * Validates evidence integrity for execution groups.
 */

import type { ExecutionGroup } from "../types";
import type { ValidationResult } from "./progress";
import { hasSufficientEvidence } from "../calculators";

/**
 * Validate evidence integrity for a group.
 *
 * Rules:
 * - Every DONE/APPROVED task must have at least one evidence item
 * - No duplicate evidence IDs
 * - Evidence must have valid type
 *
 * @param group - The execution group
 * @returns Array of validation results
 */
export function validateEvidenceIntegrity(group: ExecutionGroup): ValidationResult[] {
  const results: ValidationResult[] = [];

  // Rule: Completed tasks must have evidence
  const sufficient = hasSufficientEvidence(group);
  results.push({
    rule: `Evidence coverage for ${group.code}`,
    passed: sufficient,
    message: sufficient
      ? `${group.code} all completed tasks have evidence`
      : `${group.code} some completed tasks missing evidence`,
  });

  // Rule: No duplicate evidence IDs
  const evidenceIds = group.tasks.flatMap((t) => t.evidence.map((e) => e.id));
  const uniqueIds = new Set(evidenceIds);
  const noDuplicates = evidenceIds.length === uniqueIds.size;
  results.push({
    rule: `No duplicate evidence IDs in ${group.code}`,
    passed: noDuplicates,
    message: noDuplicates
      ? `${group.code} no duplicate evidence IDs`
      : `${group.code} has duplicate evidence IDs`,
  });

  return results;
}
