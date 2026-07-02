# SNAD Mobile Environment

## Decision

```text
MOBILE FRAMEWORK: EXPO SDK 57
REACT NATIVE: 0.86.0
REACT: 19.2.3
TARGETS: ANDROID + IOS + IPADOS + WEB COMPATIBILITY
DELIVERY: EAS BUILD / EAS UPDATE / EAS SUBMIT
DEFAULT RELEASE STATE: DISABLED
```

## Architecture

```text
SNAD Backend APIs
       ↑
Mobile API Client
       ↑
Auth + Query + Offline Sync + Localization
       ↑
Expo Router Application
       ↑
Android / iPhone / iPad
```

The mobile application uses the same server-side tenant, authentication, authorization, workflow, AI Gateway, audit, and CRM contracts as the web application. Mobile code must not create an alternative source of truth.

## Environment matrix

| Environment | API | Distribution | Updates | Submission |
|---|---|---|---|---|
| Development | Local or development API | Development client | Optional | No |
| Preview | Preview API | Internal Android/iOS | Preview channel | No |
| Production | Production API | Store build | Production channel | Explicit only |

## Installed foundations

- Expo Router universal navigation.
- Strict TypeScript.
- TanStack Query with network awareness.
- SecureStore session persistence.
- LocalAuthentication integration point.
- SQLite offline queue foundation.
- Expo Notifications integration point.
- Expo Updates automatic activation when configured.
- Arabic and English localization.
- Runtime environment validation.
- GitHub Actions quality, preview, and release jobs.
- EAS development, preview, and production profiles.

## Automatic behavior

The mobile environment remains inert when credentials are unavailable. It automatically activates each capability only when its dependencies exist:

| Capability | Activation condition |
|---|---|
| Backend health | `EXPO_PUBLIC_API_BASE_URL` is reachable |
| EAS Updates | `EAS_PROJECT_ID` is configured in app config |
| Push token | Real device, permission granted, and EAS project configured |
| Preview builds | `EXPO_TOKEN` exists and `MOBILE_AUTO_BUILD_ENABLED=true` |
| Store submission | Release tag, `EXPO_TOKEN`, protected environment, and `MOBILE_AUTO_SUBMIT_ENABLED=true` |

## Mandatory secrets policy

- No OpenAI, email, database, signing, or provider secret in `EXPO_PUBLIC_*`.
- No credentials committed to Git.
- Apple and Google signing credentials remain in EAS credential management.
- Access tokens are memory-only.
- Refresh tokens use platform secure storage.
- Offline data must be minimized and tenant-scoped.

## Remaining gates

- Generate and commit `package-lock.json` through a successful package-manager run.
- Restore GitHub Actions execution at the repository/account level.
- Create the EAS project and set `EAS_PROJECT_ID`.
- Configure `EXPO_TOKEN` in GitHub Actions.
- Configure development, preview, and production API URLs in EAS.
- Add Apple and Google developer accounts and signing credentials.
- Complete CRM authentication, Accounts, Contacts, Leads, Opportunities, Activities, and notifications.
- Add device tests, accessibility tests, offline conflict-resolution tests, and store compliance evidence.
- Obtain explicit production and store-release approval.
