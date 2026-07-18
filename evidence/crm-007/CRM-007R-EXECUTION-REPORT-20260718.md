# EXEC-PROMPT-CRM-007R — تقرير تاريخي مستبدل

> **الحالة:** SUPERSEDED / NOT AUTHORITATIVE  
> **التاريخ:** 2026-07-18  
> **المرجع الحالي:** `CRM-007R2-CLOSURE-RECORD-20260718.md`

## تنبيه حوكمي

هذه النسخة كانت مبنية على استنتاجات لم تعد صحيحة، منها اعتبار النشر اليدوي على Vercel العائق الوحيد، واعتبار H2 بديلًا كافيًا لإثبات Production PostgreSQL، واعتبار فحوص anonymous smoke كافية للإغلاق.

لا يجوز استخدام هذا الملف لإغلاق CRM-007 أو CRM-G3D، ولا لتفويض CRM-008.

## الحالة الرسمية الصحيحة

```text
EXEC-PROMPT-CRM-007: IN PROGRESS — BLOCKED
CRM-G3D:              OPEN — NOT APPROVED
Issue #563:           OPEN
CRM-008:              NOT AUTHORIZED
```

## التصحيحات الأساسية

1. Vercel Git Integration تعمل؛ النشر اليدوي ليس العائق الرئيسي.
2. `main` تحرك بعد `4c7d640...` إلى `7b7c06d6a96ee07e082de86baf8169d0b93f8c11`، وهو SHA نشر Production الحالي وقت التصحيح.
3. الفحص الحي لمساري BFF v1 وv2 أعاد HTTP `502`، مع أخطاء `BackendRequestError / timeout` في Vercel.
4. CI على GitHub نفذ Testcontainers بنجاح، ولذلك أخطاء Docker المحلية ليست دليل فشل للكود؛ لكنها لا تثبت أن runtime الدائم يستخدم PostgreSQL.
5. تشغيل H2 بملف `local` لا يحقق بوابة Production PostgreSQL ولا يثبت الاستمرارية.
6. Flyway `20260718.1` مثبت على PostgreSQL Testcontainers في CI، لكنه يحتاج إثباتًا منفصلًا على قاعدة runtime الدائمة.
7. authenticated production smoke وtwo-tenant isolation وRBAC وaudit/timeline لم تنفذ عبر Vercel+BFF.
8. ngrok الحالي مؤقت وغير محجوز، ولا يحقق بوابة endpoint ثابت طويل الأمد.

راجع سجل R2 الحالي للحصول على الأدلة والعوائق وشروط العودة إلى Ready.
