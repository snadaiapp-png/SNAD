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
  // Pre-SDS components using legacy patterns (setState in effect).
  // These modules were built before the react-hooks/set-state-in-effect
  // rule was enforced. They are tracked for future migration.
  // CRM components, analytics, management, stores, websites, workflow,
  // erp, ai-platform, executive, system-health, and workspace all use
  // the same pre-SDS pattern (useEffect + setState for data loading).
  {
    files: [
      "**/app/crm/**/*.tsx",
      "**/app/analytics/**/*.tsx",
      "**/app/management/**/*.tsx",
      "**/app/stores/**/*.tsx",
      "**/app/websites/**/*.tsx",
      "**/app/workflow/**/*.tsx",
      "**/app/erp/**/*.tsx",
      "**/app/ai-platform/**/*.tsx",
      "**/app/executive/**/*.tsx",
      "**/app/system-health/**/*.tsx",
      "**/app/workspace/**/*.tsx",
    ],
    rules: {
      "react-hooks/set-state-in-effect": "off",
    },
  },
]);

export default eslintConfig;
