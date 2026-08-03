/**
 * useExecutionProgress Hook
 * -------------------------
 * Calculates progress for an execution group or program.
 */

import { useMemo } from "react";
import type { ExecutionGroup, ExecutionProgram, ExecutionProgress } from "../types";
import { calculateGroupProgress, calculateProgramProgress } from "../calculators";

/**
 * Calculate progress for an execution group.
 *
 * @param group - The execution group
 * @returns ExecutionProgress
 */
export function useGroupProgress(group: ExecutionGroup): ExecutionProgress {
  return useMemo(() => calculateGroupProgress(group), [group]);
}

/**
 * Calculate progress for an execution program.
 *
 * @param program - The execution program
 * @returns ExecutionProgress
 */
export function useProgramProgress(program: ExecutionProgram): ExecutionProgress {
  return useMemo(() => calculateProgramProgress(program), [program]);
}

/**
 * Calculate progress for multiple groups.
 *
 * @param groups - Array of execution groups
 * @returns Map of group code to progress
 */
export function useGroupProgressMap(
  groups: ExecutionGroup[]
): Map<string, ExecutionProgress> {
  return useMemo(() => {
    const map = new Map<string, ExecutionProgress>();
    for (const group of groups) {
      map.set(group.code, calculateGroupProgress(group));
    }
    return map;
  }, [groups]);
}
