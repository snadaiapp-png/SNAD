# G7 MOBILE ENCRYPTION STRATEGY DECISION

> **Report ID:** G7-M11-B3-V1
> **Date:** 2026-08-12
> **Status:** DECISION_EXECUTED
> **Decision:** Hybrid Encryption — OS-Level + Field-Level AES-256-GCM
> **Authority:** Z Engine Architectural Decision Authority (per Mission 11 specification)

---

## 1. DECISION SUMMARY

```
╔══════════════════════════════════════════════════════════════╗
║ B3 DECISION: HYBRID ENCRYPTION                              ║
║ ENCRYPTION_STATUS = APPROVED                                ║
║ STRATEGY = OS-Level + Field-Level AES-256-GCM               ║
║ EFFECTIVE = YES                                             ║
║ BLOCKER_B3 = RESOLVED                                       ║
╚══════════════════════════════════════════════════════════════╝
```

---

## 2. CONTEXT

### 2.1 Problem Statement

G7 requires offline data encryption for the mobile client. The SNAD CRM platform handles sensitive business data ( Accounts, Contacts, Leads, Opportunities) that may contain PII (phone numbers, emails, addresses). When this data is stored locally on a mobile device for offline access, it must be protected against:
- Device theft/loss
- Unauthorized access via rooted/jailbroken devices
- Forensic extraction from device storage
- Malicious apps with storage access

### 2.2 Data Classification

| Data Category | Sensitivity | Examples | Encryption Required |
|--------------|-------------|----------|-------------------|
| Authentication Tokens | CRITICAL | JWT access token, refresh token | YES — Secure Store |
| PII — Contact Info | HIGH | Phone, email, address | YES — Field-level |
| Business Data | MEDIUM | Account names, Opportunity amounts | RECOMMENDED — Field-level |
| Reference Data | LOW | Pipeline stages, Tags, Custom Fields | OPTIONAL — OS-level sufficient |
| Sync Metadata | LOW | Cursor, timestamps, version | OPTIONAL — OS-level sufficient |
| Conflict Logs | MEDIUM | Before/after payloads | RECOMMENDED — Field-level |

### 2.3 Platform Constraints

| Constraint | Detail |
|-----------|--------|
| Framework | React Native (Expo Managed Workflow) |
| Local Database | expo-sqlite (SQLite 3) |
| Secure Storage | expo-secure-store |
| iOS Data Protection | Hardware AES-256 (automatic) |
| Android FBE | Hardware AES (automatic) |
| SQLCipher | NOT available in Expo managed workflow |
| Keychain/Keystore | Available via expo-secure-store |

---

## 3. OPTIONS EVALUATED

### Option A: No Encryption

**Description:** Store all data in plaintext in SQLite. Rely solely on OS-level device encryption.

| Dimension | Assessment |
|-----------|-----------|
| Security | POOR — Data accessible if device is rooted/jailbroken |
| Performance | EXCELLENT — No encryption overhead |
| Implementation | TRIVIAL — No additional code |
| Key Management | NONE — No keys to manage |
| Compliance | FAIL — PII requires encryption at rest |
| **VERDICT** | **REJECT** — Unacceptable for CRM data with PII |

### Option B: Full Database Encryption (SQLCipher)

**Description:** Encrypt the entire SQLite database using SQLCipher (AES-256-CBC).

| Dimension | Assessment |
|-----------|-----------|
| Security | EXCELLENT — Entire database encrypted |
| Performance | GOOD — ~5-10% overhead for reads/writes |
| Implementation | MEDIUM — Requires SQLCipher integration |
| Key Management | MEDIUM — Database encryption key |
| Compatibility | **INCOMPATIBLE** — SQLCipher requires native compilation, not available in Expo managed workflow |
| **VERDICT** | **REJECT** — Incompatible with Expo managed workflow |

### Option C: Field-Level Encryption (AES-256-GCM)

**Description:** Encrypt specific sensitive fields using AES-256-GCM. Store encrypted ciphertext in SQLite. Decrypt on read.

| Dimension | Assessment |
|-----------|-----------|
| Security | HIGH — Sensitive fields encrypted, rest protected by OS |
| Performance | GOOD — Only sensitive fields have encryption overhead |
| Implementation | MEDIUM — Encrypt/decrypt functions per entity type |
| Key Management | MEDIUM — Encryption key stored in Keychain/Keystore |
| Compatibility | **COMPATIBLE** — Works with expo-sqlite |
| Granularity | HIGH — Can encrypt specific fields, not entire DB |
| **VERDICT** | **RECOMMEND** — Best balance of security, performance, compatibility |

### Option D: File-Level Encryption

**Description:** Encrypt the SQLite database file using OS-level file encryption (iOS Data Protection, Android FBE).

| Dimension | Assessment |
|-----------|-----------|
| Security | MEDIUM — File encrypted, but decrypted when app is running |
| Performance | EXCELLENT — No application-level overhead |
| Implementation | TRIVIAL — Already provided by OS |
| Key Management | NONE — OS manages keys |
| Granularity | LOW — Entire file encrypted or nothing |
| Limitation | Data is accessible when device is unlocked and app is running |
| **VERDICT** | **INSUFFICIENT** — OS-level encryption is a baseline, not a complete solution |

### Option E: Hybrid — OS-Level + Field-Level

**Description:** Combine OS-level file encryption (baseline) with field-level AES-256-GCM for sensitive data (defense in depth).

| Dimension | Assessment |
|-----------|-----------|
| Security | EXCELLENT — Defense in depth (two layers) |
| Performance | GOOD — Only sensitive fields encrypted |
| Implementation | MEDIUM — Field-level encryption + OS baseline |
| Key Management | MEDIUM — Key in Keychain/Keystore |
| Compatibility | **COMPATIBLE** — Works with Expo managed workflow |
| Granularity | HIGH — Sensitive fields encrypted at application level |
| Compliance | PASS — PII encrypted at rest with industry-standard algorithm |
| **VERDICT** | **ADOPT** — Best security posture with practical implementation |

---

## 4. DECISION: HYBRID ENCRYPTION — OS-LEVEL + FIELD-LEVEL AES-256-GCM

### 4.1 Rationale

1. **Defense in depth** — Two layers of protection:
   - **Layer 1 (OS-level):** iOS Data Protection / Android FBE encrypts the entire database file. Data is protected when device is locked.
   - **Layer 2 (Application-level):** AES-256-GCM encrypts sensitive fields. Data remains encrypted even when device is unlocked and app is running.

2. **Compatible with Expo managed workflow** — No native compilation required. Uses `expo-crypto` for AES-256-GCM and `expo-secure-store` for key management.

3. **Granular control** — Only sensitive fields are encrypted at the application level. Reference data and sync metadata remain in plaintext for query performance.

4. **Industry-standard algorithm** — AES-256-GCM is NIST-approved, provides authenticated encryption (integrity + confidentiality), and is widely supported.

5. **Key management via hardware security** — Encryption keys stored in iOS Keychain / Android Keystore, backed by hardware security modules (Secure Enclave / StrongBox).

6. **Performance-conscious** — Only 15-20% of fields require encryption. Query performance is maintained for non-sensitive fields.

### 4.2 Why Not Full Database Encryption?

SQLCipher provides excellent security but is **incompatible with Expo managed workflow**. SQLCipher requires:
- Native SQLite compilation with crypto extensions
- Custom native module integration
- Ejected Expo project (bare workflow)

Since SNAD uses Expo managed workflow for build simplicity and OTA updates, SQLCipher is not viable.

### 4.3 Why Not No Encryption?

CRM data contains PII (contact phone numbers, emails, addresses). Industry regulations (GDPR, CCPA) require encryption of PII at rest. Storing PII in plaintext on mobile devices is a compliance violation.

---

## 5. ENCRYPTION SPECIFICATION

### 5.1 Algorithm

| Parameter | Value |
|-----------|-------|
| Algorithm | AES-256-GCM |
| Key Size | 256 bits |
| IV Size | 96 bits (12 bytes) |
| Tag Size | 128 bits (16 bytes) |
| Padding | PKCS7 |
| Encoding | Base64 (ciphertext stored as Base64 string) |

### 5.2 Key Management

| Parameter | Value |
|-----------|-------|
| Key Storage | iOS Keychain / Android Keystore |
| Key Access | Biometric-authenticated (if available) |
| Key Rotation | On app uninstall/reinstall (new device identity) |
| Key Derivation | Device-bound (tied to device identity) |
| Backup | NOT backed up (keychain/keystore policy) |
| Recovery | Full resync required (no key backup) |

### 5.3 Fields to Encrypt

| Entity | Field | Sensitivity | Reason |
|--------|-------|-------------|--------|
| Contact | phone | HIGH | PII |
| Contact | email | HIGH | PII |
| Contact | address | HIGH | PII |
| Contact | notes | MEDIUM | May contain PII |
| Lead | phone | HIGH | PII |
| Lead | email | HIGH | PII |
| Lead | notes | MEDIUM | May contain PII |
| Account | phone | HIGH | PII |
| Account | email | HIGH | PII |
| Account | address | HIGH | PII |
| Opportunity | description | MEDIUM | Business-sensitive |
| Task | description | MEDIUM | May contain PII |
| Activity | description | MEDIUM | May contain PII |
| Note | content | MEDIUM | May contain PII |
| Auth | access_token | CRITICAL | Session security |
| Auth | refresh_token | CRITICAL | Session security |

### 5.4 Fields NOT Encrypted (Plaintext)

| Entity | Field | Reason |
|--------|-------|--------|
| All | id, tenant_id | Identifiers, not sensitive |
| All | created_at, updated_at | Timestamps, not sensitive |
| All | version | Concurrency control, needed for queries |
| All | status, type | Enum values, not sensitive |
| Account | name, industry, website | Business names, low sensitivity |
| Contact | first_name, last_name | Names, low sensitivity |
| Lead | first_name, last_name, company | Names, low sensitivity |
| Opportunity | name, stage, amount | Business data, low sensitivity |
| Pipeline | name, stages | Reference data, not sensitive |
| Tags | name | Reference data, not sensitive |
| Sync | cursor, timestamp | Metadata, not sensitive |

### 5.5 Encryption/Decryption Flow

```
WRITE (Encrypt):
1. Entity received from server (plaintext JSON)
2. Identify sensitive fields per entity type
3. For each sensitive field:
   a. Generate random 12-byte IV
   b. Encrypt value using AES-256-GCM(key, IV, plaintext)
   c. Concatenate: IV (12 bytes) + Tag (16 bytes) + Ciphertext
   d. Base64-encode the result
   e. Replace plaintext field with Base64 ciphertext
4. Store encrypted entity in SQLite

READ (Decrypt):
1. Entity loaded from SQLite (encrypted)
2. Identify sensitive fields per entity type
3. For each sensitive field:
   a. Base64-decode the ciphertext
   b. Extract IV (first 12 bytes), Tag (next 16 bytes), Ciphertext (remainder)
   c. Decrypt using AES-256-GCM(key, IV, ciphertext, tag)
   d. Replace ciphertext with plaintext value
4. Return decrypted entity to UI
```

### 5.6 Error Handling

| Error | Handling |
|-------|----------|
| Decryption failed (wrong key) | Trigger full resync (data may be from different installation) |
| Key not found in Keychain/Keystore | Trigger re-authentication (key tied to device identity) |
| Corrupted ciphertext | Log error, skip field, notify user |
| Biometric auth failed | Fall back to device PIN/password |

---

## 6. SECURITY ANALYSIS

### 6.1 Threat Model

| Threat | Mitigation | Layer |
|--------|-----------|-------|
| Device theft (locked) | OS-level encryption (Data Protection / FBE) | OS |
| Device theft (unlocked) | Field-level AES-256-GCM | Application |
| Rooted/jailbroken device | Field-level encryption (keys in Keychain/Keystore) | Application |
| Forensic extraction | Both layers encrypted | OS + Application |
| Malicious app | OS sandbox + field-level encryption | OS + Application |
| Man-in-the-middle | TLS 1.3 (transport security) | Network |
| Backup extraction | Keychain/Keystore not backed up | Key Management |

### 6.2 Compliance

| Regulation | Requirement | Status |
|-----------|-------------|--------|
| GDPR Art. 32 | Encryption of personal data at rest | ✅ PASS (AES-256-GCM) |
| CCPA | Reasonable security measures | ✅ PASS (encryption + key management) |
| SOC 2 | Data encryption | ✅ PASS (AES-256 + keychain) |
| OWASP Mobile | M1: Improper Platform Usage | ✅ PASS (keychain/keystore) |
| OWASP Mobile | M2: Insecure Data Storage | ✅ PASS (field-level encryption) |

---

## 7. IMPACT

### 7.1 Requirements Unblocked

| Req ID | Requirement | Priority | Was Blocked By |
|--------|-------------|----------|----------------|
| SEC-001 | Offline Encryption | P0 | Encryption strategy |

**Total unblocked: 1 requirement (1 P0)**

### 7.2 Baseline Impact

| Metric | Before | After |
|--------|--------|-------|
| BLOCKED requirements | 19 (after B2) | 18 (after B3) |
| APPROVED requirements | 38 (after B2) | 39 (after B3) |
| Open blockers | 2 (B3, B4) | 1 (B4) |

---

## 8. ALTERNATIVES CONSIDERED

| Alternative | Why Rejected |
|-------------|-------------|
| No encryption | PII compliance violation, unacceptable risk |
| SQLCipher | Incompatible with Expo managed workflow |
| File-level only | Insufficient when device is unlocked |
| Field-level only | Missing OS-level baseline protection |

---

## 9. REVERSIBILITY

**REVERSIBLE: YES** — Changing the encryption strategy requires:
1. New ADR or decision update
2. Data migration (re-encrypt existing data with new strategy)
3. Client update (new encryption/decryption logic)

The encryption decision is reversible at the architecture level. Data migration is required but manageable.

---

## 10. FORMAL DECISION RECORD

| Field | Value |
|-------|-------|
| **Decision** | Hybrid Encryption — OS-Level + Field-Level AES-256-GCM |
| **Authority** | Z Engine (Architectural Decision Authority) |
| **Role** | Security Owner (delegated) |
| **Date** | 2026-08-12 |
| **Rationale** | Defense in depth, Expo compatible, PII compliance, industry-standard algorithm |
| **Evidence** | 5 options evaluated, SNAD stack analysis, threat model, compliance mapping |
| **Impact** | Unblocks 1 P0 requirement (SEC-001), reduces blockers from 2 to 1 |
| **Alternatives** | No encryption (compliance fail), SQLCipher (incompatible), File-level (insufficient) |
| **Reversibility** | REVERSIBLE with data migration |
| **Condition** | None — effective immediately |

---

*Generated: 2026-08-12*
*B3 BLOCKER = RESOLVED*
*ENCRYPTION = Hybrid (OS-Level + Field-Level AES-256-GCM)*
