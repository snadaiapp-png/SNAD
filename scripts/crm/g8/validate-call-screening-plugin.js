#!/usr/bin/env node
/**
 * G8 EXECUTION 05 §52 — Config Plugin validation.
 *
 * Runs the sanad-call-screening plugin's REAL manifest mutation through
 * expo's own `@expo/config-plugins` AndroidConfig.Manifest serializer and
 * asserts — idempotently, across two consecutive mutations:
 *   1. CallScreeningService manifest entry (permission + intent-filter)
 *   2. SanadCallerIdActivity entry
 *   3. READ_CONTACTS declared exactly once
 *   4. NO duplicate entries after the second mutation (repeatable generation)
 *   5. Forbidden permissions (READ_PHONE_STATE / READ_CALL_LOG / CALL_PHONE /
 *      SYSTEM_ALERT_WINDOW) are never declared AND are actively stripped when
 *      present in the input (the Expo bare-minimum template ships
 *      SYSTEM_ALERT_WINDOW by default — G8-05-R §13)
 *
 * Usage (from repo root, node 18+):
 *   node scripts/crm/g8/validate-call-screening-plugin.js
 * Exit 0 = PASS, 1 = FAIL.
 */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { createRequire } = require('module');

const mobileRequire = createRequire(
  path.join(__dirname, '..', '..', '..', 'apps', 'mobile', 'package.json')
);
const { AndroidConfig } = mobileRequire('@expo/config-plugins');

const PLUGIN_PATH = path.join(
  __dirname,
  '..',
  '..',
  '..',
  'apps',
  'mobile',
  'modules',
  'sanad-call-screening',
  'app.plugin.js'
);
const plugin = require(PLUGIN_PATH);
const { mutateAndroidManifest, CALL_SCREENING_SERVICE, CALLER_ID_ACTIVITY } = plugin;

const FORBIDDEN_PERMISSIONS = [
  'android.permission.READ_PHONE_STATE',
  'android.permission.READ_CALL_LOG',
  'android.permission.CALL_PHONE',
  'android.permission.SYSTEM_ALERT_WINDOW',
];

const FIXTURE_MANIFEST = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.sanad.crm">
  <uses-permission android:name="android.permission.INTERNET" />
  <application android:label="Sanad CRM">
  </application>
</manifest>
`;

/** Mirrors the Expo bare-minimum template baseline (getAndroidManifestTemplate
 *  in @expo/config-plugins) which ships SYSTEM_ALERT_WINDOW + friends as
 *  "optional permissions" — the plugin must strip all forbidden ones. */
const TEMPLATE_LIKE_MANIFEST = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.sanad.crm">
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
  <uses-permission android:name="android.permission.VIBRATE" />
  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
  <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
  <application android:label="Sanad CRM">
  </application>
</manifest>
`;

function countOccurrences(text, needle) {
  return text.split(needle).length - 1;
}

async function main() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sanad-plugin-'));
  const manifestPath = path.join(dir, 'AndroidManifest.xml');
  fs.writeFileSync(manifestPath, FIXTURE_MANIFEST, 'utf8');
  try {
    // first mutation — entries must exist exactly once
    let manifest = await AndroidConfig.Manifest.readAndroidManifestAsync(manifestPath);
    manifest = mutateAndroidManifest(manifest);
    await AndroidConfig.Manifest.writeAndroidManifestAsync(manifestPath, manifest);
    const xml = fs.readFileSync(manifestPath, 'utf8');

    const serviceCount = countOccurrences(xml, `android:name="${CALL_SCREENING_SERVICE}"`);
    const activityCount = countOccurrences(xml, `android:name="${CALLER_ID_ACTIVITY}"`);
    const contactsCount = countOccurrences(xml, 'android.permission.READ_CONTACTS');
    const bindCount = countOccurrences(xml, 'android.permission.BIND_SCREENING_SERVICE');
    const intentCount = countOccurrences(xml, 'android.telecom.CallScreeningService');

    const problems = [];
    if (serviceCount !== 1) problems.push(`service entries=${serviceCount} (expected 1)`);
    if (bindCount !== 1) problems.push(`BIND_SCREENING_SERVICE occurrences=${bindCount} (expected 1)`);
    if (intentCount !== 1) problems.push(`CallScreeningService intent-filter occurrences=${intentCount} (expected 1)`);
    if (activityCount !== 1) problems.push(`activity entries=${activityCount} (expected 1)`);
    if (contactsCount !== 1) problems.push(`READ_CONTACTS occurrences=${contactsCount} (expected 1)`);
    for (const forbidden of FORBIDDEN_PERMISSIONS) {
      if (xml.includes(forbidden)) problems.push(`FORBIDDEN permission present: ${forbidden}`);
    }

    // second mutation — idempotency / repeatable generation
    let manifest2 = await AndroidConfig.Manifest.readAndroidManifestAsync(manifestPath);
    manifest2 = mutateAndroidManifest(manifest2);
    await AndroidConfig.Manifest.writeAndroidManifestAsync(manifestPath, manifest2);
    const xml2 = fs.readFileSync(manifestPath, 'utf8');
    if (countOccurrences(xml2, `android:name="${CALL_SCREENING_SERVICE}"`) !== 1) {
      problems.push('service duplicated after second mutation (not idempotent)');
    }
    if (countOccurrences(xml2, `android:name="${CALLER_ID_ACTIVITY}"`) !== 1) {
      problems.push('activity duplicated after second mutation (not idempotent)');
    }
    if (countOccurrences(xml2, 'android.permission.READ_CONTACTS') !== 1) {
      problems.push('READ_CONTACTS duplicated after second mutation (not idempotent)');
    }

    // third mutation — Expo-template-like input: forbidden permissions that
    // ARE present in the input must be stripped (G8-05-R §13)
    const templatePath = path.join(dir, 'TemplateManifest.xml');
    fs.writeFileSync(templatePath, TEMPLATE_LIKE_MANIFEST, 'utf8');
    let manifest3 = await AndroidConfig.Manifest.readAndroidManifestAsync(templatePath);
    manifest3 = mutateAndroidManifest(manifest3);
    await AndroidConfig.Manifest.writeAndroidManifestAsync(templatePath, manifest3);
    const xml3 = fs.readFileSync(templatePath, 'utf8');
    for (const forbidden of FORBIDDEN_PERMISSIONS) {
      if (xml3.includes(forbidden)) {
        problems.push(`FORBIDDEN permission NOT stripped from template-like input: ${forbidden}`);
      }
    }
    if (!xml3.includes('android.permission.VIBRATE')) {
      problems.push('VIBRATE unexpectedly removed (only FORBIDDEN list may be stripped)');
    }
    if (!xml3.includes('android.permission.READ_EXTERNAL_STORAGE')) {
      problems.push('READ_EXTERNAL_STORAGE unexpectedly removed (only FORBIDDEN list may be stripped)');
    }

    if (problems.length > 0) {
      console.error('CONFIG_PLUGIN_VALIDATION: FAIL');
      for (const p of problems) console.error('  - ' + p);
      process.exit(1);
    }
    console.log(
      `CONFIG_PLUGIN_VALIDATION: PASS (service=${serviceCount}, activity=${activityCount}, ` +
        `READ_CONTACTS=${contactsCount}, BIND_SCREENING=${bindCount}, intent-filter=${intentCount}, ` +
        `idempotent=yes, forbidden-permissions=0, template-strip=yes)`
    );
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

main().catch((err) => {
  console.error('CONFIG_PLUGIN_VALIDATION: ERROR ' + (err && err.stack ? err.stack : err));
  process.exit(1);
});
