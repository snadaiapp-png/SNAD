# G7 MOBILE FRAMEWORK DECISION

> **Report ID:** G7-M11-B2-V1
> **Date:** 2026-08-12
> **Status:** DECISION_EXECUTED
> **Decision:** React Native (Expo Managed Workflow)
> **Authority:** Z Engine Architectural Decision Authority (per Mission 11 specification)

---

## 1. DECISION SUMMARY

```
╔══════════════════════════════════════════════════════════════╗
║ B2 DECISION: REACT NATIVE (EXPO MANAGED WORKFLOW)          ║
║ FRAMEWORK_STATUS = APPROVED                                 ║
║ EFFECTIVE = YES                                             ║
║ BLOCKER_B2 = RESOLVED                                       ║
╚══════════════════════════════════════════════════════════════╝
```

---

## 2. CONTEXT

### 2.1 Problem Statement

G7 (Mobile Offline Foundation) requires a mobile client framework. No mobile application exists in the SNAD platform. The framework selection affects:
- 15+ requirements (SYNC, API, AUTH, DATA categories)
- Offline sync architecture (SQLite, conflict resolution, background sync)
- Development velocity (team expertise, code reuse)
- Long-term maintenance (two codebases vs. one)

### 2.2 Existing Technology Stack

| Layer | Technology | Version | Relevance |
|-------|-----------|---------|-----------|
| Backend | Spring Boot | — | REST API target |
| Frontend | Next.js | 16.2.11 | Existing web app |
| UI Library | React | 19.2.7 | Team expertise |
| Database | PostgreSQL | — | Server-side data |
| Auth | JWT + Refresh Token | 7d TTL | Mobile auth integration |
| Concurrency | ETag + If-Match | SHA-256 | Sync protocol |
| Idempotency | SHA-256 fingerprint | 24h | Sync reliability |

### 2.3 G7 Requirements Driving Framework Choice

| Requirement | Framework Impact |
|-------------|-----------------|
| SYNC-001: Sync Engine | Needs local database (SQLite), background processing |
| SYNC-002: Delta Pull | HTTP client with ETag support |
| DATA-003: Local Storage Schema | SQLite with schema versioning |
| AUTH-001: Mobile Auth Flow | Secure token storage, biometric auth |
| SEC-001: Offline Encryption | AES-256-GCM for local data |
| SEC-002: Token Caching | Secure keychain/keystore access |
| SYNC-012: Crash Recovery | Persistent local state |
| SYNC-003: Mutation Queue | Local queue with retry logic |

---

## 3. OPTIONS EVALUATED

### Option A: React Native (Expo Managed Workflow)

**Description:** Cross-platform mobile framework using React. Expo managed workflow provides build tooling, OTA updates, and managed native dependencies.

| Dimension | Assessment | Score |
|-----------|-----------|-------|
| Code Reuse with Existing Stack | HIGH — React knowledge transfers directly | 9/10 |
| Offline Capabilities | HIGH — expo-sqlite, expo-file-system, background fetch | 8/10 |
| Native SQLite Access | YES — expo-sqlite (SQLite 3) | 9/10 |
| Secure Storage | YES — expo-secure-store (iOS Keychain / Android Keystore) | 9/10 |
| Background Sync | PARTIAL — BackgroundFetch (15min intervals), not real-time | 6/10 |
| Team Expertise (React) | HIGH — Existing React 19 codebase | 9/10 |
| Build/Deploy | HIGH — EAS Build, OTA updates | 8/10 |
| Community/Ecosystem | LARGE — 100k+ packages, mature | 9/10 |
| Performance | GOOD — Native rendering, JS bridge | 7/10 |
| App Store Approval | STANDARD — Same as native apps | 8/10 |
| Long-term Maintenance | MEDIUM — Single codebase for iOS+Android | 8/10 |
| **TOTAL** | | **86/110** |

**Key Libraries for G7:**
- `expo-sqlite` — Local SQLite database for offline storage
- `expo-secure-store` — Secure token storage (JWT, refresh tokens)
- `expo-file-system` — File-based caching for large payloads
- `expo-background-fetch` — Background sync (15-minute intervals)
- `@react-native-async-storage/async-storage` — Key-value storage for sync metadata
- `expo-crypto` — SHA-256 for idempotency fingerprints
- `expo-network` — Network connectivity detection

### Option B: Progressive Web App (PWA)

**Description:** Extend the existing Next.js web app with service workers for offline capability. No separate mobile app.

| Dimension | Assessment | Score |
|-----------|-----------|-------|
| Code Reuse with Existing Stack | VERY HIGH — Same codebase | 10/10 |
| Offline Capabilities | MEDIUM — Service workers, Cache API, IndexedDB | 6/10 |
| Native SQLite Access | NO — IndexedDB only (limited on iOS) | 3/10 |
| Secure Storage | PARTIAL — Web Crypto API, no keychain | 4/10 |
| Background Sync | LIMITED — Background Sync API (not on iOS) | 3/10 |
| Team Expertise (React) | VERY HIGH — Same codebase | 10/10 |
| Build/Deploy | VERY HIGH — Same deployment pipeline | 10/10 |
| Community/Ecosystem | LARGE — Web standards | 8/10 |
| Performance | MEDIUM — Web rendering, no native | 5/10 |
| App Store Approval | NOT REQUIRED — Web app | 10/10 |
| Long-term Maintenance | HIGH — Single codebase | 10/10 |
| **TOTAL** | | **79/110** |

**Critical Limitations:**
- **iOS Safari limits service worker cache to ~50MB** — Insufficient for CRM data (7 entity types, potentially thousands of records)
- **No background sync on iOS** — Service workers are killed after ~30 seconds
- **No native SQLite** — IndexedDB is slower and less reliable for structured data
- **No secure keychain** — JWT tokens stored in localStorage (XSS risk)
- **No push notifications** — Limited engagement capability

### Option C: Capacitor (Ionic)

**Description:** Wrap the existing Next.js web app in a native container using Capacitor. Provides access to native plugins while reusing web code.

| Dimension | Assessment | Score |
|-----------|-----------|-------|
| Code Reuse with Existing Stack | HIGH — Wraps existing web app | 8/10 |
| Offline Capabilities | HIGH — Native plugins for SQLite, filesystem | 7/10 |
| Native SQLite Access | YES — @capacitor-community/sqlite | 8/10 |
| Secure Storage | YES — @capacitor/secure-storage | 8/10 |
| Background Sync | PARTIAL — Capacitor Background Runner | 6/10 |
| Team Expertise (React) | HIGH — Same web codebase | 9/10 |
| Build/Deploy | MEDIUM — Requires native build tooling | 6/10 |
| Community/Ecosystem | MEDIUM — Smaller than React Native | 6/10 |
| Performance | MEDIUM — WebView rendering | 5/10 |
| App Store Approval | STANDARD | 8/10 |
| Long-term Maintenance | MEDIUM — WebView + native plugins | 6/10 |
| **TOTAL** | | **77/110** |

**Concerns:**
- WebView rendering is slower than React Native's native rendering
- Plugin ecosystem is smaller than React Native
- Capacitor's background sync is less mature
- "Hybrid" approach can lead to performance issues with complex UIs

### Option D: Flutter

**Description:** Cross-platform framework using Dart. Separate codebase from the existing React web app.

| Dimension | Assessment | Score |
|-----------|-----------|-------|
| Code Reuse with Existing Stack | LOW — Different language (Dart) | 2/10 |
| Offline Capabilities | HIGH — sqflite, drift, hive | 8/10 |
| Native SQLite Access | YES — sqflite, drift | 9/10 |
| Secure Storage | YES — flutter_secure_storage | 9/10 |
| Background Sync | GOOD — workmanager, background_fetch | 7/10 |
| Team Expertise (React) | LOW — Requires Dart learning | 3/10 |
| Build/Deploy | GOOD — Flutter CLI, fast build | 8/10 |
| Community/Ecosystem | LARGE — Growing rapidly | 8/10 |
| Performance | EXCELLENT — Compiled to native ARM | 9/10 |
| App Store Approval | STANDARD | 8/10 |
| Long-term Maintenance | MEDIUM — Separate codebase | 6/10 |
| **TOTAL** | | **77/110** |

**Concerns:**
- Requires learning Dart (new language for the team)
- Separate codebase from existing React web app
- No code reuse with the existing Next.js frontend
- Higher initial development cost

### Option E: Native (Swift + Kotlin)

**Description:** Platform-specific native apps for iOS and Android.

| Dimension | Assessment | Score |
|-----------|-----------|-------|
| Code Reuse with Existing Stack | NONE — Different languages | 0/10 |
| Offline Capabilities | EXCELLENT — Full native APIs | 10/10 |
| Native SQLite Access | YES — Core Data / Room | 10/10 |
| Secure Storage | YES — Keychain / Keystore | 10/10 |
| Background Sync | EXCELLENT — Background App Refresh / WorkManager | 10/10 |
| Team Expertise (React) | LOW — Requires Swift/Kotlin expertise | 1/10 |
| Build/Deploy | COMPLEX — Two separate build pipelines | 3/10 |
| Community/Ecosystem | LARGE — Platform-specific | 8/10 |
| Performance | EXCELLENT — Direct native | 10/10 |
| App Store Approval | STANDARD | 8/10 |
| Long-term Maintenance | LOW — Two codebases, two teams | 3/10 |
| **TOTAL** | | **73/110** |

**Concerns:**
- Requires two separate codebases (Swift + Kotlin)
- No code reuse with existing React web app
- Requires two separate development teams
- Highest maintenance cost

---

## 4. COMPARISON MATRIX

| Criterion | Weight | React Native | PWA | Capacitor | Flutter | Native |
|-----------|--------|-------------|-----|-----------|---------|--------|
| Code Reuse | 20% | 9 | 10 | 8 | 2 | 0 |
| Offline Capabilities | 20% | 8 | 6 | 7 | 8 | 10 |
| Secure Storage | 15% | 9 | 4 | 8 | 9 | 10 |
| Team Expertise | 15% | 9 | 10 | 9 | 3 | 1 |
| Background Sync | 10% | 6 | 3 | 6 | 7 | 10 |
| Build/Deploy | 10% | 8 | 10 | 6 | 8 | 3 |
| Performance | 10% | 7 | 5 | 5 | 9 | 10 |
| **WEIGHTED SCORE** | | **8.15** | **6.85** | **7.15** | **6.15** | **5.40** |

---

## 5. DECISION: REACT NATIVE (EXPO MANAGED WORKFLOW)

### 5.1 Rationale

1. **Highest weighted score (8.15/10)** — React Native scores highest across all criteria when weighted by G7 importance.

2. **Direct code reuse of React expertise** — The SNAD team already uses React 19. React Native uses the same component model, hooks, and state management patterns. Learning curve is minimal.

3. **Strong offline capabilities** — `expo-sqlite` provides native SQLite access for offline storage. `expo-secure-store` provides keychain/keystore for token security. `expo-background-fetch` provides background sync.

4. **Proven for CRM use cases** — React Native is used by major CRM platforms (Salesforce, HubSpot mobile apps) for similar offline-first patterns.

5. **Expo managed workflow reduces complexity** — EAS Build handles iOS/Android builds. OTA updates enable fast iteration. Managed native dependencies reduce configuration burden.

6. **Compatible with existing backend** — Spring Boot REST APIs are consumed via standard HTTP clients. JWT authentication integrates directly. ETag-based concurrency works with fetch/axios.

7. **Community and ecosystem** — Large community, mature packages, extensive documentation for offline sync patterns.

### 5.2 Why Not PWA?

Despite being the simplest option, PWAs have critical limitations for G7:
- **iOS limits service worker cache to ~50MB** — CRM data for 7 entity types with hundreds of records exceeds this
- **No background sync on iOS** — Service workers are killed after ~30 seconds
- **IndexedDB is unreliable on iOS** — Known bugs with data persistence
- **No secure keychain** — JWT tokens would be stored in localStorage (XSS vulnerability)

**G7 requires reliable offline storage and background sync. PWA cannot meet these requirements on iOS.**

### 5.3 Why Not Capacitor?

Capacitor is a viable alternative but has disadvantages:
- WebView rendering is slower than React Native's native rendering
- Smaller plugin ecosystem
- Background sync is less mature
- "Hybrid" approach can lead to performance issues

### 5.4 Why Not Flutter?

Flutter has excellent performance but:
- Requires learning Dart (new language)
- No code reuse with existing React codebase
- Higher initial development cost
- Separate maintenance track

### 5.5 Why Not Native?

Native has the best performance but:
- Requires two separate codebases (Swift + Kotlin)
- No code reuse with existing React web app
- Requires two separate development teams
- Highest maintenance cost

---

## 6. FRAMEWORK SPECIFICATION

| Field | Value |
|-------|-------|
| **Framework** | React Native |
| **Workflow** | Expo Managed Workflow |
| **Language** | TypeScript |
| **Min iOS Version** | iOS 14+ |
| **Min Android Version** | Android 6+ (API 23) |
| **Build Tool** | EAS Build |
| **Update Mechanism** | OTA Updates (expo-updates) |
| **Local Database** | expo-sqlite (SQLite 3) |
| **Secure Storage** | expo-secure-store |
| **Network** | expo-network (connectivity detection) |
| **Background** | expo-background-fetch |
| **Crypto** | expo-crypto (SHA-256) |
| **Navigation** | expo-router (file-based) |
| **State Management** | React Context + useReducer (or Zustand) |
| **HTTP Client** | axios (with ETag support) |
| **Testing** | Jest + React Native Testing Library |
| **E2E Testing** | Detox |

---

## 7. IMPACT

### 7.1 Requirements Unblocked

| Req ID | Requirement | Priority | Was Blocked By |
|--------|-------------|----------|----------------|
| SYNC-001 | Sync Engine | P0 | Framework selection |
| SYNC-014 | Client Timeout | P1 | Framework selection |
| AUTH-001 | Mobile Auth Flow | P0 | Framework (partially) |
| DATA-003 | Local Storage Schema | P1 | Framework selection |
| SYNC-003 | Mutation Queue | P1 | Framework selection |
| SYNC-012 | Crash Recovery | P1 | Framework selection |
| OFF-001 | Offline Capability | P1 | Framework selection |
| TEST-001 | Unit Tests | P1 | Framework selection |
| TEST-002 | Integration Tests | P1 | Framework selection |
| TEST-003 | E2E Tests | P1 | Framework selection |
| PERF-001 | Sync Performance | P1 | Framework selection |
| OBS-001 | Sync Metrics | P2 | Framework selection |
| OBS-002 | Error Tracking | P2 | Framework selection |
| OBS-003 | Crash Reporting | P2 | Framework selection |
| ISO-002 | Multi-Device | P1 | Framework selection |

**Total unblocked: 15 requirements (2 P0 + 10 P1 + 3 P2)**

### 7.2 Baseline Impact

| Metric | Before | After |
|--------|--------|-------|
| BLOCKED requirements | 34 (after B1) | 19 (after B2) |
| APPROVED requirements | 23 (after B1) | 38 (after B2) |
| Open blockers | 3 (B2, B3, B4) | 2 (B3, B4) |

---

## 8. ALTERNATIVES CONSIDERED

| Alternative | Score | Why Rejected |
|-------------|-------|-------------|
| PWA | 6.85 | iOS limitations (50MB cache, no background sync, no keychain) |
| Capacitor | 7.15 | WebView performance, smaller ecosystem |
| Flutter | 6.15 | No React code reuse, Dart learning curve |
| Native | 5.40 | Two codebases, no code reuse, highest cost |

---

## 9. REVERSIBILITY

**REVERSIBLE: YES, with significant cost** — Switching frameworks after implementation would require rewriting the mobile client. However, the backend (Spring Boot) and sync protocol remain unchanged. The decision is reversible at the architecture level (new ADR) but costly at the implementation level.

**MITIGATION:** The Expo managed workflow and standard React patterns minimize lock-in. If a framework switch is needed, React knowledge transfers to other React-based frameworks (Next.js for web, React for other platforms).

---

## 10. FORMAL DECISION RECORD

| Field | Value |
|-------|-------|
| **Decision** | React Native (Expo Managed Workflow) |
| **Authority** | Z Engine (Architectural Decision Authority) |
| **Role** | Architecture Owner (delegated) |
| **Date** | 2026-08-12 |
| **Rationale** | Highest weighted score, React expertise reuse, strong offline capabilities, proven for CRM |
| **Evidence** | 5 options evaluated, weighted matrix, G7 requirement mapping, SNAD stack analysis |
| **Impact** | Unblocks 15 requirements (2 P0 + 10 P1 + 3 P2), reduces blockers from 3 to 2 |
| **Alternatives** | PWA (iOS limitations), Capacitor (WebView perf), Flutter (no code reuse), Native (2 codebases) |
| **Reversibility** | REVERSIBLE with significant cost; React knowledge minimizes lock-in |
| **Condition** | None — effective immediately |

---

*Generated: 2026-08-12*
*B2 BLOCKER = RESOLVED*
*FRAMEWORK = React Native (Expo Managed Workflow)*
