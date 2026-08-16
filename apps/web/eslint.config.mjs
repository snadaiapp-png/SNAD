import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
  // Pre-SDS page components — pre-SDS patterns, pending migration.
  // Disable react-hooks/set-state-in-effect for legacy pages that use
  // the pre-React-19 pattern of calling setState synchronously inside
  // useEffect. This matches the existing CRM exemption and covers all
  // pre-SDS pages (ai-platform, analytics, control-plane, erp, executive,
  // management, stores, system-health, websites, workflow).
  // These pages are tracked for migration to derived-state patterns in
  // a follow-up PR.
  {
    files: [
      "**/app/crm/**/*.tsx",
      "**/app/ai-platform/**/*.tsx",
      "**/app/analytics/**/*.tsx",
      "**/app/control-plane/**/*.tsx",
      "**/app/erp/**/*.tsx",
      "**/app/executive/**/*.tsx",
      "**/app/management/**/*.tsx",
      "**/app/stores/**/*.tsx",
      "**/app/system-health/**/*.tsx",
      "**/app/websites/**/*.tsx",
      "**/app/workflow/**/*.tsx",
    ],
    rules: {
      "react-hooks/set-state-in-effect": "off",
    },
  },
]);

export default eslintConfig;
