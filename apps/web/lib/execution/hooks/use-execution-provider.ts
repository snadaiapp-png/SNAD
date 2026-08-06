/**
 * useExecutionProvider Hook
 * -------------------------
 * Provides access to an execution provider.
 */

import { useCallback } from "react";
import type { ExecutionProvider } from "../providers/execution-provider";

/**
 * Hook interface for using an execution provider.
 */
export interface UseExecutionProviderResult {
  /** The execution provider */
  provider: ExecutionProvider;
  /** Module identifier */
  moduleId: string;
  /** Module name */
  moduleName: string;
}

/**
 * Use an execution provider.
 *
 * @param provider - The execution provider to use
 * @returns Provider interface
 */
export function useExecutionProvider(
  provider: ExecutionProvider
): UseExecutionProviderResult {
  return {
    provider,
    moduleId: provider.moduleId,
    moduleName: provider.moduleName,
  };
}
