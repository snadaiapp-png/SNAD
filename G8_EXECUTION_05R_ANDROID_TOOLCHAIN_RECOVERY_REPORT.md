# G8 EXECUTION 05-R — Android Toolchain Recovery Report

**Date:** 2026-08-21
**Command:** G8 EXECUTION COMMAND 05-R (P0 Mobile Toolchain Alignment + Installable Android APK)
**Branch:** `g8/android-native-caller-identification` · **PR:** #893 (OPEN — no merge, §2/§28)
**Scope:** Corrective only — no new feature. Closes P0-1 (toolchain mismatch), P0-2 (full APK not buildable), P0-3 (branch behind main governance).

---

## 1 — Baseline forensics (§3–§4)

```text
NODE (system)          = v24.18.1   npm 11.16.0
WORKING NODE (adopted) = v20.20.2   npm 10.8.2   (portable, outside repo)
```

Pre-upgrade repository matrix (`expo install --check` baseline):

```text
expo             ~52.0.0  (52.0.49)
react            19.2.8
react-native     0.79.4
expo-modules-core 2.2.3
expo-crypto      14.0.2
expo-secure-store 14.0.1
expo-sqlite      15.0.6
jest-expo        52.0.6
typescript       ~5.8.0
```

`expo install --check` (SDK 52 expected values):

```text
react            19.2.8  → expected 18.3.1     ❌
react-native     0.79.4  → expected 0.76.9     ❌
@types/react     19.2.18 → expected ~18.3.12   ❌
expo-sqlite      15.0.6  → expected ~15.1.4    (drift)
```

**Classification (§4): `MOBILE_SDK_MATRIX = MISALIGNED` — proven.** The repo paired
SDK-52-era Expo with SDK-53-era RN/React. (The prior session's assumption of the
same classification is confirmed by CLI evidence, not assumption.)

## 2 — Root cause of the prebuild impossibility (§10 — prior diagnosis REVISED)

The prior 05 report attributed the prebuild failure to an inherent
"SDK-52 Metro-style package" ecosystem quirk. That diagnosis was **over-broad**.
Precise root cause chain, isolated by plugin-bisect under Node 20:

1. `app.json` listed `"expo-crypto"` in `plugins` — but **expo-crypto ships NO
   config plugin** (no `app.plugin.js`, no `expo` field; it is autolinked via
   `expo-module.config.json`). The entry was wrong from the day it was added.
2. With no plugin to resolve, Expo CLI fell back to loading the package entry
   (`build/Crypto.js`), which ESM-imports `expo-modules-core`.
3. `expo-modules-core`'s package `main` is `src/index.ts`. Plain Node cannot
   load a `.ts` entry from node_modules:
   - Node 24 → `ERR_UNSUPPORTED_NODE_MODULES_TYPE_STRIPPING`
   - Node 20 → `ERR_UNKNOWN_FILE_EXTENSION`
4. Bisect evidence (per-plugin `expo config` runs):
   - `plugins: []` → **PASS**
   - `plugins: ["./modules/sanad-call-screening/app.plugin.js"]` → **PASS** (Track E plugin innocent)
   - `plugins: ["expo-crypto"]` → **FAIL** (sole culprit; secure-store/sqlite PASS)

**Fix:** removed `expo-crypto` from `plugins` (autolinking unaffected).
**Corrected environment rule:** Expo CLI operations for this repo must run on
**Node 20 LTS** (SDK 53 supported line). Node 24's node_modules TS refusal is a
real constraint but only ever surfaced through the bogus plugin entry.

## 3 — Governed upgrade SDK 52 → 53 (§5–§7)

Method: official `npx expo install expo@^53` + `npx expo install --fix`, npm as
the project package manager. Two misplacements introduced by the CLI fixer were
corrected (moved to devDependencies; `react-test-renderer` realigned):

```text
                      BEFORE                 AFTER
expo                  ~52.0.0  (52.0.49)      ^53       (53.0.27)
react                 19.2.8                 19.0.0
react-native          0.79.4                 0.79.6
expo-modules-core     2.2.3                  2.5.0
expo-crypto           14.0.2                 14.1.5
expo-secure-store     14.0.1                 14.2.4
expo-sqlite           15.0.6                 15.2.14
jest-expo             52.0.6 (dev)           53.0.14 (dev)
typescript            ~5.8.0 (5.8.3)         ~5.8.0  (5.8.3)   unchanged
@types/react          ~19.2.0 (dev)          ~19.0.10 (dev)
react-test-renderer   (nested 18.3.1 only)   19.0.0 (dev, explicit)
```

No web/backend packages touched (§7 constraint).

## 4 — Lockfile + doctor gates (§8–§9)

```text
npm ci (run 1, clean node_modules, Node 20) = PASS
npm ci (run 2, clean node_modules, Node 20) = PASS → added 823 packages deterministically
LOCKFILE_REPRODUCIBLE                       = YES
npx expo install --check                    = "Dependencies are up to date"
npx expo-doctor                             = 18/18 checks passed
```

(The only doctor failure ever seen was missing scaffold assets — resolved in §5.)

## 5 — Prebuild prerequisites (part of §13)

`expo prebuild` hard-fails on the scaffold asset references (`icon.png`,
`adaptive-icon.png`, `splash.png`) that were referenced by `app.json` since
inception but never committed (verified: no git history for `apps/mobile/assets`).
Added neutral placeholder PNGs + committed generator
(`apps/mobile/scripts/generate-placeholder-assets.js`). These are build inputs,
not brand assets; owner may replace later.

## 6 — Real prebuild + manifest gates (§13–§14)

```text
npx expo prebuild --platform android --clean   = PASS (Node 20)
repeat prebuild (no --clean)                   = PASS
DUPLICATE_SERVICE    = 0      DUPLICATE_ACTIVITY = 0      DUPLICATE_PERMISSION = 0
```

Generated `android/app/src/main/AndroidManifest.xml`:

```text
com.sanad.crm.callerid.SanadCallScreeningService   = 1
android.permission.BIND_SCREENING_SERVICE          = 1
android.telecom.CallScreeningService (intent)      = 1
com.sanad.crm.callerid.SanadCallerIdActivity       = 1
android.permission.READ_CONTACTS                   = 1
READ_PHONE_STATE / READ_CALL_LOG / CALL_PHONE / SYSTEM_ALERT_WINDOW = 0
```

**New finding:** the Expo bare-minimum manifest template
(`getAndroidManifestTemplate`, `@expo/config-plugins`) ships
`SYSTEM_ALERT_WINDOW` as a default "optional permission". Absence of forbidden
permissions can therefore only be guaranteed by active stripping. The
sanad-call-screening config plugin now removes all four forbidden permissions on
every mutation, and the validator asserts a template-like input comes out clean
(while preserving non-forbidden template permissions such as VIBRATE).

Config-plugin validator:

```text
CONFIG_PLUGIN_VALIDATION: PASS
(service=1, activity=1, READ_CONTACTS=1, BIND_SCREENING=1, intent-filter=1,
 idempotent=yes, forbidden-permissions=0, template-strip=yes)
```

## 7 — Main reconciliation (§11–§12)

```text
CURRENT_MAIN_SHA = 516fc8ea (4 commits: G7 execution-board integrity fix, PR #892)
merge origin/main → feature branch = CLEAN (auto-merge)
```

Main's board-consistency fixes (G7 task registrations, consistency validator,
new consistency test) are intact, and the G8 stageReport line was preserved.
stageReport updated to V5 (truthful wording per §12: Track E = BLOCKED pending
physical device, not COMPLETE).

## 8 — Full app APK (§15–§17)

```text
command  = cd android && ./gradlew assembleDebug   (JDK 17.0.19, Gradle 8.13 wrapper)
FULL_APP_APK = <PENDING — see §RESULT>
APK path    = <PENDING>
APK size    = <PENDING>
APK SHA-256 = <PENDING>
APK_NATIVE_MANIFEST (aapt2 dump xmltree, inside the built APK):
  SanadCallScreeningService / SanadCallerIdActivity / BIND_SCREENING_SERVICE /
  CallScreeningService intent / READ_CONTACTS present; forbidden four absent = <PENDING>
```

AAR 21/21 remains valid module-level evidence only (§16) — it is not an APK
substitute and is retained as the harness lower layer.

## 9 — Regression (§19–§21)

```text
npm run typecheck                      = PASS
npm test (jest)                        = PASS — 99/99 (11 suites)
  Track D regression: caller-normalizer-parity, caller-hmac           PASS
  G7 regression: sync-engine, push-sync, conflict-resolver, security  PASS
Kotlin JVM tests (module harness)      = <PENDING — after Gradle frees>
```

No G7 breakage from the SDK alignment (§21).

## 10 — CI (§22–§23)

`.github/workflows/g8-android-native-validation.yml` rewritten as three layers:

```text
1. mobile-regression — Node 20, npm ci, typecheck + jest
2. android-native    — harness (JVM tests + AAR + plugin mutation) — lower layer kept
3. android-full-apk  — REAL expo prebuild + full gradlew assembleDebug +
                       generated-manifest assertions + IN-APK (aapt2) manifest
                       proof + APK artifact upload (30-day retention)
```

Backend/OpenAPI/Flyway untouched (§25): `BACKEND CHANGE = 0`, `FLYWAY = 0`,
`OPENAPI = 0` — verified by merge diff scope (only web board files came from main).

## 11 — Verdict inputs (§27)

```text
EXPO_SDK_MATRIX      = ALIGNED (SDK 53 / RN 0.79.6 / React 19.0.0)
EXPO_DOCTOR          = PASS (18/18)
PREBUILD             = PASS
PREBUILD_IDEMPOTENCY = PASS
FULL_APP_APK         = <PENDING>
APK_NATIVE_MANIFEST  = <PENDING>
MOBILE_REGRESSION    = PASS (99/99 + typecheck)
KOTLIN               = <PENDING>
CI                   = <PENDING (branch push)>
```

**Standing constraints:** no merge of PR #893 (§2/§28) — the physical-device
gate (§29–§36) follows this command and remains OPEN. Emulator evidence, if any,
does not close it (§18).
