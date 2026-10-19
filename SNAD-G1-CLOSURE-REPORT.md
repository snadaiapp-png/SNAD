# SNAD-G1-CLOSURE-REPORT

**رقم الأمر:** SNAD-G1-EXEC-CLOSE-001
**التاريخ:** 2026-10-19 (الإصدار الختامي)
**المنفّذ:** وكيل منصة Z التنفيذي
**المستودع:** `snadaiapp-png/SNAD` — الفرع `main`
**إصدار هذا المستند:** **v2 (الختامي)** — يحدّث v1 بعد إتمام السلسلة التنفيذية كاملة بأدلة حية.

---

## 1) الملخص التنفيذي وقرار الإغلاق

**القرار النهائي: `CLOSED` — بإثبات حي كامل.**

سلسلة الإغلاق اكتملت بتاريخ 2026-10-19 على SHA النهائي `1d80c573305778e1c0d4453a47b72c9bd41ffd23` (= دمج PR #1417 المحوكم):

1. **مصدر البيانات مؤكَّد** (سلسلة أدلة متقاطعة، القسم 3).
2. **RC-1 (انجراف الاعتماد المشترك) مُصلَح فعليًا**: مزامنة `g1-credential-sync` (run `39276700331` = SUCCESS) حدّثت `SPRING_DATASOURCE_PASSWORD` و`PRODUCTION_DATABASE_PASSWORD` في النطاقين (4 كتابات HTTP 204، القيم لا تُطبع أبدًا)، والتحقق اللاحق `AUTH_OK` لكل المرشحين.
3. **RC-2 (غياب RLS على `public.users`) مُصلَح فعليًا في الإنتاج**: النشر الحي طبّق الترحيلات الثلاثة (رتب Flyway 238–240 كـ `postgres`، success)، وأصبح `users` RLS مفعّلًا + **FORCE** مع السياسات المغلقة.
4. **الشهادة الحية #27 (run `39306892699`) = `G1_CERTIFICATION=PASS`** — 34 فحصًا كلها PASS، بما فيها مصفوفة عزل كاملة بصفوف أدلة غير صفرية عبر (`crm_accounts`, `hr_employees`, `users`) للمستأجرين A/B مع `all_visible_rows_match_context`.
5. **الإنتاج الحي**: Vercel web + Render backend يخدمان بالضبط `main@1d80c573` (تحقق حي مستقل عبر `/api/system/release`).

قواعد الحماية ظلت قائمة طوال التنفيذ: لا قيم أسرار في أي دليل، فشل مغلق عند كل عائق (staging، النشر #1/#2/#3، الشهادة #25/#26)، وكل تغيير قاعدة بيانات تم بعد إثبات مصدر البيانات وبموافقة موكلة صريحة من المالك.

## 2) الحالة قبل المعالجة وبعدها

| البند | قبل المعالجة (مثبت) | بعد المعالجة (مثبت حيًا) |
|---|---|---|
| `SPRING_DATASOURCE_PASSWORD` (النطاقان) | `AUTH_FAIL` (تشغيل #7/#8) | متزامن (`g1-credential-sync` run 39276700331، 4×HTTP 204) ثم `AUTH_OK` |
| `PRODUCTION_DATABASE_PASSWORD` (النطاقان) | `AUTH_FAIL` | متزامن ثم `AUTH_OK` (forensics على 1d80c573) |
| `G1_DATASOURCE_*` (بيئة production) | سليم (مصدر الحقيقة) | دون تغيير — `sanad.<ref>`، `rolsuper=false`، `rolbypassrls=false` |
| `public.users` | RLS معطّل، بلا سياسات، المالك `postgres` (شهادة #24) | **RLS مفعّل + FORCE**؛ سياسة `users_tenant_isolation TO sanad` + `users_tenant_isolation_runtime TO postgres` (رتبة 238/240) |
| `login_tenant_index` + `g1_resolve_login_tenants` | غير موجودين | موجودان (DDL مثبّت SHA256 `441a6e60…` + backfill 36 صفًا؛ رتبة 239)؛ EXECUTE لـ `sanad` حصريًا، مُسح من `PUBLIC` |
| صورة Render الحية | نشر 2026-10-12 (صورة قديمة) | **`ghcr.io/snadaiapp-png/snad-backend:1d80c573…` حيّة** (deploy `dep-dbb65vu7bikc73d79b`، live 18:38:47Z) |
| Vercel production web | كانت تخدم `9a35fa5a` (عائق الإصدار) | تخدم `1d80c573` (reconcile #104 = run 39278726702 SUCCESS بعد موافقة المالك) |
| ترحيلات Flyway الإنتاجية | الرتبة 237 = `20261010.99` | **الرتبة 240 = `20261019.1`** (238–240 طبّقت بنجاح كـ `postgres`) |
| هوية Flyway الإنتاجية | `SPRING_FLYWAY_USER=sanad.<ref>` (انحراف يمنع DDL المالك) | **`postgres.<ref>`** (مطابق للتصميم الموثق في V20261019_1) — الـ runtime بقي `sanad` (أقل امتياز) |
| شهادة العزل الحية | #24: فشل وحيد `users_rls_enabled`، لم تصل لفحوص الأدلة | **#27: PASS كامل (34 فحصًا)** |

## 3) إثبات مصدر البيانات الفعلي (المرحلة 1)

سلسلة الإثبات (v1 §3) ظلت قائمة وصحيحة، وأُضيف إثبات ختامي:
- الشهادة #27 اتصلت بنفس المشروع `tkbrvupemreqabwzdpyq` عبر `aws-0-eu-central-1.pooler.supabase.com:5432` باسم `sanad.tkbrvupemreqabwzdpyq` (`current_user=sanad`).
- الطباعة الجنائية على `1d80c573` (run `39276879531`) أثبتت `AUTH_OK+ROLE_OK` للمشاركين عبر pooler-session-5432 وpooler-txn-6543.
- بصمة Flyway بعد النشر (رتب 238–240) مرصودة عبر قناتين مستقلتين (قناة الشهادة + قناة المالك المقروءة فقط) — تعزيزًا لمبدأ "التطبيق المنشور = قاعدة الشهادة".
- القيد البيئي الموثق باقٍ: `direct-5432 = network_unreachable_or_dns` (IPv6 فقط) — قيد منصة وليس عيب اعتماد.

## 4) سجل الإجراءات (10–16: تنفيذ الإغلاق)

| # | الإجراء | الأداة/النظام | التاريخ (UTC) | النتيجة | الدليل |
|---|---|---|---|---|---|
| 10 | المزامنة الفعلية للاعتماد المشترك | `g1-credential-sync.yml` (run 39276700331) | 2026-10-19 15:17 | **SUCCESS** — 4×HTTP 204 | `evidence/sync_ev.zip` |
| 11a | إثبات forensics على SHA النهائي | `g1-datasource-forensics.yml` (run 39276879531) | 2026-10-19 15:2x | SUCCESS — AUTH_OK/ROLE_OK على 3 نقاط | `evidence/fx_12198416370/` |
| 11b | تهيئة الفهرس عبر القناة الرسمية | `g1-stage-preauth-index.yml` (run 39277156855) | 2026-10-19 15:2x | FAIL-CLOSED (لا PAT صالح؛ لا owner pair) — بلا أي تطبيق | `evidence/stage_x/` |
| 11c | تهيئة الفهرس + الباكفيل (قناة موكلة من المالك، DDL مثبّت SHA256) | `stage_owner_delegated.py` | 2026-10-19 18:02 | **PASS** — 36 صفًا، 0 mismatches | `evidence/stage_owner_delegated.json` |
| 12 | نشر صورة SHA النهائي (4 محاولات محوكم) | `production-release.yml` runs 39299348934/39301081085/39302681832/**39303712256** | 2026-10-19 18:03–18:42 | **SUCCESS** (المحاولة 4)؛ الفشلان 1–2 بجذر موثق (هوية Flyway) وrollback ناجح | سجلات runs + `evidence/watch_release.log` |
| 12b | إصلاح هوية Flyway على Render | Render env-vars API (PUT موثق) | 2026-10-19 18:28 | متغيران فقط تغيّرا: `SPRING_FLYWAY_USER=postgres.<ref>` + كلمة مروره | مقارنة قبل/بعد (48 متغيرًا دون تغيير) |
| 13 | إعادة الشهادة الحية على الإنتاج المنشور | `g1-postgres-live-security-cert.yml` #25/#26/#27 | 2026-10-19 18:46–18:57 | #27 = **PASS كامل** | `evidence/g1-postgres-rls-evidence.json` (#27) |
| 13b | تهيئة صفوف أدلة الشهادة (إضافي فقط) | `seed_cert_evidence.py` + `seed_hr_evidence.py` | 2026-10-19 18:5x | 2 حسابات/مستأجر + 2 موظفين/مستأجر A (B كان فيه 3) | `evidence/cert_*_seed.json` |
| 13c | تحقق حي مستقل من هوية الإصدار | `/api/system/release` + Render API + GitHub HTML | 2026-10-19 | web=1d80c573/production؛ backend live على الصورة المطابقة | `evidence/reconcile_104_verification.json` |

## 5) أدلة G1 المنقّحة وبصماتها

- الطباعة الجنائية: `g1-forensics-*-39223217596-1` (12172791212/12173010627)، و`g1-forensics-*-39236371817-1` (12179592269/12179306499)، وعلى SHA النهائي (run 39276879531).
- الشهادات: #24 (artifact 12178539657 — FAIL وحيد users_rls_enabled)، **#27 (دليل PASS كامل — مؤرشف ومرفوع بهذا الـ PR)**.
- المزامنة: `g1-credential-sync-evidence.json` + `sync_ev.zip`.
- الـ staging: `stage_owner_delegated.json` (يشمل SHA256 للـ DDL المنفذ = `441a6e60c05a6e699dfed0bad6c4c15b433b13db64de723cf935667a177160f6` و`backfill_sha256=78c9a982…`).
- الإصدار: `release_dispatch.json` + run 39303712256 (artifact production-release-39303712256).
- الأدلة الحيّة محفوظة في مستودع التنفيذ `scripts/g1_chain/evidence/` وتُرفق بهذا الـ PR حيثما أمكن دون أي قيمة سرية.

## 6) تدقيق هوية PostgreSQL وRLS والصلاحيات (الشهادة #27 النهائية)

- `supabase_endpoint`، `ssl_required`، `least_privilege_secret_user`، `runtime_role_is_sanad`، `runtime_cannot_bypass_rls`: **PASS**.
- المستأجرون A/B/CONTROL: موجودة/ACTIVE/مختلفة: **PASS**.
- `users`: **RLS enabled + FORCE** وينطبق على زمن التشغيل: **PASS** (سائق الفشل التاريخي زال).
- `crm_accounts`، `hr_employees`: RLS + انطباق على التشغيل: **PASS**.
- عقد الدور: `sanad` بلا `SUPERUSER`/`BYPASSRLS` — **PASS** (بالشهادة والطباعة واختبار SET ROLE السلبي المدموج بـ #1416).

## 7) اختبارات العزل والأمن (مصفوفة #27 الكاملة)

| الجدول | المستأجر A (صفوف) | المستأجر B (صفوف) | `all_visible_rows_match_context` |
|---|---|---|---|
| `crm_accounts` | 2 PASS | 2 PASS | PASS (A وB) |
| `hr_employees` | 2 PASS | 3 PASS | PASS (A وB) |
| `users` | 1 PASS | 5 PASS | PASS (A وB) |

- مسار ما قبل المصادقة حي ومُختبَر بالمصادقة الفعلية: دخول G2 production visual smoke ناجح عبر `PreAuthTenantResolver` → `g1_resolve_login_tenants` → سياق مستأجر موثوق.
- فحص المالك المعياري في مستأجر CONTROL: **PASS**.

## 8) المخاطر المتبقية (سجل معلوم ومُدار)

1. **دور `postgres` يحمل `rolbypassrls=true` (حقيقة منصة Supabase):** الـ runtime الحالي يتصل بـ `sanad` (مقيد، خاضع للسياسة)، بينما Flyway يتصل بـ `postgres` لتطبيق DDL المالك فقط. تبديل كامل لمسار البيانات إلى دور غير مالك يبقى برنامجًا تاليًا (كما في v1 §8-2).
2. **قنوات الإدارة:** `SUPABASE_ACCESS_TOKEN`/`SUPABASE_PAT` المخزنة ما زالت 401 (ثلاثة توكنات متتالية رفضها الـ API) — التهيئة تمت عبر قناة المالك الموكلة الموثقة؛ يوصى بتدوير PAT صالح لتفعيل `g1-stage-preauth-index.yml` آليًا مستقبلًا (سير العمل يفشل مغلقًا حتى ذلك الحين).
3. **`PROD_DB_*` (مسار الترحيل اليدوي القديم):** ما زال مكسورًا (مستأجر Supavisor غير موجود) — يوصى بحذفه بعد الإغلاق.
4. **`Z_GITHUB_TOKEN`:** لم يُختبر باستقلال في هذه السلسلة (بديل PAT المالك استُخدم لكل العمليات) — يبقى على قائمة التدوير.
5. **صفوف أدلة الشهادة:** أُنشئت صفوف إضافية موسومة `G1_CERT` للمستأجرين A/B (إضافي فقط، بلا مساس بأي صف قائم) — تُدار كـ fixtures معتمدة للشهادة الدورية.

## 9) قرار الإغلاق

**`CLOSED`.**

شروط القبول استُوفيت بأدلة حية: إثبات مصدر البيانات، مزامنة الاعتماد (RC-1)، تفعيل RLS + السياسات + المُحلّل (RC-2)، مصفوفة عزل كاملة غير فارغة (الشهادة #27 = PASS)، إصدار إنتاج محوكم مطابق للـ SHA النهائي على الويب والـ backend مع rollback مُختبَر فعليًا (3 مرات fail-closed ناجحة أثناء الحل)، وتقرير إغلاق v2 عبر تغيير مراجَع.

## 10) توصيات منع التكرار والمراقبة

1. تدوير `SUPABASE_PAT` بصلاحية Management API وتخزينه في السر لتشغيل التهيئة آليًا لاحقًا.
2. فحص دوري أسبوعي (forensics + الشهادة) عبر `workflow_dispatch` مجدول مع تنبيه عند أي تحول في الأحكام.
3. جعل كل تدوير اعتماد يُحدّث كل المستهلكين في نفس التشغيل (تم لأجل RC-1 عبر `g1-credential-sync`)، ومراقبة `flyway_schema_history` بعد كل نشر (الفارق المتوقع = عدد الترحيلات الجديدة).
4. التحقق من هوية Flyway (`SPRING_FLYWAY_USER`) عند أي تغيير بيئة Render — الانحراف إلى `sanad` كان سبب فشلَي النشر الأولين.
5. برنامج تالٍ (خارج هذا الإغلاق): تبديل مسار البيانات بالكامل إلى دور غير مالك، وتدوير `sanad` عبر القناة المعتمدة، وحذف مسار `PROD_DB_*` القديم.
