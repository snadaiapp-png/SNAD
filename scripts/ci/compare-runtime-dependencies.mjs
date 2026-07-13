#!/usr/bin/env node
/**
 * Compares runtime dependencies between base and head package.json.
 * Only fails if a NEW runtime dependency is added (not devDependency/script/metadata).
 * Usage: node scripts/ci/compare-runtime-dependencies.mjs <base.json> <head.json>
 */
import { readFileSync } from 'fs';

const [baseFile, headFile] = process.argv.slice(2);
if (!baseFile || !headFile) {
  console.error('Usage: node compare-runtime-dependencies.mjs <base.json> <head.json>');
  process.exit(2);
}

const base = JSON.parse(readFileSync(baseFile, 'utf-8'));
const head = JSON.parse(readFileSync(headFile, 'utf-8'));

const sections = ['dependencies', 'optionalDependencies', 'peerDependencies'];
let violations = [];

for (const section of sections) {
  const baseDeps = base[section] || {};
  const headDeps = head[section] || {};
  for (const dep of Object.keys(headDeps)) {
    if (!(dep in baseDeps)) {
      violations.push(`New runtime dependency added: ${dep} (${section})`);
    }
  }
}

if (violations.length > 0) {
  console.error('CRM Deployment Readiness: FAIL');
  violations.forEach(v => console.error(`  - ${v}`));
  process.exit(1);
}

console.log('CRM Deployment Readiness: PASS (no new runtime dependencies)');
process.exit(0);
