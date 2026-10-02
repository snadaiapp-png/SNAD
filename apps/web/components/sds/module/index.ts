/*
 * ============================================================================
 *  SNAD Shared Module Visual Contract — barrel export
 * ----------------------------------------------------------------------------
 *  The canonical module shell + surface primitives extracted from the CRM
 *  reference architecture. Every SNAD module consumes these instead of
 *  building an independent visual identity.
 *
 *  Governed by docs/superpowers/specs/2026-10-02-snad-module-visual-contract.md
 * ============================================================================
 */

export {
  SnadModuleShell,
  type SnadModuleShellProps,
  type SnadModuleNavItem,
  type SnadModuleNavSection,
} from "./SnadModuleShell";

export {
  SnadPageHeader,
  SnadKpiGrid,
  SnadKpiCard,
  SnadOperationalPanel,
  SnadActionBar,
  SnadStatusBadge,
  SnadLoadingState,
  SnadEmptyState,
  SnadErrorState,
  SnadForbiddenState,
  SnadSuccessNotice,
  SnadMobileRecordList,
  SnadMobileRecordCard,
  type SnadBadgeTone,
} from "./SnadSurfaces";

export * from "./icons";
