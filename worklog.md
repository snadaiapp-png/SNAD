# SANAD Platform — Worklog

---
Task ID: forgot-password-fix
Agent: main (Super Z)
Task: إصلاح صفحتي استعادة كلمة المرور المفقودتين على Vercel

Work Log:
- تشخيص المشكلة: صفحة `/forgot-password` غير موجودة أصلاً في الكود، وصفحة `/reset-password` موجودة لكن بدون رابط وصول إليها من نموذج تسجيل الدخول
- إنشاء صفحة `/forgot-password` جديدة في `apps/web/app/forgot-password/page.tsx` بتصميم مطابق لصفحة `/reset-password` الحالية
- الصفحة الجديدة تستخدم `authApi.forgotPassword()` المتوفر مسبقاً في `lib/api/auth.ts`
- تصميم السلوك وفق مبدأ anti-enumeration: دائماً يُظهر رسالة "تحقّق من بريدك" حتى لو فشل الـ API
- دعم وضع التشغيل التجريبي: إذا أرجع الـ backend `resetUrl`، يُعرض رابط مباشر لإكمال العملية
- تحديث `globals.css` لإضافة الـ classes الجديدة: `.snad-reset-label`, `.snad-reset-secondary`, `.snad-reset-info`, `.snad-reset-success`
- إضافة رابط "نسيت كلمة المرور؟" في `components/auth/login-form.tsx` يربط إلى `/forgot-password`
- إضافة الـ classes الجديدة في `auth.module.css`: `.authForgotLinkRow`, `.authForgotLink` مع hover/focus states
- تحسين صفحة `/reset-password`: إضافة رسالة نجاح واضحة + رابط "طلب رابط استعادة جديد" بدلاً من إخفاء الزر
- إنشاء اختبارات شاملة في `app/forgot-password/page.test.tsx` (8 tests)
- تحديث اختبار `login-form.test.tsx` ليعكس السلوك الجديد الصحيح (وجود رابط forgot-password بدلاً من غيابه)
- التحقق من TypeScript: ✅ بلا أخطاء
- تشغيل كل اختبارات web: ✅ 262/262 نجحت
- التحقق من lint: ✅ بلا أخطاء
- بناء Next.js للإنتاج: ✅ نجح، `/forgot-password` ظاهرة في قائمة الـ routes

Stage Summary:
- الملفات المُنشأة:
  - `apps/web/app/forgot-password/page.tsx` (صفحة طلب استعادة كلمة المرور)
  - `apps/web/app/forgot-password/page.test.tsx` (8 اختبارات)
- الملفات المُعدّلة:
  - `apps/web/app/globals.css` (إضافة styles: label, secondary link, info panel, success message)
  - `apps/web/app/reset-password/page.tsx` (تحسين UX في حالتي النجاح والفشل)
  - `apps/web/components/auth/login-form.tsx` (إضافة رابط "نسيت كلمة المرور؟")
  - `apps/web/components/auth/login-form.test.tsx` (تحديث الاختبار المتعلق بالرابط)
  - `apps/web/components/auth/auth.module.css` (إضافة styles للرابط الجديد)
- النتيجة: التدفق الكامل لاستعادة كلمة المرور أصبح متاحاً في الواجهة الأمامية
  - المستخدم يدخل بريده → `/forgot-password`
  - البريد يصل (في الإنتاج) أو يظهر رابط مباشر (في وضع pilot)
  - المستخدم يضغط الرابط → `/reset-password?token=...`
  - المستخدم يعيّن كلمة مرور جديدة → يعود لتسجيل الدخول
- الخطوات التالية المطلوبة من المستخدم:
  1. عمل commit و push لهذه التغييرات إلى main
  2. Vercel سيعيد النشر تلقائياً (auto-deploy)
  3. اختبار التدفق على https://snad-app.vercel.app/forgot-password
  4. التأكد من إعدادات SMTP على Render (MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD) ليصل البريد فعلياً
