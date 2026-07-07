/*
 * ============================================================================
 *  Executive Shell — Barrel Export
 * ----------------------------------------------------------------------------
 *  Re-exports the ExecutiveShell component and its types so consumers can do:
 *
 *     import { ExecutiveShell, type ExecutiveShellProps } from '@/components/shell';
 *
 *  Future shell-level primitives (sidebar, command palette, breadcrumbs)
 *  will be added to this barrel as they land.
 * ============================================================================
 */

export {
  ExecutiveShell,
  type ExecutiveShellProps,
} from './ExecutiveShell';
