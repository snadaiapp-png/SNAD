# SANAD Platform — روابط استعادة كلمة المرور

## 🌐 روابط الخدمة المنشورة

| الخدمة | الرابط |
|--------|--------|
| **الواجهة الأمامية (Frontend)** | https://snad-app.vercel.app |
| **الخادم (Backend API)** | https://sanad-backend-mcrj.onrender.com |
| **صفحة استعادة كلمة المرور (Web UI)** | https://snad-app.vercel.app/reset-password |
| **صفحة طلب استعادة كلمة المرور** | https://snad-app.vercel.app/forgot-password |

---

## 🔗 Endpoint الخاص بـ API (لاستعادة كلمة المرور)

### 1) طلب رابط استعادة كلمة المرور (Forgot Password)

```http
POST https://sanad-backend-mcrj.onrender.com/api/v1/auth/forgot-password
Content-Type: application/json

{
  "email": "user@example.com"
}
```

**الاستجابة:** دائماً `200 OK` (لتجنب كشف وجود الحساب — anti-enumeration)

```json
{
  "message": "If the email exists, a reset link has been sent."
}
```

> ملاحظة: الرابط الفعلي لإعادة التعيين يُرسل إلى البريد الإلكتروني للمستخدم إن وُجد.

---

### 2) تعيين كلمة مرور جديدة (Reset Password)

```http
POST https://sanad-backend-mcrj.onrender.com/api/v1/auth/reset-password
Content-Type: application/json

{
  "token": "<TOKEN_FROM_EMAIL>",
  "newPassword": "<NEW_STRONG_PASSWORD>"
}
```

---

### 3) إعادة تعيين بواسطة الأدمن (Admin-Initiated Reset)

```http
POST https://sanad-backend-mcrj.onrender.com/api/v1/auth/admin-reset-password/{userId}
Authorization: Bearer <ADMIN_JWT>
Content-Type: application/json

{
  "locale": "ar"
}
```

> يتطلب صلاحية `USER.WRITE`

---

## 🧪 اختبار سريع بـ curl

### اختبار endpoint نسيت كلمة المرور

```bash
curl -X POST https://sanad-backend-mcrj.onrender.com/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@sanad.local"}'
```

### التحقق من حالة الخادم أولاً

```bash
curl https://sanad-backend-mcrj.onrender.com/actuator/health
```

---

## ⚠️ ملاحظات مهمة

1. **رابط الاستعادة (reset link)** يُرسَل عبر البريد الإلكتروني فقط — يجب أن يكون إعداد `SMTP` / `MAIL_*` صحيحاً في متغيرات البيئة على Render
2. **الرمز (token)** صالح للاستخدام مرة واحدة فقط (single-use) — ينتهي بعد التعيين مباشرة
3. **حماية من التخمين (rate limit)** مطبّقة على `forgot-password` لمنع إساءة الاستخدام
4. **في حال عدم وصول البريد**، الخيارات البديلة:
   - تحديث كلمة المرور مباشرة في قاعدة البيانات عبر `sanad_password_rotation.sql` (الملف الذي ولّدناه سابقاً)
   - استخدام admin-reset-password endpoint (إذا كان لديك صلاحية أدمن فعّالة)

---

## 📂 الملفات المرجعية في الكود

| الملف | الوصف |
|-------|------|
| `apps/sanad-platform/src/main/java/com/sanad/platform/security/api/AuthController.java` | تعريف endpoints (الأسطر 166–203) |
| `apps/sanad-platform/src/main/java/com/sanad/platform/security/dto/ForgotPasswordRequest.java` | DTO لطلب الاستعادة |
| `apps/sanad-platform/src/main/java/com/sanad/platform/security/dto/ResetPasswordRequest.java` | DTO لإعادة التعيين |
| `apps/sanad-platform/src/main/java/com/sanad/platform/security/notification/PasswordRecoveryNotificationCoordinator.java` | منطق إرسال البريد |
| `apps/sanad-platform/src/main/resources/db/migration/V12__create_password_reset_tokens.sql` | جدول الـ tokens |

---

## 🚀 خطوات تطبيقية لاستعادة كلمة مرور admin

1. اذهب إلى: **https://snad-app.vercel.app/forgot-password**
2. أدخل البريد المرتبط بحساب admin
3. افحص صندوق البريد (أو مجلد Spam)
4. اضغط على الرابط — سيحولك إلى `https://snad-app.vercel.app/reset-password?token=...`
5. أدخل كلمة المرور الجديدة (20+ حرف، حروف + أرقام + رموز)
6. سجّل الدخول بالكلمة الجديدة

### في حال فشل تسليم البريد
استخدم `sanad_password_rotation.sql` لتعيين الـ hash مباشرة في قاعدة البيانات (الطريقة التي جهّزناها سابقاً).
