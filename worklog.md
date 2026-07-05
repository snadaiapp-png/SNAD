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

---
Task ID: forgot-password-fix-v2
Agent: main (Super Z)
Task: تحسين شاشة تسجيل الدخول — إضافة رابط "نسيت كلمة المرور؟" بأيقونة + نقل المسار إلى /auth/forgot-password

Work Log:
- تثبيت `lucide-react` (v1.23.0) في apps/web — لم يكن مثبتاً سابقاً
- نقل صفحة استعادة كلمة المرور إلى المسار الكنسي `/auth/forgot-password` (المسار المطلوب في التذكرة الجديدة)
- إبقاء `/forgot-password` كـ redirect فقط (HTTP 308 دائم) للتوافق الخلفي مع الروابط المرسلة سابقاً عبر البريد أو المحفوظة في المفضلة
- تعديل `login-form.tsx`:
  - نقل رابط "نسيت كلمة المرور؟" ليصبح **مباشرة أسفل حقل كلمة المرور، وقبل زر تسجيل الدخول** (مطابق لمتطلبات التذكرة)
  - إضافة أيقونة `KeyRound` من lucide-react بجانب النص
  - إضافة `aria-label="نسيت كلمة المرور؟ استعادة كلمة المرور"` للوصولية
- إعادة تصميم تنسيقات `auth.module.css` للرابط الجديد:
  - `display: inline-flex` لمحاذاة الأيقونة مع النص عمودياً
  - `gap: 0.4375rem` بين الأيقونة والنص
  - `min-height: 36px` لضمان حجم لمس كافٍ (WCAG 2.5.5)
  - حالات Hover: تغيير اللون + خلفية شفافة + تغيير لون الأيقونة
  - حالة Focus-visible: outline واضح بـ `--snad-focus-ring`
  - حالة Active: تأثير بصري خفيف للضغط
  - دعم RTL عبر `margin-inline-start` لمحاذاة الحافة
  - دعم الوضع الداكن عبر `prefers-color-scheme: dark`
- تحديث `globals.css` بإضافة styles لـ brand header في صفحة الاستعادة (أيقونة KeyRound + شعار SNAD)
- تحديث `reset-password/page.tsx`: رابط "طلب رابط استعادة جديد" يشير الآن إلى `/auth/forgot-password`
- تحديث الاختبارات:
  - `app/auth/forgot-password/page.test.tsx`: 9 اختبارات (8 سابقة + اختبار الأيقونة)
  - `components/auth/login-form.test.tsx`: 13 اختباراً (11 سابقاً + 2 جديدان للأيقونة وترتيب الرابط)
  - `app/reset-password/page.test.tsx`: 7 اختبارات (6 سابقة + اختبار رابط إعادة الطلب)
  - mock جديد لـ `lucide-react` في الاختبارات لإرجاع `<svg data-testid="key-round-icon">`
- التحقق النهائي:
  - TypeScript: ✅ بلا أخطاء
  - ESLint: ✅ بلا أخطاء
  - كل اختبارات web: ✅ 266/266 نجحت
  - بناء Next.js للإنتاج: ✅ نجح، `/auth/forgot-password` و `/forgot-password` (redirect) ظاهرتان في الـ routes

Stage Summary:
- الملفات المُنشأة:
  - `apps/web/app/auth/forgot-password/page.tsx` (الصفحة الكاملة في المسار الكنسي)
  - `apps/web/app/auth/forgot-password/page.test.tsx` (9 اختبارات)
- الملفات المُعدّلة:
  - `apps/web/app/forgot-password/page.tsx` (أصبح redirect فقط إلى /auth/forgot-password)
  - `apps/web/app/forgot-password/page.test.tsx` (حُذف — الصفحة أصبحت redirect)
  - `apps/web/app/reset-password/page.tsx` (تحديث رابط "طلب جديد" للمسار الكنسي)
  - `apps/web/app/reset-password/page.test.tsx` (تحديث اختبار النجاح + اختبار جديد لرابط الإعادة)
  - `apps/web/components/auth/login-form.tsx` (نقل الرابط + إضافة أيقونة + aria-label)
  - `apps/web/components/auth/login-form.test.tsx` (mock لـ lucide + 3 اختبارات جديدة للأيقونة والترتيب)
  - `apps/web/components/auth/auth.module.css` (تنسيقات شاملة: RTL, dark mode, hover/focus/active, tap target)
  - `apps/web/app/globals.css` (styles لـ brand header مع أيقونة)
  - `apps/web/package.json` (إضافة lucide-react v1.23.0)
- معايير القبول المُنفّذة (مطابقة لمتطلبات التذكرة):
  1. ✅ الرابط يظهر لجميع المستخدمين
  2. ✅ أيقونة KeyRound بجانب النص
  3. ✅ Next.js Link — لا إعادة تحميل للصفحة
  4. ✅ التوجيه إلى `/auth/forgot-password`
  5. ✅ Responsive — يعمل على الجوال والكمبيوتر
  6. ✅ يدعم الوضعين الفاتح والداكن (via prefers-color-scheme)
  7. ✅ لا يؤثر على زر تسجيل الدخول (الرابط قبل الزر، ولم يُمَس منطق onLogin)
  8. ✅ WCAG: aria-label، focus-visible، tap target 36px، contrast tokens

