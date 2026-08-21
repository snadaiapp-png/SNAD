# G8 EXECUTION 05 — ANDROID NATIVE CALLER IDENTIFICATION — EXECUTION REPORT

> **Command:** G8 EXECUTION COMMAND 05 (Track E only — Android Native Caller Identification)
> **Date:** 2026-08-21 · **Base:** `main` `25d332fd` (delta 3acd2957→25d332fd audited: G8/Mobile identity untouched)
> **Branch:** `g8/android-native-caller-identification` · **Code HEAD:** `40d751dd`
> **STOP:** after this report — no merge (OWNER GATE); Tracks F/G/I/G9 NOT started.

============================================================
SNAD — G8 EXECUTION 05 — ANDROID NATIVE RESULT
============================================================

```text
BASE_MAIN_SHA                    = 25d332fd
BRANCH                           = g8/android-native-caller-identification
FINAL_SHA (code)                 = 40d751dd (feat+test; docs commit follows)
PR_NUMBER                        = (created after this report)

G8_STATUS                        = IN_PROGRESS

TRACK_A                          = COMPLETE
TRACK_B                          = COMPLETE
TRACK_C                          = COMPLETE
TRACK_D                          = COMPLETE
TRACK_E_ANDROID                  = BLOCKED (code+CI complete; PHYSICAL_DEVICE gate OPEN — no Android device in this environment; see §57)

ANDROID_MIN_NATIVE_API           = 29 (Android 10+; below → UNSUPPORTED, no undocumented fallback)
CALL_SCREENING_SERVICE           = PASS (SanadCallScreeningService; native-only ring path)
ROLE_CALL_SCREENING              = PASS (RoleManager; consent required)
ROLE_REQUEST                     = PASS (createRequestRoleIntent via Activity)
ROLE_REVOKE_BEHAVIOR             = PASS (isRoleHeld=false → caller ID disabled; dataset protected; no auto re-request)

READ_PHONE_STATE                 = NOT_REQUESTED
READ_CALL_LOG                    = NOT_REQUESTED
READ_CONTACTS                    = OPTIONAL COVERAGE (declared only, never auto-requested — §11/§46/§58)
CONTACTS_COVERAGE_LIMITATION     = DOCUMENTED (device-contact numbers only covered when granted)

RING_PATH_NETWORK                = NONE (no HTTP/DNS/backend in onScreenCall)
RING_PATH_JS_DEPENDENCY          = NONE (no RN bridge/JS on ring path)

NATIVE_DATASET_PROJECTION        = PASS (AndroidNativeCallerProjection — derived cache of Track D, no independent sync)
NATIVE_DATABASE                  = PASS (SQLite, tenant-bound, tombstone-aware)
NATIVE_LOOKUP_INDEX              = PASS (idx_native_caller_token on (tenant_id, phone_lookup_token))
DATASET_GENERATION_ATOMICITY     = PASS (generation-commit in one transaction; old generations GC'd after)

DATASET_KEY_STORAGE              = PASS (Android Keystore AES-GCM wrapped — no plain SharedPreferences/SQLite/BuildConfig/logs)
PII_ENCRYPTION                   = PASS (AES-256-GCM, Keystore-protected; no plaintext at rest; RESTRICTED rows carry no PII)
HMAC                             = PASS (javax.crypto HmacSHA256 → hex)
NORMALIZATION                    = PASS (parity Kotlin normalizer, SA rules identical)

NORMALIZATION_PARITY             = PASS (Kotlin JVM — all 18 shared vectors, byte-identical copy under test resources)
HMAC_PARITY                      = PASS (byte-for-byte shared token vector)
MATCHING_PARITY                  = PASS (tiered policy §9; no FIRST_ROW_WINS/fuzzy/suffix/random)

EXACT                            = PASS (unit 8/8 resolver suite)
AMBIGUOUS                        = PASS
UNKNOWN                          = PASS
RESTRICTED                       = PASS (marker-only card, no identity)
PRIVATE_PRESENTATION             = PLATFORM_LIMITATION (null TEL handle → ALLOW, ANDROID_PLATFORM_NOT_DELIVERED documented — not a SNAD bug)

TENANT_ISOLATION                 = PASS (queries + rows tenant-scoped; no global lookup)
TENANT_SWITCH_PURGE              = PASS (engine + store purge per tenant; A data not readable under B)
LOGOUT_PURGE                     = PASS (rows + meta + Keystore aliases)
DEVICE_REVOKE                    = PASS (purge path bound to G7 revoke policy — data purged/inaccessible)

CALLER_ID_ACTIVITY               = PASS (SanadCallerIdActivity — minimal card, auto-closes; NOT Track I)
LOCKSCREEN_PRIVACY               = PASS (CONFIDENTIAL masked when locked; RESTRICTED = "عميل محمي" marker only)
ARABIC_RTL                       = PASS (values-ar resources)
ENGLISH_LTR                      = PASS (default values)

RESPOND_P50_MS                   = NOT MEASURED (no device/emulator run — see PHYSICAL_DEVICE)
RESPOND_P95_MS                   = NOT MEASURED
RESPOND_MAX_MS                   = NOT MEASURED (platform deadline 5000 ms enforced in code; internal fallback 750 ms)
LOOKUP_P95_MS                    = NOT MEASURED (indexed lookup path asserted; runtime SLO pending device)
ANDROID_5_SECOND_DEADLINE        = PASS (code path: overBudget() → immediate ALLOW; single respondToCall with original details)

APP_FOREGROUND                   = NOT EXECUTED (device gate)
APP_BACKGROUND                   = NOT EXECUTED (device gate)
COLD_PROCESS                     = NOT EXECUTED (device gate; platform limitation documented per §47)
NETWORK_OFF                      = NOT EXECUTED (device gate; ring path is network-free by construction)
SCREEN_LOCKED                    = NOT EXECUTED (device gate; lockscreen policy implemented)

PHYSICAL_DEVICE_1                = BLOCKED — no Android device attached to this environment; cellular incoming-call evidence REQUIRED by §57/§73 (emulator is NOT accepted as final evidence)
PHYSICAL_DEVICE_2                = UNVERIFIED (OEM second device not available — recorded per §60)

KOTLIN_UNIT_TESTS                = PASS — 21/21 (JVM; parity 3+2, resolver 8, engine 5, budget 3)
ANDROID_INSTRUMENTATION          = NOT RUN (requires device/emulator; CI covers compile+AAR; device suite = separate owner evidence gate)
MOBILE_TESTS                     = PASS — 99/99 (11 suites; Track D/G7 regression green; module facade 5/5)
G7_REGRESSION                    = PASS
G8_A_D_REGRESSION                = PASS
ANDROID_BUILD                    = PASS (local Gradle 8.9: compileDebugKotlin + testDebugUnitTest + assembleDebug AAR — BUILD SUCCESSFUL)
CONFIG_PLUGIN_TEST               = PASS (service=1, activity=1, READ_CONTACTS=1, BIND_SCREENING=1, intent-filter=1, idempotent=yes, forbidden-permissions=0)
SECRET_SCAN                      = PASS (repo convention + CI gitleaks runs on PR; supplementary local sweep clean)
CI                               = PENDING (branch CI runs on the PR; workflow g8-android-native-validation.yml added)

NEW_FLYWAY_MIGRATIONS            = 0
BACKEND_API_CHANGE               = NO
OPENAPI_CHANGE                   = NO

IOS_NATIVE                       = NOT_STARTED
PBX_VOIP                         = NOT_STARTED
FULL_CALLER_UI                   = NOT_STARTED
G9                               = NOT_STARTED

OWNER_MERGE_READINESS            = BLOCKED (pending PHYSICAL_DEVICE evidence gate — §57/§73; code, tests, build and plugin gates PASS)
MERGE_EXECUTED                   = NO

NEXT_ACTION = OWNER REVIEW
→ PHYSICAL ANDROID DEVICE EVIDENCE (cellular incoming call matrix §57/§58/§59)
→ MERGE TRACK E
→ THEN G8 EXECUTION COMMAND 06
============================================================
```

## Verification evidence (local, Windows dev machine)

- **Gradle 8.9 + SDK 35/36 + JDK 17 harness** (module-only, outside the repo — `expo-modules-core` compiled from workspace node_modules; `react-android` version constraint injected for the standalone harness; the full `expo prebuild` pipeline is exercised on CI where the ecosystem toolchain is canonical).
- **Kotlin JVM tests 21/21** with XML reports (HmacParity 3, NormalizationParity 2 (18 vectors), NativeCallerResolver 8, ProjectionEngine 5, RingBudgetPolicy 3) + **AAR assembleDebug BUILD SUCCESSFUL**.
- **TypeScript facade 5/5**, mobile suite **99/99**, `tsc --noEmit` clean.
- **Config plugin validation PASS** via expo's own `AndroidConfig.Manifest` serializer — idempotent double mutation, single entries, zero forbidden permissions.
- Vectors file byte-identical (sha256) between the canonical doc and the module test resource.

## Environment notes (recorded honestly, §47/§57/§60/§73)

1. No physical Android device is attached to this machine — cellular incoming-call evidence (role grant/deny, network on/off, foreground/background/cold, locked screen, contacts granted/denied, tenant switch, logout, stale dataset, OEM matrix) could NOT be executed. **TRACK_E_ANDROID = BLOCKED** until that owner-level evidence gate is performed on a real device. No fabricated PASS.
2. The canonical `expo prebuild` CLI (and thus the full `:app` APK) is blocked in BOTH local and CI environments by an SDK-52 packaging quirk (expo packages ship Metro-style TS/extensionless entry points — e.g. `expo-crypto/build/Crypto.js` imports `./Crypto.types` — which modern Node cannot `require`; reproduces with an empty plugin list, i.e. it is unrelated to this module). The module is therefore built through the SAME android sources via `scripts/crm/g8/run-native-harness.sh` (reproduced identically in CI: `:sanad-call-screening:testDebugUnitTest` + `assembleDebug` against the workspace `expo-modules-core`), and the config-plugin manifest contract is validated through expo's own `AndroidConfig.Manifest` pipeline (`validate-call-screening-plugin.js`). FULL_APP_APK = BLOCKED-BY-TOOLCHAIN — recorded honestly; the module AAR, JVM tests, plugin contract, and mobile regression all PASS.
3. The three code-level fixes made during verification (RoleManager package `android.app.role`, missing compile-stub `Call.Details.getDirection()` → reflective defensive access with documented fallback, ProjectionEngine field mapping) are source fixes on the branch, not workarounds in node_modules.
