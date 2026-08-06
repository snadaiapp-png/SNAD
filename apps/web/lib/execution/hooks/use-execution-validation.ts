/**
 * useExecutionValidation Hook
 * ---------------------------
 * Validates execution data integrity.
 */

import { useMemo } from "react";
import type { ExecutionGroup, ExecutionProgram, Certification } from "../types";
import type { ValidationResult } from "../validators/progress";
import { validateExecutionGroup } from "../validators/group";
import { validateExecutionProgram, getValidationSummary } from "../validators/program";

/**
 * Validate an execution group.
 *
 * @param group - The execution group
 * @param certification - Optional certification record
 * @returns Validation results
 */
export function useGroupValidation(
  group: ExecutionGroup,
  certification?: Certification
): ValidationResult[] {
  return useMemo(
    () => validateExecutionGroup(group, certification),
    [group, certification]
  );
}

/**
 * Validate an execution program.
 *
 * @param program - The execution program
 * @param certifications - Map of group code to certification
 * @returns Validation summary
 */
export function useProgramValidation(
  program: ExecutionProgram,
  certifications?: Map<string, Certification>
): {
  totalRules: number;
  passed: number;
  failed: number;
  allPassed: boolean;
  results: ValidationResult[];
} {
  return useMemo(
    () => getValidationSummary(program, certifications),
    [program, certifications]
  );
}
