# EXEC-PROMPT-CRM-007R2 — سجل بوابة الإغلاق الإنتاجي

> **التاريخ:** 2026-07-18 (Asia/Riyadh)  
> **الحالة:** DRAFT — NOT A CLOSURE RECORD  
> **PR:** #567 — DO NOT MERGE

## 1. القرار الرسمي الحالي

```text
EXEC-PROMPT-CRM-007: IN PROGRESS — BLOCKED
CRM-G3D:              OPEN — NOT APPROVED
Issue #563:           OPEN
CRM-008:              NOT AUTHORIZED
```

لا يجوز تحويل هذه الحالة إلى `CLOSED / APPROVED / AUTHORIZED` قبل اكتمال بوابات الإنتاج الواردة في القسم 7.

## 2. مرجع الشفرة والنشر الحالي

```text
CRM-007 feature merge:
083ea46bec989a27f60d7f6ca0085230188470b9

BFF API v2 hotfix merge:
ff4fba0bec7873ee11b760fbf43c4e562ba11eaf

CRM-G1 reconciliation merge:
4c7d6405d84d854232e3c09e4333e2c5dffa2903

Current main / Vercel production SHA:
7b7c06d6a96ee07e082de86baf8169d0b93f8c11

Current Vercel production deployment:
dpl_CD4Yq9BK1xwwzcjiAsMfwRSR8koe — READY
```

النشر `dpl_2HCSAmequnNrZQ6xBQvqCbEM5KNL` على `4c7d640...` تاريخي ولم يعد نشر Production الحالي.

## 3. ما تم التحقق منه

### 3.1 المستودع وVercel

- PRs #546 و#561 و#565 مدمجة.
- Vercel Git Integration تعمل.
- Production deployment الحالي بحالة `READY` وعلى `main` SHA `7b7c06d...`.
- صفحات الواجهة العامة قابلة للنشر عبر Vercel.

### 3.2 CI وPostgreSQL/Testcontainers

على رأس PR السابق `457ffff5992b20edb1fbc28ba169581b277f0827`:

- Workflow `CI` رقم التشغيل `29652401036`: SUCCESS.
- Job `Maven Test Suite` رقم `88100843057`: SUCCESS.
- خطوة `Verify Docker availability (for Testcontainers)`: SUCCESS.
- خطوة `Run tests (including Testcontainers)`: SUCCESS.
- Surefire artifact: `8431866651`.
- تقارير Surefire: 104 ملفات XML، دون failures أو errors.
- اختبارات PostgreSQL التالية نفذت ونجحت:
  - `CrmAddressCommunicationMigrationUpgradeTest` — 2 tests.
  - `CrmPostgresMigrationTest` — 3 tests.
  - `CrmG1TenantIsolationPostgresTest` — 1 test.
  - `CrmContactRelationshipMigrationUpgradeTest` — 2 tests.
  - `FlywayV15ProductionUpgradeTest` — 1 test.
- سجلات Testcontainers تثبت تنفيذ PostgreSQL فعليًا داخل GitHub Actions.
- سجلات Flyway تثبت نجاح `20260717.100` و`20260717.101` على PostgreSQL Testcontainers.
- سجلات PostgreSQL Testcontainers تثبت نجاح migration `20260718.1` في مسارات الاختبار التي تحمل vendor `postgresql`.

هذه أدلة صلاحية migration chain على PostgreSQL في CI، لكنها ليست دليلًا بذاتها على أن قاعدة runtime العامة الدائمة قد استقبلت migration نفسها.

## 4. الفحص الحي الحالي

أعيد الفحص على Production بعد تحرك `main` إلى `7b7c06d...`:

```text
GET /api/system/backend-status
HTTP 200
configured=true
reachable=true
statusCode=200
```

لكن المسارات الفعلية أعادت:

```text
GET /api/platform/api/v1/auth/me
HTTP 502

GET /api/platform/api/v2/crm/accounts/{id}/addresses
HTTP 502
```

كما توجد مجموعة أخطاء حديثة في Vercel:

```text
BackendRequestError
failureKind=timeout
route=/api/platform/[...path]
path=/api/v1/auth/me
```

لذلك لا يجوز اعتماد `backend-status` منفردًا، ولا يجوز تسجيل `Runtime Errors: 0` أو `Unexpected 5xx: 0`.

## 5. حالة قاعدة التشغيل

التقرير السابق وصف runtime بأنه:

```text
SPRING profile=local
H2 in PostgreSQL mode
```

هذا لا يحقق بوابة Production PostgreSQL الدائمة. نتائج Testcontainers تثبت التوافق والقدرة على الترحيل، لكنها لا تثبت قاعدة runtime المستمرة ولا حفظ البيانات عبر إعادة تشغيل الجهاز.

المطلوب قبل الإغلاق:

- `SPRING_PROFILES_ACTIVE=prod`.
- PostgreSQL دائم، محلي أو خارجي، وليس H2 in-memory.
- `JPA_DDL_AUTO=validate`.
- `FLYWAY_ENABLED=true`.
- إثبات `flyway_schema_history` من قاعدة runtime الدائمة.
- إثبات `20260717.100`, `20260717.101`, `20260718.1` كـ `SQL / SUCCESS`.
- إثبات postconditions: CRM-G1 `8/8` tables، `26/26` indexes، `8/8` tenant FKs، وCRM-007 `5/5` canonical tables.

## 6. الأمن والهوية

ظهور بيانات اعتماد في المحادثة يفرض التدوير قبل استخدام أي منها مجددًا:

- GitHub PAT المشار إليه في الجلسة: rotation/revocation غير مثبت.
- Vercel tokens المشار إليها في الجلسة: rotation/revocation غير مثبت.

لا يحتوي هذا الملف على القيم السرية ولا يجوز إضافتها للمستودع أو السجلات.

Authenticated production smoke غير منفذ لعدم وجود هوية Smoke مخصصة مثبتة. Anonymous `401` يثبت حدود المصادقة فقط، ولا يثبت CRUD أو tenant isolation أو RBAC بعد تسجيل الدخول.

## 7. العوائق المتبقية

1. إعادة BFF v1 وv2 إلى استجابات مستقرة دون `502` أو timeout.
2. تشغيل backend runtime بملف `prod` وقاعدة PostgreSQL دائمة.
3. إثبات تطبيق Flyway `20260718.1` على قاعدة runtime الدائمة.
4. إثبات post-migration schema من قاعدة runtime الدائمة.
5. إنشاء هوية Production Smoke محدودة الصلاحيات.
6. تنفيذ login/session/refresh smoke عبر Vercel+BFF.
7. تنفيذ CRM-007 authenticated lifecycle وconflict smoke.
8. تنفيذ two-tenant isolation وRBAC وaudit/timeline smoke.
9. استبدال ngrok العشوائي بنطاق محجوز أو Cloudflare Tunnel ثابت، أو إثبات استقرار endpoint المعتمد.
10. تدوير/إلغاء بيانات الاعتماد التي ظهرت في المحادثة.
11. تحديث هذا السجل على SHA واحد ثم نجاح جميع CI workflows على ذلك الرأس.

## 8. شروط العودة إلى Ready

لا يعاد PR #567 من Draft إلى Ready إلا بعد إرفاق أدلة قابلة للتدقيق لكل بند في القسم 7، ثم تسجيل:

```text
BFF v1 anonymous: 401
BFF v2 anonymous: 401
Authenticated production smoke: PASS
Production PostgreSQL: VERIFIED
Flyway 20260718.1: SQL / SUCCESS on runtime DB
Tenant isolation: PASS
RBAC: PASS
Audit/timeline: PASS
Unexpected 5xx after final deployment: 0
New runtime error groups after final deployment: 0
Unresolved blockers: 0
```

بعدها فقط يتم الدمج باستخدام `expected_head_sha`، ثم يغلق Issue #563 يدويًا بعد التحقق من نشر merge SHA على Vercel.
