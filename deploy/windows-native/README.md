# SANAD Windows Native Hosting

هذا المسار يشغّل باك إند SANAD مباشرة على Windows دون WSL أو Docker أو Virtualization.

## البنية

```text
Vercel
  -> Cloudflare Tunnel
  -> 127.0.0.1:8080
  -> Spring Boot JAR
  -> PostgreSQL 16 on 127.0.0.1:5432
```

لا يتم فتح المنفذين 8080 أو 5432 للإنترنت.

## المتطلبات

- Windows 11 x64
- Eclipse Temurin JDK 17
- PostgreSQL 16
- قاعدة `sanad` ومستخدم `sanad_app`
- مساحة 5 GB على الأقل للبرنامج والسجلات والنسخ المؤقتة

## 1. تنزيل حزمة الإصدار

شغّل GitHub Actions workflow باسم `Windows Native Backend Release` على الفرع المطلوب، ثم نزّل Artifact باسم:

```text
sanad-windows-native-release
```

فك الضغط مباشرة إلى:

```text
C:\SANAD
```

يجب أن تصبح البنية:

```text
C:\SANAD\app\sanad-backend.jar
C:\SANAD\scripts\Start-Sanad.ps1
C:\SANAD\scripts\Stop-Sanad.ps1
C:\SANAD\scripts\Install-SanadTask.ps1
C:\SANAD\scripts\Migrate-RenderDatabase.ps1
C:\SANAD\config\sanad.env.example
C:\SANAD\SHA256SUMS.txt
```

تحقق من الملف التنفيذي:

```powershell
Get-FileHash C:\SANAD\app\sanad-backend.jar -Algorithm SHA256
Get-Content C:\SANAD\SHA256SUMS.txt
```

## 2. إعداد الأسرار

```powershell
Copy-Item C:\SANAD\config\sanad.env.example C:\SANAD\config\sanad.env
notepad C:\SANAD\config\sanad.env
```

استبدل القيم التالية:

- `DATABASE_PASSWORD`: كلمة مرور `sanad_app` المحلية.
- `JWT_SECRET`: نفس القيمة الموجودة في Render حتى لا تُلغى الجلسات الحالية.
- `SANAD_CORS_ALLOWED_ORIGINS`: عنوان واجهة Vercel المعتمد.
- إعدادات الإشعارات وResend المستخدمة في الإنتاج.

لا تضع كلمة مرور `postgres` داخل ملف البيئة. ولا ترسل محتوى الملف في المحادثات أو تحفظه داخل GitHub.

## 3. نقل PostgreSQL من Render

أوقف أي نسخة محلية من الباك إند ثم نفّذ من PowerShell كمسؤول:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
& C:\SANAD\scripts\Migrate-RenderDatabase.ps1
```

سيطلب السكربت رابط Render الخارجي وكلمة مرور `postgres` المحلية بصورة مخفية، ثم ينشئ Dump متحققًا منه وبصمة SHA-256 قبل الاستعادة.

لا تغيّر DNS ولا توقف Render بعد هذه الخطوة.

## 4. تشغيل SANAD يدويًا

```powershell
Set-ExecutionPolicy -Scope Process Bypass
& C:\SANAD\scripts\Start-Sanad.ps1
```

المتوقع:

```text
SANAD STARTUP: SUCCESS (..., health=UP)
```

اختبار مستقل:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

المتوقع:

```text
status
------
UP
```

السجلات:

```text
C:\SANAD\logs\sanad-out.log
C:\SANAD\logs\sanad-error.log
```

## 5. إيقاف SANAD

```powershell
& C:\SANAD\scripts\Stop-Sanad.ps1
```

## 6. التشغيل التلقائي مع Windows

بعد نجاح التشغيل اليدوي واختبارات الدخول:

```powershell
& C:\SANAD\scripts\Install-SanadTask.ps1
Start-ScheduledTask -TaskName 'SANAD Backend'
```

يعتمد Scheduled Task حساب `SYSTEM` ويقيد ملف الأسرار على SYSTEM وAdministrators فقط.

## 7. Cloudflare Tunnel

لا يتم إنشاء Tunnel قبل نجاح الصحة المحلية والدخول وعزل المستأجرين. عند الجاهزية تكون خدمة الـTunnel موجهة إلى:

```text
http://127.0.0.1:8080
```

## حدود هذا الجهاز

الإعدادات الحالية مخصصة لجهاز بذاكرة تقارب 6 GB ومعالج ثنائي النواة:

```text
Heap: 128 MB initial / 768 MB maximum
Hikari pool: 1-3 connections
SerialGC
Lazy initialization
```

هذا مناسب للتجربة والاستخدام المنخفض، وليس استضافة إنتاجية واسعة أو عالية التوافر.

## بوابات القبول

- [ ] بصمة JAR مطابقة.
- [ ] ملف البيئة خالٍ من placeholders.
- [ ] ترحيل Render ناجح وعدد الجداول منطقي.
- [ ] `/actuator/health` يعيد `UP`.
- [ ] تسجيل الدخول وتجديد الجلسة ناجحان.
- [ ] CORS وSecure Cookies يعملان من Vercel.
- [ ] عزل المستأجرين مختبر.
- [ ] نسخة احتياطية خارج الجهاز موجودة.
- [ ] Render ما زال متاحًا لخطة الرجوع.
