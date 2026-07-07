
---
Task ID: production-closure-attempt
Agent: main (Super Z)
Task: تنفيذ الإغلاق الإنتاجي بعد دمج PR #244

Work Log:
- اكتشاف دمج PR #244 بنجاح:
  * Merge SHA: 88beddedcf6bda1a621213f1bdcfb4be57fdc4c4
  * origin/main تقدّم من ee1d18fd إلى 88bedde
  * فرع fix/commercial-go-live-hardening-20260705 حُذف (squash + delete branch)
- تحديث main المحلي لمطابقة origin/main
- الحصول على GitHub token عبر device flow (snadaiapp-png account)
- فحص main branch post-merge checks:
  * ✅ Render Blueprint Validation (success)
  * ✅ Stage 07 Artifact Provenance (success)
  * ✅ SNAD Identity Governance (success)
  * ✅ Web CI (success)
  * ❌ Production Smoke (failure — Vercel propagation delay)
  * ❌ NVD Database Maintenance (failure — external NVD feed issue)
  * ❌ NVD Feed Mirror Publisher (failure — external NVD feed issue)
- تشغيل Executive Health Production Verification workflow:
  * Health Run ID: 28759733288
  * النتيجة: FAILURE
- السبب الجوهري للفشل:
  * الـ workflow يتطلب 6 GitHub Secrets:
    - SANAD_ADMIN_EMAIL (مفقود)
    - SANAD_ADMIN_PASSWORD (موجود)
    - CONTROL_PLANE_TENANT_ID (مفقود)
    - CONTROL_PLANE_NON_ADMIN_EMAIL (مفقود)
    - CONTROL_PLANE_NON_ADMIN_PASSWORD (مفقود)
    - CONTROL_PLANE_NON_ADMIN_TENANT_ID (مفقود)
  * 5 من 6 secrets مفقودة
  * PR #244 أدخل naming convention جديد للـ secrets لم تُحدّث في GitHub
- إنشاء HEALTH-RUN-FAILURE-REPORT.md يوثّق:
  * السبب الفعلي للفشل
  * الـ secrets المطلوبة
  * الإجراء التصحيحي المطلوب
- تنظيف GitHub token فوراً

Stage Summary:
- فشل التنفيذ عند البوابة §13 — Executive Health Production Verification
- السبب: 5 من 6 GitHub Secrets مفقودة (PR #244 يستخدم naming convention جديد)
- النتيجة: GO SUSPENDED — يتطلب إضافة GitHub Secrets ثم إعادة تشغيل الـ workflow
- PR #244 = MERGED بنجاح، لكن الإغلاق الإنتاجي لم يكتمل
- الإجراء التصحيحي:
  1. إضافة الـ 5 secrets المفقودة إلى GitHub
  2. إعادة تشغيل health-production-verification.yml
  3. بعد نجاح Health، تشغيل commercial-go-live.yml

---
Task ID: p0-2-frontend-smoke-route-fix
Agent: main (Super Z)
Task: تصحيح Frontend Smoke Route واستكمال أدلة Post-Merge (أمر الإغلاق النهائي لـ P0-2)

Work Log:
- قراءة أمر PM التنفيذي (18 قسمًا) لتصحيح P0-2 دون تجاوز أي بوابة
- تشخيص السبب الجذري الحقيقي لفشل Frontend Smoke: مسار `/auth/login` غير موجود في App Router من Next.js — المسار الفعلي لتسجيل الدخول هو الجذر `/` (يُعرض `<AuthEntry />` → `<LoginScreen />` عند غياب الجلسة). كل تحقيقات PRs #351–#355 طالت المهلة (60s→180s→300s→600s) لكن المشكلة لم تكن في المهلة بل في المسار
- إنشاء فرع `fix/p0-2-correct-frontend-smoke-route` من `origin/main` (SHA 4f38807f9c102579a8f824e754bfe936accfe6da) بعد `git reset --hard` و `git clean -ffdx`
- تعديل `.github/workflows/post-merge-verification.yml`:
  * تعريف `FRONTEND_SMOKE_URL="http://127.0.0.1:3001/"` مرة واحدة في خطوة `smoke-frontend`
  * استخدام نفس المتغير في: readiness probe, final fetch, validator --url (3 تكرارات `$FRONTEND_SMOKE_URL`)
  * إزالة كل ظهور لـ `/auth/login` من خطوة smoke-frontend (حتى من التعليقات)
  * إعادة المهلة إلى 180 ثانية (60 تكرار × 3 ثوانٍ نوم) بدلاً من 600 ثانية
  * الإبقاء على فحص حياة العملية `kill -0 "$FRONTEND_PID"` مع رسالة `PROCESS_EXITED`
  * الإبقاء على أمر التشغيل `NEXT_TELEMETRY_DISABLED=1 NODE_ENV=production ./node_modules/.bin/next start -H 127.0.0.1 -p 3001`
  * إضافة `backend-smoke-metadata.json` إلى artifact رفع أدلة backend (مع `if-no-files-found: error` و retention 90 يومًا)
  * إضافة `frontend-smoke-metadata.json` إلى artifact رفع أدلة frontend (مع `if-no-files-found: error` و retention 90 يومًا)
  * إعادة تسمية artifacts من `backend-smoke-log-*` و `frontend-smoke-log-*` إلى `backend-smoke-evidence-*` و `frontend-smoke-evidence-*`
- إنشاء `scripts/ci/validate_post_merge_evidence.py` — المدقق المستقل للبوابة النهائية (238 سطرًا):
  * يقرأ ملفات JSON الفعلية (verification-manifest, secret-scan-report, backend-smoke-metadata, backend-health, frontend-smoke-metadata)
  * يتحقق من: SHA == github.sha, run-id == github.run_id
  * يتحقق من: manifest.result == PASS, no critical check skipped/cancelled/failed
  * يتحقق من: secret.result == PASS, findingsCount == 0, scanErrors == 0
  * يتحقق من: backend-metadata.result == PASS, httpStatus == 200
  * يتحقق من: backend-health.status == UP
  * يتحقق من: frontend-metadata.result == PASS, brandNamePresent == true, url must NOT contain /auth/login
  * يُخرج exit 1 عند أي انتهاك، exit 0 فقط عند اجتياز كل التحققات
- تعديل خطوة Final Gate في الـ workflow لاستدعاء `validate_post_merge_evidence.py` مع `--expected-sha` و `--expected-run-id`
- إنشاء `tests/ci/test_post_merge_frontend_route.py` — 9 اختبارات ارتداد (regression) تفحص YAML الـ workflow مباشرة:
  * لا يوجد `/auth/login` في خطوة smoke-frontend
  * `FRONTEND_SMOKE_URL="http://127.0.0.1:3001/"` معرف مرة واحدة
  * نفس المتغير مُستخدم في ≥3 مواضع (readiness + fetch + validator)
  * المهلة لا تتجاوز 180 ثانية (60 × 3)
  * رسالة الفشل تقول `180s (STARTUP_TIMEOUT)` وليس 600s
  * فحص `kill -0` موجود مع `PROCESS_EXITED`
  * `frontend-smoke-metadata.json` مُضمّن في artifact الـ frontend مع `if-no-files-found: error`
  * `backend-smoke-metadata.json` مُضمّن في artifact الـ backend مع `if-no-files-found: error`
  * Final gate يستدعي `validate_post_merge_evidence.py` مع `--expected-sha` و `--expected-run-id`
  * اختبار تحكم سلبي (negative control): workflow مُسمم بـ `/auth/login` يفشل الاختبار
- إنشاء `tests/ci/test_validate_post_merge_evidence.py` — 22 اختبارًا للمدقق المستقل:
  * Happy path (كل الأدلة صحيحة) → exit 0
  * ملفات مفقودة (manifest, secret, backend-meta, backend-health, frontend-meta) → exit 1
  * JSON تالف أو فارغ → exit 1
  * عدم تطابق SHA في manifest أو في secret → exit 1
  * عدم تطابق run-id → exit 1
  * manifest.result = FAIL → exit 1
  * backend-metadata.result = FAIL → exit 1
  * backend-health.status != UP → exit 1
  * frontend-metadata.result = FAIL → exit 1
  * frontend-metadata.url يحتوي على /auth/login → exit 1
  * frontend-metadata.brandNamePresent = false → exit 1
  * secret.findingsCount > 0 → exit 1
  * secret.scanErrors غير فارغ → exit 1
  * critical check مُخطّى (SKIPPED) → exit 1
  * critical check فاشل (FAILURE) → exit 1
  * critical check مفقود من manifest.checks → exit 1
- توسيع `tests/ci/test_validate_frontend_smoke.py` من 6 إلى 10 اختبارات:
  * 200 + SNAD → PASS (root route)
  * 200 + سند → PASS (root route)
  * 302 redirect → PASS
  * 404 → FAIL (UNEXPECTED_HTTP_STATUS) — هذا هو الاختبار الذي يمنع تكرار الخطأ
  * 500 → FAIL (HTTP_5XX)
  * missing identity → FAIL (BRAND_IDENTITY_MISSING)
  * missing HTML file → FAIL
  * empty HTML file → FAIL
  * non-numeric status → FAIL (INVALID_HTTP_STATUS)
  * metadata contract: url + port + processStarted مُسجّلة
- تنفيذ جميع الفحوص ما قبل الدمج محليًا:
  * YAML parse: OK (post-merge-verification.yml)
  * Python compile: OK (5 scripts)
  * Workflow security validation: 46/46 workflows PASS, 0 violations
  * Secret scan: 1752 files scanned, 0 findings, 0 scan errors, result=PASS
  * CI unit tests: 124/124 PASS (9 regression + 22 evidence validator + 10 frontend smoke + 9 backend smoke + 31 secret scanner + ... )
  * التحقق الهيكلي من الـ workflow بالـ YAML parser: smoke-frontend لا يحتوي /auth/login، FRONTEND_SMOKE_URL معرف، seq 1 60، final gate يستدعي validator، artifacts تشمل metadata
- عمل commit على الفرع `fix/p0-2-correct-frontend-smoke-route` (SHA 567204e) — 5 ملفات، 814 إدراج، 29 حذف

Stage Summary:
- الملفات المُنشأة:
  - `scripts/ci/validate_post_merge_evidence.py` (238 سطر) — مدقق الأدلة المستقل
  - `tests/ci/test_post_merge_frontend_route.py` (185 سطر) — 9 اختبارات ارتاد
  - `tests/ci/test_validate_post_merge_evidence.py` (262 سطر) — 22 اختبار للمدقق
- الملفات المُعدّلة:
  - `.github/workflows/post-merge-verification.yml` (تصحيح المسار + المهلة + artifacts + final gate)
  - `tests/ci/test_validate_frontend_smoke.py` (توسيع من 6 إلى 10 اختبارات)
- النتيجة المحلية: كل الفحوص تمر (124/124 CI tests، 46/46 workflow security، 0/0 secret findings)
- الحالة الحالية: الفرع محلي وجاهز للدفع، لكن لا توجد بيانات اعتماد GitHub متاحة في البيئة لتنفيذ `git push` / فتح PR / الدمج مباشرةً
- الإجراء التالي المطلوب من المستخدم: الدفع والدمج يدويًا عبر الأوامر التالية:

```bash
cd /home/z/my-project
git push origin fix/p0-2-correct-frontend-smoke-route
# ثم فتح PR على GitHub بعنوان:
#   fix(p0-2): probe the actual frontend auth entry route
# ثم الدمج بعد نجاح كل الفحوص (under SANAD-ST08-GOV-AMENDMENT-001)
# ثم انتظار post-merge verification run على الـ merge SHA الجديد
# ثم تحديث Evidence Matrix بناءً على النتيجة الفعلية
```

- القرار الحالي لـ P0-2: FAIL / NOT ACHIEVED (لم يتم بعد تشغيل post-merge verification على SHA الرئيسي الجديد)
- قرار Gate 8F: OPEN
- قرار الإصدار: NO-GO
- لا يُعلن عن نجاح إلا بعد اجتياز post-merge verification على merge SHA الفعلي واجتياز المدقق المستقل validate_post_merge_evidence.py
