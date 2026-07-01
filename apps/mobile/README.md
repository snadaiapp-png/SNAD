# SNAD CRM Mobile

Universal Android, iPhone, iPad, and web-compatible mobile workspace built with Expo SDK 57 and React Native 0.86.

## Local startup

```bash
cd apps/mobile
cp .env.example .env.local
npm ci
npm run verify:env
npm start
```

Use `A` for Android, `I` for iOS on macOS with Xcode, or `W` for web.

## Runtime environments

- `development`: local API and development builds.
- `preview`: internal Android APK and iOS internal distribution.
- `production`: App Store and Google Play builds after release approval.

Required production values:

```text
EXPO_PUBLIC_APP_ENV=production
EXPO_PUBLIC_API_BASE_URL=https://api.example.com
EAS_PROJECT_ID=<expo-project-uuid>
```

`EXPO_PUBLIC_*` values are public application configuration. Never place API keys, refresh tokens, signing keys, provider credentials, or OpenAI keys in them.

## Automatic activation

GitHub Actions runs quality checks whenever mobile files change. Cloud builds remain safely disabled until these values exist:

- GitHub Actions secret: `EXPO_TOKEN`.
- Repository variable: `MOBILE_AUTO_BUILD_ENABLED=true`.
- EAS environments: `development`, `preview`, and `production`.

Production store submission additionally requires:

- Repository variable: `MOBILE_AUTO_SUBMIT_ENABLED=true`.
- A protected GitHub environment named `mobile-production`.
- A tag matching `mobile-v*`.
- Valid Apple and Google credentials managed through EAS.

## Security model

- Access tokens remain in memory.
- Refresh tokens and identity references use `expo-secure-store`.
- Tenant identity remains server-authoritative.
- Local SQLite is used only for permitted offline cache and a bounded synchronization queue.
- Push tokens are obtained only on real devices after permission and EAS project configuration.
- OTA updates activate only when EAS Updates is configured.

## Current status

```text
MOBILE WORKSPACE: INSTALLED ON FEATURE BRANCH
PACKAGE LOCK: GENERATED AND COMMITTED
LOCAL DEVELOPMENT: READY AFTER npm ci
EXPO DOCTOR: PASS
TYPESCRIPT: PASS
ESLINT: PASS
UNIT TEST COMMAND: PASS
UNIVERSAL EXPORT: PASS IN ISOLATED EVIDENCE JOB
AUTOMATIC QUALITY: CONFIGURED
EAS PREVIEW BUILD: WAITS FOR EXPO_TOKEN AND ENABLE VARIABLE
STORE SUBMISSION: DISABLED BY DEFAULT
PRODUCTION AUTHORIZATION: NOT GRANTED
```
