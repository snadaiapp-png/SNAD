# SNAD Control Plane — تشغيل الإدارة العليا

## الحالة

هذه الحزمة مخصصة لبيئات التطوير والتجربة المقيدة. لا تمنح اعتماد تشغيل تجاري أو إنتاجي، وتظل بوابات الأمن الحالية في المشروع نافذة.

## المكونات

- لوحة المؤشرات التنفيذية.
- سجل المستأجرين وإدارة دورة حياتهم.
- سجل الأنظمة والخدمات.
- سجل تدقيق غير قابل للتعديل من واجهات الإدارة.
- واجهة Next.js على المسار `/control-plane`.
- API على المسار `/api/v1/control-plane`.

## نموذج الحماية

يطبق الخادم شرطين مستقلين على كل طلب:

1. يجب أن يكون المستأجر الموجود داخل JWT هو مستأجر التحكم المحدد في البيئة.
2. يجب أن يمتلك المستخدم صلاحية RBAC المناسبة داخل مستأجر التحكم.

إخفاء رابط الواجهة ليس وسيلة حماية؛ الخادم يعيد التحقق من الشرطين في كل طلب.

## متغير البيئة المطلوب

```text
SANAD_CONTROL_PLANE_TENANT_ID=<UUID of the dedicated control tenant>
```

عند عدم تعيين المتغير، تفشل جميع عمليات الإدارة العليا بشكل مغلق `fail closed`.

لا تضع UUID أو بيانات اعتماد فعلية داخل المستودع. تضبط القيمة من مدير الأسرار الخاص ببيئة التشغيل.

## تهيئة مستأجر التحكم

1. أنشئ مستأجرًا مخصصًا للتحكم من عملية التهيئة الحالية.
2. أنشئ حساب المسؤول من مسار bootstrap الآمن الحالي.
3. دوّر كلمة المرور المؤقتة عند أول دخول.
4. تحقق من أن الحساب يحمل دور `ADMIN` النشط.
5. عيّن UUID الخاص بالمستأجر في `SANAD_CONTROL_PLANE_TENANT_ID`.
6. أعد تشغيل خدمة API.
7. سجّل الدخول بالحساب ثم افتح `/control-plane`.

لا تستخدم مستأجر عميل عادي كمستأجر تحكم.

## مسارات API

### قراءة

```text
GET /api/v1/control-plane/dashboard
GET /api/v1/control-plane/tenants
GET /api/v1/control-plane/tenants/{tenantId}
GET /api/v1/control-plane/systems
GET /api/v1/control-plane/audit
```

### كتابة

```text
POST  /api/v1/control-plane/tenants
PATCH /api/v1/control-plane/tenants/{tenantId}/status
PATCH /api/v1/control-plane/systems/{serviceId}/status
```

## انتقالات المستأجر المسموحة

```text
PENDING   -> TRIAL | ACTIVE | ARCHIVED
TRIAL     -> ACTIVE | PAST_DUE | SUSPENDED | CANCELLED
ACTIVE    -> PAST_DUE | SUSPENDED | CANCELLED
PAST_DUE  -> ACTIVE | SUSPENDED | CANCELLED
SUSPENDED -> ACTIVE | CANCELLED | ARCHIVED
CANCELLED -> ACTIVE | ARCHIVED
ARCHIVED  -> no transition
```

تتطلب عمليات تغيير الحالة سببًا إداريًا، وتسجل الحالة السابقة والجديدة في سجل التدقيق.

## التحقق التشغيلي

نفّذ بالترتيب:

```bash
cd apps/sanad-platform
mvn -B test
mvn -B package -DskipTests

cd ../web
npm ci
npm run lint
npm test -- --run
npm run build
```

ثم تحقق من:

- رفض الطلب عند غياب `SANAD_CONTROL_PLANE_TENANT_ID`.
- رفض حساب من مستأجر مختلف.
- رفض مستخدم لا يملك الصلاحية المطلوبة.
- نجاح القراءة لحساب التحكم المخول.
- ظهور عملية إنشاء أو تغيير حالة داخل `platform_audit_logs`.
- عدم ظهور أسرار أو بيانات اعتماد في السجلات أو استجابات API.

## الاستعادة والتراجع

- لا تعدل migrations المطبقة في بيئة مشتركة.
- عند فشل النشر، أعد إصدار التطبيق السابق واتبع سياسة استعادة قاعدة البيانات المعتمدة في المشروع.
- جداول هذه المرحلة إضافية، لكن تغيير قيد حالة المستأجر يجب اختباره على نسخة احتياطية قبل أي ترقية بيئية.

## القيود الحالية

- تكامل مزود الدفع الخارجي غير مفعل.
- لا توجد مفاتيح دفع أو بيانات بطاقات داخل الكود.
- الأسعار والفواتير والتحصيل لا تعتبر جاهزة حتى يكتمل نموذج الفوترة، Webhook verification، وIdempotency، وتنجح اختبارات الأمن المالية.
