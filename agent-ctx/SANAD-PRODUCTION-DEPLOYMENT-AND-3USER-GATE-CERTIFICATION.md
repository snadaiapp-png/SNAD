# SANAD — Production Deployment & 3-User Gate Certification Report

**التاريخ:** 2026-10-28 (UTC)
**المنفذ:** وكيل التنفيذ الهندسي (جلسة أمر «إصلاح Backend الإنتاجي وإغلاق بوابة الإنتاج النهائية»)
**المرجع الشجري عند بدء التنفيذ:** `48322d0bae97082d69ee143b7af26962d6e14ef8` (دمج PR #1453 على `main`)

---

## 1. Executive Summary — النتيجة النهائية

| البند | الحالة |
|---|---|
| **المرحلة 1 — إصلاح اتصال Supabase + نشر Backend + إثبات الهوية** | **PASS** (مثبت بالأدلة أدناه) |
| **المرحلة 2 — Production 3-User Final Gate** | **BLOCKED** — بانتظار موافقة مراجعة بشرية لدمج PR #1454 (تحديث baseline) قبل تشغيل البوابة |
| **القرار النهائي** | `BLOCKED — REQUIRED EVIDENCE OR AUTHORIZATION MISSING` |

تم تحديد السبب الجذري لفشل الاتصال بالإنتاج وإصلاحه بدوران معتمد، ثم نُشرت نسخة الإنتاج عبر المسار الرسمي بالكامل (`production-release.yml`) واجتازت جميع حراسها، وتم إثبات هوية النسخة الحية والترحيلات وحالة RLS النهائية fail-closed بالفحص المستقل المباشر. لم تُشغَّل بوابة المستخدمين الثلاثة بعد، لأن تشغيلها المشروع يشترط دمج تحديث الـ baseline الذي يتطلب — بموجب حماية الفرع — موافقة مراجع بشري بصلاحية كتابة. لم يُعطَّل أي حارس، ولم يُتخطى أي فحص، ولم تُخفف أي شروط عزل.

---

## 2. Initial Evidence — الحالة التي بدأ منها التحقيق

1. **الخدمة في Render:** `srv-d8ragqkm0tmc73bviqq0` (sanad-backend، صورة env-runtime، خطة free، فرانكفورت).
   - آخر deployment ناجح قبل التدخل: `dep-dbb65vu7bikc73d79bog` — **live** منذ 2026-10-19T18:38Z بصورة `ghcr.io/snadaiapp-png/snad-backend:1d80c573305778e1c0d4453a47b72c9bd41ffd23`.
   - آخر deployment فاشل: `dep-dbgdd33l550s73blossg` — **update_failed** (2026-10-27T16:51Z) بنفس الصورة القديمة، trigger=manual.
2. **سجل فشل الإقلاع (منقّح):** أثناء تهيئة Flyway في النشر الفاشل:
   ```
   FlywayAutoConfiguration$FlywayConfiguration : Failed to instantiate [org.flywaydb.core.Flyway]:
   Factory method 'flyway' threw exception ... Could not get Connection for extracting meta-data
   Caused by: org.postgresql.util.PSQLException:
   FATAL: password authentication failed for user "postgres"
   ```
3. **بوابة الإنتاج:** run `40526704792` (رقم 261، workflow_dispatch على `4a264bce…`) فشلت في خطوة `Fail closed on baseline drift and resolve live production identity` برسالة:
   `Application source changed after the certified baseline; this gate is not authorized for that drift`
   — ولم تصل إلى أي اختبار رحلات مستخدمين، ولم يُنتج `workflow-production-3user-journey.json`.
4. **تاريخي:** الـ baseline المعتمد كان `01e77db21cf382b46d593e61f890144f83ddb639`؛ الفارق عنه حتى `48322d0b` = **261 commit** (449 ملف backend + 179 ملف web).
5. **PR #1453:** مدموج (2026-10-27T23:15Z) = التزام `main` المرجعي `48322d0b`، ويضمن تنفيذ بوابة RLS الوسيطة عبر PostgreSQL Direct.
6. **توكن Supabase المرفق أولًا كان غير صالح** (`Invalid access token`) — وُلّي بع توكن فعال من المالك.

## 3. Root Cause — السبب الجذري المثبت

**السبب:** كلمة مرور دور `postgres` المخزنة في متغير `SPRING_FLYWAY_PASSWORD` على Render أصبحت قديمة/ميتة بعد آخر اتصال Flyway ناجح (`2026-10-19T18:37:23Z` — صف `installed_by=postgres, rank 240` في `flyway_schema_history`)، بينما بيانات التطبيق التشغيلي (`sanad.tkbrvupemreqabwzdpyq` عبر `DATABASE_*`/`SPRING_DATASOURCE_*`) سليمة.

**إثبات ثلاثي المصادر:**
1. سجلات Render للنشر الفاشل (الاقتباس أعلاه) — الفشل تحديدًا في اتصال Flyway.
2. اختبار مصادقة حي عبر الـ session pooler (`aws-0-eu-central-1.pooler.supabase.com:5432`, sslmode=require):
   - `sanad.tkbrvupemreqabwzdpyq` + `DATABASE_PASSWORD` → **AUTH OK**
   - `postgres.tkbrvupemreqabwzdpyq` + `SPRING_FLYWAY_PASSWORD` → **AUTH FAIL** (نفس الرسالة الحية)
3. قاعدة الإنتاج عبر Management API query endpoint: 239 ترحيلًا ناجحًا/صفر فشل، آخر تطبيق بواسطة `postgres` في 2026-10-19T18:37Z.

**مصدر الحقيقة في الإعدادات (مثبت من الكود لا الافتراض):** `application-prod.yml` يستهلك:
```yaml
spring.datasource.url:      ${SPRING_DATASOURCE_URL:${DATABASE_URL:}}
spring.datasource.username: ${SPRING_DATASOURCE_USERNAME:${DATABASE_USERNAME:}}
spring.datasource.password: ${SPRING_DATASOURCE_PASSWORD:${DATABASE_PASSWORD:}}
```
أي أن `SPRING_DATASOURCE_*` يتقدم عند وجوده، و`DATABASE_*` fallback. أما Flyway فيستهلك `SPRING_FLYWAY_URL/USER/PASSWORD` عند تعريفها (وهي معرفة فعليًا في Render). القيم في Render كانت متسقة (pooler 5432، مستأجر `tkbrvupemreqabwzdpyq` = مشروع Supabase الإنتاجي «snad-prod-fresh») — فالمشكلة لم تكن في endpoint بل في كلمة `postgres` حصرًا.

**مسار الدوران المُعتمد:** `PATCH /v1/projects/tkbrvupemreqabwzdpyq/database/password` (Management API) → HTTP 200، ثم مزامنة `SPRING_FLYWAY_PASSWORD` في Render → HTTP 200، ثم تحقق حي: **AUTH OK لـ postgres ولـ sanad عبر الـ pooler**.

**المسارات المستبعدة بالأدلة قبل الوصول للحل:**
- `ALTER ROLE postgres …` عبر query endpoint → محجوب: «Only superusers can alter privileged roles» (دور محمي في Supabase).
- `GRANT postgres TO <role>` → محجوب: «Only roles with the ADMIN option on role postgres may grant this role».
- إنشاء دور ترحيل جديد بلا عضوية postgres → لا يملك الكائنات (كل 319 جدولًا و41 دالة مملوكة لـ `postgres`): «must be owner of table crm_cases».
- الدور المؤقت `cli_login_postgres` (endpoint الـ CLI الرسمي) → يفتقد صلاحيات ALTER على جداول postgres.
- أسرار GitHub للبيئة الإنتاجية: `PROD_DB_*` ثبتت ميتة (سجل فشل `check-flyway-history-v2` بتاريخ 2026-10-19: «tenant/user not found»)؛ `PRODUCTION_DATABASE_*`/`G1_*`/`SPRING_DATASOURCE_*` كلها لبيانات `sanad` (حسب توثيق `g1-credential-sync.yml`) وليست لـ postgres.

## 4. Changes Made — التغييرات والـ PRs

| التغيير | المعرف | الحالة |
|---|---|---|
| دوران كلمة postgres + مزامنة Render (تشغيلي — بلا أثر على الكود أو البيانات) | Management API PATCH + Render env PUT | منفذ ومُتحقق |
| تحديث baseline البوابة: `01e77db…` → `48322d0b…` (سطر واحد في `workflow-production-3user-final-gate.yml`) | **PR #1454** (فرع `chore/final-gate-baseline-48322d0b`، commit `eb7c23c2`) | **مفتوح — ينتظر موافقة مراجع بشري + اكتمال Maven** |
| هذا التقرير (وثيقة شهادة داخل `agent-ctx/`) | على فرع PR #1454 نفسه | يُدمج مع الموافقة |

لا توجد أي تعديلات على كود التطبيق، ولا على الترحيلات، ولا على الاختبارات، ولا على سياسات RLS.

## 5. Supabase Connectivity Proof — دليل الاتصال

- الاتصال الحي من بيئة التنفيذ عبر الـ pooler نفسه الذي يستخدمه الإنتاج (`aws-0-eu-central-1.pooler.supabase.com:5432`, `sslmode=require`):
  - `postgres.tkbrvupemreqabwzdpyq` (هوية Flyway الجديدة بعد الدوران): **AUTH OK**
  - `sanad.tkbrvupemreqabwzdpyq` (هوية التطبيق): **AUTH OK**
- إقلاع الإنتاج الفعلي: Flyway اتصال وطبّق الترحيلات (القسم 7) — دليل اتصال التطبيق بالقاعدة من داخل Render.
- `/actuator/health` → `{"status":"UP","groups":["liveness","readiness"]}` مع readiness/liveness → 200.
- سجلات ما بعد النشر تُظهر حركة مصادقة/تجديد نشطة بلا أي أخطاء PostgreSQL أو Flyway.
- لم تُطبع أي قيم سرية؛ الاستثناء الوحيد (echo من استجابة Render API لقيمة تم استبدالها فورًا بدوران إضافي) — القيمة المسربة مؤقتًا أصبحت غير صالحة.

## 6. Deployment Identity — هوية النسخة المنشورة

| العنصر | القيمة |
|---|---|
| الـ SHA المستهدف (head of main) | `48322d0bae97082d69ee143b7af26962d6e14ef8` |
| الصورة المنشورة | `ghcr.io/snadaiapp-png/snad-backend:48322d0bae97082d69ee143b7af26962d6e14ef8` (build+attestation: run `40600843622`) |
| مسار النشر | `production-release.yml` — run **[40606872746](https://github.com/snadaiapp-png/SNAD/actions/runs/40606872746)** (SUCCESS) |
| Render deployment | `dep-dbgjqbjl550s73cnln40` — status **live** (finished 2026-10-28T00:12:22Z)، `image.ref` مطابق حرفيًا |
| السابقة الحية (أُلغيت تلقائيًا) | `dep-dbb65vu7bikc73d79bog` → deactivated (صورة `1d80c573…`) |
| هوية Vercel الإنتاجية | `/api/system/release` = `{"commitSha":"48322d0b…","environment":"production"}` بعد مصالحة run [40605953267](https://github.com/snadaiapp-png/SNAD/actions/runs/40605953267) |
| CI على الـ SHA المستهدف | كل الفحوص success (JOB A–F، PostgreSQL Direct، HRM security/RLS، Maven Test Suite، CRM، Users، الفحوص الأمنية) |
| Probes مباشرة بعد النشر | health/readiness/liveness = 200 UP؛ `/actuator/env` = 404؛ `/swagger-ui.html` = 404؛ `/v3/api-docs` = 404 |

## 7. Flyway and RLS — الترحيلات والحالة النهائية

- **سجل Flyway قبل النشر:** 239 نجاحًا، 0 فشل، آخر تطبيق `20261019.1` بواسطة `postgres` (2026-10-19T18:37Z). صف الإصدار `15` محذوف رسميًا بعلامة `DELETE` (rank 137) — «orphan» موثق لا يخالف validate (مُثبت بإقلاع 19 أكتوبر الناجح).
- **الترحيلات المعلقة الثلاثة طُبقت بواسطة Flyway نفسه عند إقلاع النسخة الجديدة (لا تطبيق يدوي):**
  | rank | version | script | installed_by | installed_on |
  |---|---|---|---|---|
  | 241 | 20261020.1 | V20261020_1__supabase_data_api_default_deny.sql | postgres | 2026-10-28T00:10:49Z |
  | 242 | 20261020.2 | V20261020_2__enable_post_baseline_crm_rls.sql | postgres | 2026-10-28T00:10:50Z |
  | 243 | 20261021.1 | V20261021_1__crm_post_baseline_fail_closed_rls.sql | postgres | 2026-10-28T00:10:51Z |
  المجموع بعد النشر: 242 نجاحًا / **0 فشل**.
- **الحالة الوسيطة (`20261020.2`):** مُغطاة باختبارات PostgreSQL Direct الوسيطة التي جعلها PR #1453 تُنفذ صراحة في CI (نجحت على `48322d0b` قبل النشر). ملاحظة تشخيصية: الجداول التسعة كانت تحمل RLS + سياسة `tenant_isolation` مسبقًا (تطبيق سابق خارج سجل Flyway) — ما جعل تطبيق `20261020.2` فعليًا idempotent (DROP IF EXISTS + CREATE بنفس النص) دون أي كسر.
- **الحالة النهائية (`20261021.1`) مُتحقق منها مباشرة على الإنتاج:** الجداول التسعة كلها `relrowsecurity=true` **و** `relforcerowsecurity=true` مع سياسة `tenant_isolation` واحدة لكل جدول (USING/WITH CHECK تتطلب `app.tenant_id` غير فارغ ومطابقًا):
  `crm_capacity_plans, crm_cases, crm_email_logs, crm_service_assignments, crm_shift_assignments, crm_shift_templates, crm_staff_availability, crm_staff_skills, crm_workload_assignments`
- **صلاحيات الأدوار (حية):** دور التشغيل `sanad` = `rolsuper=false`, `rolbypassrls=false` (بلا تجاوز RLS)؛ `postgres` = `rolsuper=false` لكن `rolbypassrls=true` (يُستخدم للترحيلات فقط، لا للتشغيل). Data API (`anon`/`authenticated`) في وضع default-deny بعد `20261020.1`.
- **خطة الاستعادة:** المشروع على خطة بلا نسخ مجدولة ولا PITR (`backups:[]`, `pitr_enabled:false`) — **مخاطرة مسجلة**. عُوّض جزئيًا بالتقاط حالة ما قبل النشر كاملة (سياسات/أعلام/أعداد صفوف — `pre-deploy-RLS-state`)، وبأن الترحيلات الثلاثة forward-only ذرّية (DO block واحد لكل ملف) وقابلة للعكس بـ SQL موثق إن لزم، وباجتياز اختبارات CI للحالتين الوسيطة والنهائية قبل النشر.

## 8. Test Evidence — ملخص الاختبارات المنفذة

| الاختبار/الفحص | المنفذ الفعلي | البيئة/الهدف | SHA | النتيجة |
|---|---|---|---|---|
| مصادقة pooler — هوية Flyway | psycopg2 عبر `aws-0-eu-central-1.pooler…:5432` | إنتاج (نفس endpoint Render) | — | PASS بعد الدوران (FAIL قبله — دليل السبب الجذري) |
| مصادقة pooler — هوية التطبيق sanad | psycopg2 | إنتاج | — | PASS |
| CI كامل على `48322d0b` | GitHub Actions (JOB A–F، PostgreSQL Direct، HRM RLS، Maven، CRM، Users، scans) | CI | 48322d0b | PASS (كلها success) |
| النشر الرسمي وحراسه | `production-release.yml` run 40606872746 | إنتاج | 48322d0b | PASS (جميع الخطوات) |
| هوية الصورة الحية | Render API: `dep-dbgjqbjl550s73cnln40.image.ref` | إنتاج | 48322d0b | PASS (مطابقة حرفية) |
| Flyway runtime invariants + تاريخ الإنتاج | خطوات workflow + استعلام مباشر | إنتاج | 48322d0b | PASS (241–243 applied، 0 failed) |
| الحالة النهائية fail-closed RLS | استعلام `pg_class`/`pg_policies` مباشر | إنتاج | 48322d0b | PASS (9/9 FORCE RLS) |
| فحوص أمنية حية (env/swagger) | curl على `sanad-backend-mcrj.onrender.com` | إنتاج | 48322d0b | PASS (404×3) |
| سجلات ما بعد النشر | Render logs API (نافذة 40 دقيقة) | إنتاج | 48322d0b | PASS (لا أخطاء PG/Flyway/إقلاع) |
| بوابة 3-User Final Gate | — | إنتاج | — | **NOT RUN — BLOCKED (قسم 9)** |

ملاحظة التزام: لم تُشغَّل أي مجموعة Maven محليًا أو أمر قد ينفذ `Flyway.clean()`؛ الترحيلات طُبقت عبر إقلاع النسخة الرسمية نفسها.

## 9. Production Gate — البوابة

**لم تُشغَّل البوابة بعد.** أسباب وآلية الفتح:

1. كل الحراس السابقون لخطوة baseline اجتازوا بشرعية: هوية Vercel = main، هوية صورة Render الحية = سلف لـ main ومطابق لشجرة backend، متغيرات Render مكتملة.
2. حارس baseline (الفارق عن `01e77db…` في `apps/`) مشروع ومطلوب — لا يُعطَّل. المعالجة الصحيحة حسب تعليمات الأمر: تغيير منفصل قابل للمراجعة يبرر التحديث بالأدلة → **PR #1454** جاهز بوصف كامل وجدول أدلة.
3. حماية الفرع على `main` تطلب: اكتمال فحص `Maven Test Suite` على فرع الـ PR (قيد التشغيل) + **موافقة مراجع واحد بصلاحية كتابة** — لا يمكن للوكيل الموافقة على PR أنشأه، ولا يجوز تجاوز شرط المراجعة.
4. عند الموافقة والدمج: يصبح head جديدًا على main ⇒ يلزم إعادة مصالحة Vercel إلى الـ head الجديد (`vercel-main-production-reconcile.yml` — تشغيل واحد)، ثم تشغيل البوابة `workflow-production-3user-final-gate.yml` يدويًا على الـ head الجديد. عندها يعبر حارس baseline بصورة مشروعة (المرجع المعتمد = الـ head المدموج المدعوم بالأدلة) وتُنفذ رحلات المستخدمين الثلاثة وتُنتج `workflow-production-3user-journey.json`.

## 10. Artifacts — روابط الأدلة

- النشر الرسمي: https://github.com/snadaiapp-png/SNAD/actions/runs/40606872746 (يتضمن artifacts: `production-release-40606872746` مع `production-release-evidence.json`, `render-deployment.json`, `scp-contract-smoke.json`, `r0c13-g11-runtime-evidence.json`, ونتائج G2 visual).
- بناء الصورة الموثقة: https://github.com/snadaiapp-png/SNAD/actions/runs/40600843622
- مصالحة Vercel: https://github.com/snadaiapp-png/SNAD/actions/runs/40605953267
- فشل البوابة التاريخي: https://github.com/snadaiapp-png/SNAD/actions/runs/40526704792
- فشل النشر في Render: `dep-dbgdd33l550s73blossg` (سجلات Render API — منقحة في القسم 2)
- PR الـ baseline والتقرير: https://github.com/snadaiapp-png/SNAD/pull/1454
- أدلة محلية (بيئة التنفيذ): `evidence/render-logs-failed-deploy.json`, `evidence/pre-deploy-rls-state.json`, `evidence/flyway-drift.json`, `evidence/backend-probes.txt`, `evidence/render-logs-post-deploy.json`

## 11. Outstanding Risks — المخاطر والقيود غير المحسومة

1. **[BLOCKER للبوابة]** دمج PR #1454 يتطلب موافقة بشرية — بعد الموافقة: مصالحة Vercel للـ head الجديد ثم تشغيل البوابة (خطوات جاهزة، لا تحتاج أي تغيير إضافي).
2. **[HIGH]** لا توجد نسخ احتياطية مجدولة ولا PITR على مشروع Supabase الإنتاجي (الخطة الحالية) — يوصى بترقية الخطة أو جدولة `pg_dump` خارجي دوري.
3. **[MEDIUM]** كلمة دور `postgres` أدارتها هذه الجلسة عبر Management API؛ يوصى (للمالك) بمراجعتها في لوحة Supabase وتحديث أي مستهلك خارجي قديم (مسارات GitHub `PROD_DB_*` ثبتت ميتة سلفًا وتحتاج إعادة ضبط أو إزالة).
4. **[MEDIUM]** ترحيلات DDL مستقبلية ستفشل لو توقفت هوية `postgres` مجددًا — يوصى برصد تنبيه على فشل إقلاع Render، وإبقاء `SPRING_FLYWAY_*` تحت المراقبة.
5. **[LOW]** حادثة طباعة مؤقتة لقيمة كلمة مرور أثناء مزامنة Render (echo من الاستجابة) — عولجت فورًا بدوران إضافي؛ القيمة القديمة غير صالحة.
6. **[NOTE]** الجداول التسعة كانت تحمل سياساتها مسبقًا خارج سجل Flyway (تطبيق يدوي تاريخي) — الترحيلات طبتها وأصبحت الحالة موثقة في سجل Flyway؛ يوصى بمنع التطبيق اليدوي المباشر مستقبلًا.

## 12. Final Decision

```
BLOCKED — REQUIRED EVIDENCE OR AUTHORIZATION MISSING
```

**التبرير:** المرحلة الأولى (الاتصال + النشر + الهوية + Flyway + RLS + الفحوص الأمنية) مكتملة بقرارات PASS مثبتة. شرط واحد فقط يمنع إعلان `PASS — PRODUCTION GATE CERTIFIED`: تشغيل بوابة المستخدمين الثلاثة الفعلية، وهو محجوب بموافقة مراجع بشرية إلزامية لدمج PR #1454 (تحديث baseline المُبرَّر بالأدلة). لا توجد اختبارات حقيقية أُستبدلت بمحاكاة، ولا حارس أُعطّل، ولا ادعاء إصلاح كامل قبل اكتمال الأدلة.
