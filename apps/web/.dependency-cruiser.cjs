/**
 * SANAD Architecture Protection Policy — Dependency Cruiser Config
 *
 * Enforces import boundaries between bounded contexts in the frontend (apps/web).
 * Loaded by: .github/workflows/architecture-protection-gate.yml
 *
 * Reference: docs/governance/ARCHITECTURE-PROTECTION-POLICY.md
 *
 * Rules:
 *   1. Executive MUST NEVER import System Health (and vice versa)
 *   2. Executive MUST NEVER import CRM, ERP, Accounting, HRM, POS
 *   3. System Health MUST NEVER import CRM, ERP, Accounting, HRM, POS
 *   4. Same rule applies symmetrically to every bounded context
 *   5. Business logic MUST NOT live in apps/web/lib (shared utilities only)
 *   6. Layer direction: UI -> API client -> http/types only
 */

/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  extends: 'dependency-cruiser/configs/recommended-strict',

  forbidden: [
    // -------------------------------------------------------------------
    // RULE 1: Executive <-> System Health cross-import (bidirectional ban)
    // -------------------------------------------------------------------
    {
      name: 'executive-imports-system-health',
      severity: 'error',
      comment: 'Executive Management MUST NOT import System Health (Architecture Protection Policy §4.1)',
      from: { path: 'app/executive/' },
      to: { path: '(app/system-health/|lib/api/system-health-|lib/navigation/system-health-|lib/routes/system-health-|lib/modules/system-health-|lib/feature-flags/system-health-)' },
    },
    {
      name: 'system-health-imports-executive',
      severity: 'error',
      comment: 'System Health MUST NOT import Executive Management (Architecture Protection Policy §4.1)',
      from: { path: 'app/system-health/' },
      to: { path: '(app/executive/|lib/api/executive-|lib/navigation/executive-|lib/routes/executive-|lib/modules/executive-|lib/feature-flags/executive-)' },
    },

    // -------------------------------------------------------------------
    // RULE 2: Executive MUST NOT import other business modules
    // -------------------------------------------------------------------
    {
      name: 'executive-imports-crm',
      severity: 'error',
      comment: 'Executive Management MUST NOT import CRM (Architecture Protection Policy §4.1)',
      from: { path: 'app/executive/' },
      to: { path: '(app/crm/|lib/api/crm-)' },
    },
    {
      name: 'executive-imports-other-business',
      severity: 'error',
      comment: 'Executive Management MUST NOT import other business modules (Architecture Protection Policy §4.1)',
      from: { path: 'app/executive/' },
      to: { path: '(app/erp/|app/accounting/|app/hrm/|app/pos/)' },
    },

    // -------------------------------------------------------------------
    // RULE 3: System Health MUST NOT import other business modules
    // -------------------------------------------------------------------
    {
      name: 'system-health-imports-crm',
      severity: 'error',
      comment: 'System Health MUST NOT import CRM (Architecture Protection Policy §4.1)',
      from: { path: 'app/system-health/' },
      to: { path: '(app/crm/|lib/api/crm-)' },
    },
    {
      name: 'system-health-imports-other-business',
      severity: 'error',
      comment: 'System Health MUST NOT import other business modules (Architecture Protection Policy §4.1)',
      from: { path: 'app/system-health/' },
      to: { path: '(app/erp/|app/accounting/|app/hrm/|app/pos/)' },
    },

    // -------------------------------------------------------------------
    // RULE 4: Symmetric ban — CRM MUST NOT import Executive or System Health
    // -------------------------------------------------------------------
    {
      name: 'crm-imports-executive',
      severity: 'error',
      comment: 'CRM MUST NOT import Executive Management (Architecture Protection Policy §4.1)',
      from: { path: 'app/crm/' },
      to: { path: '(app/executive/|lib/api/executive-|lib/navigation/executive-|lib/routes/executive-|lib/modules/executive-)' },
    },
    {
      name: 'crm-imports-system-health',
      severity: 'error',
      comment: 'CRM MUST NOT import System Health (Architecture Protection Policy §4.1)',
      from: { path: 'app/crm/' },
      to: { path: '(app/system-health/|lib/api/system-health-|lib/navigation/system-health-|lib/routes/system-health-|lib/modules/system-health-)' },
    },

    // -------------------------------------------------------------------
    // RULE 5: No business logic in shared lib
    // -------------------------------------------------------------------
    {
      name: 'shared-lib-imports-business-module',
      severity: 'error',
      comment: 'Shared lib MUST NOT import any business module (Architecture Protection Policy §6) — Core contains no business logic',
      from: { path: 'lib/(?!api/http|api/types|i18n|design-system|feature-flags/feature-flags|routes/|navigation/|modules/)' },
      to: { path: '(app/executive/|app/system-health/|app/crm/|app/erp/|app/accounting/|app/hrm/|app/pos/)' },
    },

    // -------------------------------------------------------------------
    // RULE 6: lib/api/executive-* MUST NOT import lib/api/system-health-* (and vice versa)
    // -------------------------------------------------------------------
    {
      name: 'executive-api-imports-system-health-api',
      severity: 'error',
      comment: 'Executive API client MUST NOT import System Health API client (Architecture Protection Policy §5.2)',
      from: { path: 'lib/api/executive-' },
      to: { path: 'lib/api/system-health-' },
    },
    {
      name: 'system-health-api-imports-executive-api',
      severity: 'error',
      comment: 'System Health API client MUST NOT import Executive API client (Architecture Protection Policy §5.2)',
      from: { path: 'lib/api/system-health-' },
      to: { path: 'lib/api/executive-' },
    },

    // -------------------------------------------------------------------
    // RULE 7: No circular dependencies (inherited from recommended-strict)
    // -------------------------------------------------------------------
    {
      name: 'no-circular',
      severity: 'error',
      comment: 'Circular dependencies are forbidden (Architecture Protection Policy §3, §5)',
      from: {},
      to: { circular: true },
    },

    // -------------------------------------------------------------------
    // RULE 8: No orphan modules (inherited from recommended-strict)
    // -------------------------------------------------------------------
    {
      name: 'no-orphans',
      severity: 'warn',
      comment: 'Orphan modules — verify they are still needed (Architecture Protection Policy §3)',
      from: { orphan: true, pathNot: '\\.(spec|test)\\.(js|ts|tsx)$' },
      to: {},
    },

    // -------------------------------------------------------------------
    // RULE 9: lib/routes/<context> MUST NOT import anything except lib/types
    // -------------------------------------------------------------------
    {
      name: 'routes-must-be-pure',
      severity: 'error',
      comment: 'Route registries must be pure constants — no runtime imports (Architecture Protection Policy §5.2)',
      from: { path: 'lib/routes/' },
      to: { path: '(app/|lib/api/|lib/navigation/|lib/modules/)', pathNot: '^lib/types/' },
    },

    // -------------------------------------------------------------------
    // RULE 10: lib/feature-flags/feature-flags.ts MUST NOT import business modules
    // -------------------------------------------------------------------
    {
      name: 'feature-flags-must-not-import-business',
      severity: 'error',
      comment: 'Feature flag registry MUST NOT import business modules (Architecture Protection Policy §9)',
      from: { path: 'lib/feature-flags/' },
      to: { path: '(app/executive/|app/system-health/|app/crm/|app/erp/|app/accounting/|app/hrm/|app/pos/|lib/api/)' },
    },
  ],

  options: {
    doNotFollow: {
      path: 'node_modules',
    },
    moduleSystems: ['esm', 'cjs'],
    tsPreCompilationDeps: true,
    tsConfig: { fileName: 'tsconfig.json' },
    enhancedResolveOptions: {
      exportsFields: ['exports'],
      conditionNames: ['import', 'require', 'node', 'default'],
      extensions: ['.ts', '.tsx', '.js', '.jsx', '.mjs', '.cjs'],
    },
    reporterOptions: {
      text: {
        highlightFocused: true,
      },
      json: {
        summary: true,
        logSyntaxIssues: true,
      },
    },
    exclude: {
      path: ['^node_modules/', '^\\.next/', '^dist/', '^build/', '^out/', '^coverage/'],
    },
  },
};
