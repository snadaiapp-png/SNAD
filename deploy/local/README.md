# SANAD — Local Windows Hosting with Cloudflare Tunnel

هذا المسار يشغّل باك إند SANAD وPostgreSQL على جهاز Windows موجود لديك، وينشر واجهة API عبر Cloudflare Tunnel دون فتح منافذ الراوتر ودون كشف قاعدة البيانات للإنترنت.

## البنية

```text
Vercel Frontend
      |
      | HTTPS
      v
Cloudflare Edge
      |
      | outbound encrypted tunnel
      v
cloudflared container
      |
      | Docker private network
      v
Spring Boot backend :8080
      |
      | Docker private network
      v
PostgreSQL :5432
```

## قواعد الأمان

- لا تفتح منافذ في الراوتر.
- لا تنشر PostgreSQL أو منفذ 5432.
- الباك إند متاح محليًا فقط على `127.0.0.1:18080` للتشخيص.
- الوصول العام يمر فقط عبر Cloudflare Tunnel.
- لا تحفظ رمز Tunnel أو كلمات المرور داخل GitHub.
- لا توقف Render قبل نجاح النقل النهائي وخطة الرجوع.

---

# المتطلبات

- Windows 10/11 مدعوم ومحدّث.
- تفعيل Virtualization من BIOS/UEFI.
- ذاكرة 8 GB كحد أدنى؛ 16 GB أفضل.
- مساحة حرة 30 GB على الأقل.
- اتصال إنترنت ثابت.
- بقاء الجهاز قيد التشغيل وعدم دخوله في Sleep.
- حساب Cloudflare مجاني.
- نطاق مضاف إلى Cloudflare للنشر الدائم.

---

# المرحلة 1 — تثبيت WSL2

افتح PowerShell بصلاحية Administrator:

```powershell
wsl --install
```

أعد تشغيل Windows، ثم افتح Ubuntu وأنشئ اسم مستخدم وكلمة مرور Linux.

تحقق:

```powershell
wsl --version
wsl --list --verbose
```

يجب أن تظهر Ubuntu على Version 2. عند الحاجة:

```powershell
wsl --set-version Ubuntu 2
wsl --update
```

## بوابة القبول G1

- [ ] WSL يعمل.
- [ ] Ubuntu تعمل على Version 2.
- [ ] Virtualization مفعلة.

---

# المرحلة 2 — تثبيت Docker Desktop

1. نزّل Docker Desktop الرسمي لنظام Windows.
2. اختر WSL 2 backend أثناء التثبيت.
3. افتح Docker Desktop واقبل شروط الاستخدام المناسبة لحالتك.
4. من Settings > General فعّل التشغيل عند تسجيل الدخول إلى Windows.
5. من Settings > Resources > WSL Integration فعّل Ubuntu.

اختبر من PowerShell:

```powershell
docker version
docker compose version
docker run --rm hello-world
```

## بوابة القبول G2

- [ ] Docker Desktop يعمل.
- [ ] Linux containers هي الوضع الحالي.
- [ ] أمر `hello-world` ناجح.

---

# المرحلة 3 — إعداد الطاقة والاستمرارية

في Windows:

1. افتح Settings > System > Power.
2. اجعل Sleep = Never أثناء توصيل الكهرباء.
3. اترك إطفاء الشاشة حسب رغبتك؛ المهم منع Sleep/Hibernate.
4. فعّل Start Docker Desktop when you sign in.
5. استخدم اتصال Ethernet إن توفر.
6. يفضل استخدام UPS لحماية الجهاز والراوتر من انقطاع الكهرباء.

لا يُعد اللابتوب مناسبًا كخادم دائم إذا كان يُغلق الغطاء أو ينقطع عنه الاتصال باستمرار.

---

# المرحلة 4 — تنزيل فرع التشغيل المحلي

من PowerShell:

```powershell
cd C:\
git clone --branch infra/local-cloudflare-hosting https://github.com/snadaiapp-png/SNAD.git SNAD
cd C:\SNAD
```

إن كان المستودع موجودًا:

```powershell
cd C:\SNAD
git fetch origin
git switch infra/local-cloudflare-hosting
git pull
```

---

# المرحلة 5 — إنشاء حساب Cloudflare وإضافة النطاق

1. أنشئ حساب Cloudflare مجانيًا.
2. أضف النطاق الذي ستستخدمه.
3. غيّر Nameservers لدى مسجل النطاق إلى القيم التي يقدمها Cloudflare.
4. انتظر حتى تصبح حالة النطاق Active.

للاختبار المؤقت يمكن استخدام Quick Tunnel، لكن النشر الدائم لـSANAD يجب أن يستخدم Named Tunnel ونطاقًا مملوكًا لك.

---

# المرحلة 6 — إنشاء Cloudflare Tunnel

من Cloudflare Dashboard:

1. افتح Networking > Tunnels.
2. اختر Create a tunnel.
3. اختر Cloudflared.
4. الاسم المقترح: `sanad-local-backend`.
5. اختر Docker كبيئة التشغيل.
6. انسخ قيمة Token فقط من أمر التشغيل المعروض؛ لا تشاركها ولا تحفظها في GitHub.

لا تشغّل الأمر الذي يعرضه Cloudflare مباشرة، لأن خدمة `cloudflared` موجودة داخل Docker Compose الخاص بالمشروع.

---

# المرحلة 7 — إعداد الأسرار

من PowerShell داخل المستودع:

```powershell
Copy-Item .\deploy\local\.env.local.example .\deploy\local\.env
notepad .\deploy\local\.env
```

استبدل جميع القيم التي تبدأ بـ:

```text
REPLACE_WITH_
```

أهم المتغيرات:

```dotenv
CLOUDFLARE_TUNNEL_TOKEN=رمز_النفق
DATABASE_PASSWORD=كلمة_مرور_قوية
JWT_SECRET=نفس_قيمة_JWT_SECRET_الحالية_في_Render
APPLICATION_BASE_URL=https://snad-app.vercel.app
SANAD_CORS_ALLOWED_ORIGINS=https://snad-app.vercel.app
```

توليد كلمات مرور عشوائية من Ubuntu/WSL:

```bash
openssl rand -base64 48
openssl rand -base64 64
```

مهم: استخدم نفس `JWT_SECRET` الحالي أثناء النقل لتجنب إبطال جلسات المستخدمين دون قصد.

## بوابة القبول G3

- [ ] لا توجد قيم `REPLACE_WITH_`.
- [ ] رمز Cloudflare Tunnel محفوظ في `.env` فقط.
- [ ] PostgreSQL لديها كلمة مرور قوية.
- [ ] `JWT_SECRET` مطابق للقيمة الحالية في Render.

---

# المرحلة 8 — تشغيل نسخة فارغة واختبار البناء

من PowerShell:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\deploy\local\scripts\start-local.ps1
```

السكربت يقوم تلقائيًا بـ:

1. التحقق من Docker.
2. فحص ملف `.env`.
3. التحقق من Docker Compose.
4. بناء Spring Boot.
5. تشغيل PostgreSQL والباك إند والنفق.
6. انتظار `/actuator/health`.
7. عرض حالة الخدمات وسجلات Cloudflare Tunnel.

اختبار يدوي:

```powershell
Invoke-RestMethod http://127.0.0.1:18080/actuator/health
```

المتوقع:

```json
{"status":"UP"}
```

عرض الحالة والسجلات:

```powershell
docker compose --env-file .\deploy\local\.env -f .\deploy\local\docker-compose.local.yml ps
docker compose --env-file .\deploy\local\.env -f .\deploy\local\docker-compose.local.yml logs --tail 200 backend
docker compose --env-file .\deploy\local\.env -f .\deploy\local\docker-compose.local.yml logs --tail 100 cloudflared
```

---

# المرحلة 9 — ربط Published Application

من Cloudflare Dashboard:

1. Networking > Tunnels.
2. افتح `sanad-local-backend`.
3. Routes > Add route > Published application.
4. اختر النطاق الفرعي، مثل `api`.
5. اختر نطاقك.
6. Service Type: `HTTP`.
7. Service URL:

```text
http://backend:8080
```

مهم: لا تستخدم `localhost:8080`؛ لأن cloudflared يعمل داخل Container منفصل ويصل للباك إند باسم الخدمة `backend` عبر شبكة Docker الخاصة.

بعد الحفظ، اختبر:

```powershell
Invoke-RestMethod https://api.your-domain.example/actuator/health
```

يجب أن تصبح حالة Tunnel في Cloudflare: `Healthy`.

## بوابة القبول G4

- [ ] الصحة المحلية UP.
- [ ] الصحة العامة عبر HTTPS تساوي UP.
- [ ] لا توجد أي Port Forwarding rules في الراوتر.
- [ ] منفذ PostgreSQL غير مكشوف.

---

# المرحلة 10 — النقل التجريبي من Render

لا توقف Render. نفّذ تجربة نقل أولًا:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\deploy\local\scripts\migrate-postgres-from-render.ps1
```

عند الطلب:

1. الصق Render External PostgreSQL URL في الإدخال المخفي.
2. انتظر إنشاء Dump والتحقق منه.
3. اكتب `RESTORE` عند التأكد من استبدال قاعدة البيانات المحلية.

بعد الاستعادة، شغّل كامل النظام:

```powershell
.\deploy\local\scripts\start-local.ps1
```

اختبر على الأقل:

- تسجيل الدخول والخروج.
- Refresh Token.
- إنشاء وتحديث عميل CRM.
- صلاحيات الإدارة العليا.
- عزل المستأجرين.
- Flyway schema history.
- CORS من واجهة Vercel.
- البريد والإشعارات.

---

# المرحلة 11 — النسخ الاحتياطي

نسخة يدوية:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\deploy\local\scripts\backup-postgres.ps1
```

المسار الافتراضي:

```text
%USERPROFILE%\SNAD-Backups
```

ينشئ السكربت:

- PostgreSQL custom-format dump.
- ملف SHA-256.
- حذف النسخ الأقدم من 7 أيام افتراضيًا.

لتغيير مدة الاحتفاظ:

```powershell
.\deploy\local\scripts\backup-postgres.ps1 -RetentionDays 14
```

أنشئ مهمة يومية من Windows Task Scheduler لتشغيل PowerShell بالوسائط:

```text
-ExecutionPolicy Bypass -File C:\SNAD\deploy\local\scripts\backup-postgres.ps1
```

يجب الاحتفاظ بنسخة ثانية خارج الجهاز، مثل قرص USB منفصل أو مساحة تخزين سحابية موثوقة.

---

# المرحلة 12 — التحويل النهائي

بعد نجاح الاختبارات:

1. أعلن نافذة صيانة قصيرة.
2. أوقف عمليات الكتابة مؤقتًا.
3. نفّذ آخر تصدير من Render.
4. استعده في PostgreSQL المحلية.
5. شغّل النظام واختبر الصحة وتسجيل الدخول.
6. غيّر متغير API URL في Vercel إلى نطاق Cloudflare الجديد.
7. اختبر الإنتاج.
8. أعد فتح العمليات الكتابية.
9. اترك Render فعالًا لمدة 48–72 ساعة كخطة رجوع.

لا تحذف Render أو قاعدة بياناته مباشرة.

---

# خطة الرجوع

عند حدوث خلل جوهري:

1. أعد Vercel API URL إلى Render.
2. أوقف الكتابة على البيئة المحلية لتجنب انقسام البيانات.
3. احتفظ بسجلات Docker ونسخة قاعدة البيانات المحلية.
4. حل المشكلة ثم أعد تجربة النقل.

أوامر إيقاف محلية دون حذف البيانات:

```powershell
docker compose --env-file .\deploy\local\.env -f .\deploy\local\docker-compose.local.yml stop
```

إعادة التشغيل:

```powershell
docker compose --env-file .\deploy\local\.env -f .\deploy\local\docker-compose.local.yml up -d
```

لا تستخدم `down -v` في الإنتاج لأنه يحذف Volume قاعدة البيانات.

---

# القيود التشغيلية

هذا الحل مجاني سحابيًا لكنه يعتمد على جهازك:

- انقطاع الكهرباء يوقف الخدمة.
- Sleep أو إغلاق Windows يوقف الخدمة.
- تعطل الراوتر أو الإنترنت يوقف الوصول الخارجي.
- تحديثات Windows قد تعيد تشغيل الجهاز.
- لا توجد High Availability بجهاز واحد.

لذلك يجب تطبيق النسخ الاحتياطي اليومي، تفعيل التشغيل التلقائي، ومراقبة حالة Tunnel والخدمة.
