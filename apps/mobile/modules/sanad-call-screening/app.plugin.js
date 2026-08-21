/**
 * Expo Config Plugin — G8 Track E (Android native caller identification).
 *
 * CONFIG PLUGIN OWNS NATIVE CONFIG (G8 EXECUTION 05 §5):
 * the CallScreeningService manifest entry, the minimal caller-ID activity,
 * and the optional READ_CONTACTS coverage permission are all injected here —
 * never hand-edited inside a generated android/ directory. Repeated prebuilds
 * are idempotent (G8 EXECUTION 05 §52).
 *
 * Permission policy (§10–§11):
 *   - READ_PHONE_STATE / READ_CALL_LOG / CALL_PHONE / SYSTEM_ALERT_WINDOW
 *     are NEVER declared by this plugin.
 *   - READ_CONTACTS is declared but not auto-requested; the app requests it
 *     only as an OPTIONAL coverage permission (callers present in device
 *     contacts are only delivered to a CallScreeningService when the app can
 *     read contacts). Denial keeps caller ID enabled with documented coverage
 *     limitation (G8-05 §46, §58).
 */

const { withAndroidManifest } = require('@expo/config-plugins');

/** Service + activity names must match the Kotlin sources. */
const CALL_SCREENING_SERVICE = 'com.sanad.crm.callerid.SanadCallScreeningService';
const CALLER_ID_ACTIVITY = 'com.sanad.crm.callerid.SanadCallerIdActivity';

/**
 * Pure manifest mutation — exported for deterministic validation
 * (scripts/crm/g8/validate-call-screening-plugin.js) and reused by the
 * withAndroidManifest wrapper. Idempotent by construction: every entry is
 * added only when absent.
 * @param {object} manifest parsed AndroidConfig.Manifest
 * @returns {object} the same manifest object (mutated)
 */
function mutateAndroidManifest(manifest) {
  const application = manifest.manifest.application?.[0];
  if (!application) {
    throw new Error('sanad-call-screening: no <application> node in AndroidManifest');
  }

  const services = application.service ?? [];
  const screeningExists = services.some(
    (s) => s.$['android:name'] === CALL_SCREENING_SERVICE
  );
  if (!screeningExists) {
    services.push({
      $: {
        'android:name': CALL_SCREENING_SERVICE,
        'android:permission': 'android.permission.BIND_SCREENING_SERVICE',
        'android:exported': 'true',
      },
      'intent-filter': [
        {
          action: [{ $: { 'android:name': 'android.telecom.CallScreeningService' } }],
        },
      ],
    });
  }

  const activities = application.activity ?? [];
  const activityExists = activities.some(
    (a) => a.$['android:name'] === CALLER_ID_ACTIVITY
  );
  if (!activityExists) {
    activities.push({
      $: {
        'android:name': CALLER_ID_ACTIVITY,
        'android:exported': 'false',
        'android:excludeFromRecents': 'true',
        'android:noHistory': 'true',
        'android:theme': '@style/Theme.SanadCallerIdCard',
      },
    });
  }

  application.service = services;
  application.activity = activities;

  // Optional coverage permission (declared only — never auto-requested).
  const permissions = manifest.manifest['uses-permission'] ?? [];
  const contactsDeclared = permissions.some(
    (p) => p.$['android:name'] === 'android.permission.READ_CONTACTS'
  );
  if (!contactsDeclared) {
    permissions.push({
      $: { 'android:name': 'android.permission.READ_CONTACTS' },
    });
  }
  manifest.manifest['uses-permission'] = permissions;

  return manifest;
}

function withSanadCallScreening(config) {
  return withAndroidManifest(config, (config) => {
    config.modResults = mutateAndroidManifest(config.modResults);
    return config;
  });
}

module.exports = withSanadCallScreening;
module.exports.withSanadCallScreening = withSanadCallScreening;
module.exports.mutateAndroidManifest = mutateAndroidManifest;
module.exports.CALL_SCREENING_SERVICE = CALL_SCREENING_SERVICE;
module.exports.CALLER_ID_ACTIVITY = CALLER_ID_ACTIVITY;
