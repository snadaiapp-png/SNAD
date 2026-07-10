# SANAD — Oracle Cloud Always Free Migration Runbook

هذا الدليل ينقل باك إند SANAD من Render إلى Oracle Cloud مع إبقاء Render فعالًا حتى نجاح الاختبارات والتحويل النهائي.

## المبادئ الحاكمة

- لا يتم تعديل `main` أو تعطيل Render أثناء التجهيز.
- لا تُحفظ أي أسرار داخل GitHub.
- PostgreSQL غير مكشوف للإنترنت.
- الباك إند غير مكشوف مباشرة؛ الوصول العام يمر عبر Caddy وHTTPS.
- كل مرحلة لها بوابة قبول واضحة.
- التحويل النهائي لا يتم قبل نجاح نسخة تجريبية كاملة وخطة رجوع موثقة.

---

## البنية المستهدفة

```text
Internet
   |
   | TCP 80/443
   v
Caddy (automatic HTTPS)
   |
   | Docker private network
   v
Spring Boot backend :8080
   |
   | Docker private network
   v
PostgreSQL :5432 + persistent named volume
```

## مواصفات Oracle المستهدفة

- Shape: `VM.Standard.A1.Flex`
- Architecture: ARM64 / Ampere
- OCPU: 2
- Memory: 12 GB
- OS: Ubuntu 24.04 LTS ARM64
- Boot volume: 50–100 GB حسب المساحة المجانية المتاحة
- Public IPv4: enabled
- Ingress: 22 من عنوان الإدارة فقط، و80/443 من الإنترنت

> لا تنشئ أي مورد قبل التأكد من ظهور علامة `Always Free Eligible` وتحقق حدود الحساب والمنطقة الرئيسية.

---

# المرحلة 0 — التجميد والتوثيق

## المطلوب

1. سجل عنوان خدمة Render الحالي.
2. سجل عنوان PostgreSQL الخارجي في مدير أسرار آمن؛ لا تضعه في ملف داخل المستودع.
3. احصر متغيرات Render الحالية، خصوصًا:
   - `JWT_SECRET`
   - `DATABASE_URL`
   - `DATABASE_USERNAME`
   - `DATABASE_PASSWORD`
   - `SANAD_CORS_ALLOWED_ORIGINS`
   - `APPLICATION_BASE_URL`
   - إعدادات Resend/الإشعارات
   - إعدادات bootstrap/control-plane
4. خفّض TTL لسجل API DNS إلى 300 ثانية قبل التحويل النهائي بمدة مناسبة.
5. لا تدوّر `JWT_SECRET` أثناء النقل، حتى لا تُلغى الجلسات الحالية بلا قصد.

## بوابة القبول G0

- [ ] قائمة المتغيرات مكتملة.
- [ ] بيانات دخول Render وOracle متاحة.
- [ ] عنوان DNS المطلوب معروف.
- [ ] Render لم يتوقف.

---

# المرحلة 1 — إنشاء Oracle VM

من Oracle Console:

1. أنشئ VCN مع اتصال بالإنترنت أو استخدم VCN عامة قائمة.
2. أنشئ Network Security Group للخادم.
3. أنشئ Compute Instance بالمواصفات المستهدفة.
4. أضف مفتاح SSH العام، واحتفظ بالمفتاح الخاص خارج GitHub.
5. فعّل Public IPv4.
6. أضف قواعد ingress التالية:

| المصدر | البروتوكول | المنفذ | الغرض |
|---|---|---:|---|
| عنوان IP الإداري فقط `/32` | TCP | 22 | SSH |
| `0.0.0.0/0` | TCP | 80 | ACME/HTTP redirect |
| `0.0.0.0/0` | TCP | 443 | HTTPS |
| `0.0.0.0/0` | UDP | 443 | HTTP/3 اختياري |

لا تفتح `5432` أو `8080` في OCI.

اتصل بالخادم:

```bash
chmod 600 ~/.ssh/oracle_snad.key
ssh -i ~/.ssh/oracle_snad.key ubuntu@ORACLE_PUBLIC_IP
```

## بوابة القبول G1

```bash
uname -m
free -h
lsblk
```

المتوقع:

- `aarch64`
- قرابة 12 GB ذاكرة
- حجم القرص مطابق للمحدد

---

# المرحلة 2 — تجهيز نظام التشغيل

بعد نسخ المستودع أو ملف السكربت إلى الخادم:

```bash
sudo bash deploy/oracle/scripts/provision-ubuntu.sh
```

ثم سجّل الخروج والدخول من جديد، واختبر:

```bash
docker version
docker compose version
sudo ufw status verbose
systemctl is-active docker fail2ban
```

## بوابة القبول G2

- [ ] Docker يعمل دون `sudo` للمستخدم التشغيلي.
- [ ] UFW يسمح فقط بـ22 و80 و443.
- [ ] fail2ban يعمل.
- [ ] لا توجد حزم مدفوعة أو خدمات Oracle إضافية غير مقصودة.

---

# المرحلة 3 — تنزيل المشروع وإعداد الأسرار

```bash
sudo mkdir -p /opt/snad
sudo chown "$USER:$USER" /opt/snad
git clone --branch infra/oracle-cloud-migration https://github.com/snadaiapp-png/SNAD.git /opt/snad
cd /opt/snad
cp deploy/oracle/.env.oracle.example deploy/oracle/.env
chmod 600 deploy/oracle/.env
```

أنشئ أسرارًا قوية:

```bash
openssl rand -base64 48
openssl rand -base64 64
```

حرّر الملف:

```bash
nano deploy/oracle/.env
```

يجب استبدال جميع قيم `REPLACE_WITH_...` وضبط:

```dotenv
SANAD_API_DOMAIN=api.your-domain.example
ACME_EMAIL=your-email@example.com
APPLICATION_BASE_URL=https://snad-app.vercel.app
SANAD_CORS_ALLOWED_ORIGINS=https://snad-app.vercel.app
```

تحقق من عدم وجود placeholders:

```bash
if grep -n 'REPLACE_WITH\|example.com' deploy/oracle/.env; then
  echo 'Resolve all placeholders before deployment.'
  exit 1
fi
```

تحقق من Compose دون إظهار الأسرار في السجل:

```bash
docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  config --quiet
```

## بوابة القبول G3

- [ ] `.env` صلاحياته `600`.
- [ ] لا توجد أسرار في GitHub.
- [ ] `docker compose config --quiet` ناجح.
- [ ] CORS يشمل واجهة Vercel الفعلية فقط أو النطاقات المعتمدة.

---

# المرحلة 4 — بناء الصورة على ARM64

```bash
cd /opt/snad
docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  build --pull backend
```

اختبر معمارية الصورة:

```bash
docker image inspect sanad-backend:oracle --format '{{.Architecture}}'
```

المتوقع: `arm64`.

## بوابة القبول G4

- [ ] Maven build ناجح.
- [ ] صورة الباك إند `arm64`.
- [ ] لا توجد أخطاء اعتماد native مقيّدة بـAMD64.

---

# المرحلة 5 — تشغيل PostgreSQL الهدف فقط

```bash
docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  up -d postgres

docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  ps
```

تحقق أن قاعدة البيانات ليست مكشوفة:

```bash
sudo ss -lntup | grep 5432 && exit 1 || true
```

> غياب المنفذ 5432 من واجهة المضيف هو السلوك المطلوب.

## بوابة القبول G5

- [ ] PostgreSQL `healthy`.
- [ ] لا يوجد استماع عام على 5432.
- [ ] volume `sanad_oracle_pgdata` موجود.

---

# المرحلة 6 — النقل التجريبي لقاعدة البيانات

هذه المرحلة لا تتطلب إيقاف Render. الغرض قياس مدة التصدير والاستعادة واكتشاف أخطاء Flyway أو البيانات.

```bash
cd /opt/snad
chmod +x deploy/oracle/scripts/*.sh
sudo -E BACKUP_DIR=/var/backups/snad/postgres \
  deploy/oracle/scripts/migrate-postgres-from-render.sh
```

عند الطلب، الصق `Render External Database URL` في الإدخال المخفي. لا تحفظه في shell history.

بعد الاستعادة:

```bash
docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  exec -T postgres sh -ec \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from flyway_schema_history;"'
```

قارن على الأقل:

- عدد جداول `public`.
- عدد سجلات `flyway_schema_history`.
- عدد المستأجرين والمستخدمين والسجلات الأساسية.
- أحدث timestamps في الجداول عالية النشاط.

## بوابة القبول G6

- [ ] dump صالح ويمكن فتح قائمته بـ`pg_restore --list`.
- [ ] الاستعادة انتهت دون خطأ.
- [ ] فحوص الأعداد والعينات متطابقة.
- [ ] زمن النقل مسجل لتحديد نافذة الصيانة.

---

# المرحلة 7 — تشغيل الباك إند داخليًا

```bash
docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  up -d backend

docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  logs -f --tail=200 backend
```

اختبار محلي على الخادم:

```bash
curl -fsS http://127.0.0.1:18080/actuator/health
```

المتوقع:

```json
{"status":"UP"}
```

اختبر واجهات حرجة قبل DNS:

- تسجيل الدخول.
- تحديث access/refresh tokens.
- tenant isolation.
- قراءة بيانات العميل.
- العمليات الكتابية الأساسية.
- الإشعارات/البريد.
- عدم وجود أخطاء Flyway أو Hibernate validation.

## بوابة القبول G7

- [ ] health = UP.
- [ ] لا يوجد restart loop.
- [ ] الذاكرة مستقرة.
- [ ] الاختبارات الوظيفية الحرجة ناجحة.

---

# المرحلة 8 — DNS وHTTPS التجريبي

أنشئ سجل DNS:

```text
A  api.your-domain.example  -> ORACLE_PUBLIC_IP
TTL 300
```

بعد انتشار DNS:

```bash
dig +short api.your-domain.example
```

شغّل Caddy:

```bash
docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  up -d caddy

docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  logs -f --tail=100 caddy
```

اختبر:

```bash
curl -fsS https://api.your-domain.example/actuator/health
curl -I https://api.your-domain.example/actuator/health
```

## بوابة القبول G8

- [ ] الشهادة صالحة.
- [ ] HTTP يتحول إلى HTTPS.
- [ ] health العام ناجح.
- [ ] 5432 و8080 غير مكشوفين من الإنترنت.

---

# المرحلة 9 — اختبار تكامل الواجهة

حدّث بيئة Preview في Vercel لاستخدام Oracle API أولًا، وليس Production.

نفّذ سيناريو الاختبار:

1. تسجيل دخول وخروج.
2. refresh token بعد انتهاء/تجديد access token.
3. cookies تعمل مع `Secure` و`SameSite=None` عند اختلاف النطاق.
4. لا توجد أخطاء CORS.
5. الصفحات الإدارية والبيانات الأساسية تعمل.
6. عمليات إنشاء/تعديل/حذف تجريبية.
7. الطلبات المتزامنة لا تسبب connection-pool exhaustion.
8. رسائل البريد والاستعادة الأمنية تعمل.

راقب أثناء الاختبار:

```bash
docker stats --no-stream
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.oracle.yml ps
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.oracle.yml logs --since=30m backend postgres caddy
```

## بوابة القبول G9

- [ ] اختبارات الواجهة ناجحة.
- [ ] CORS/cookies ناجحة.
- [ ] لا أخطاء 5xx غير مفسرة.
- [ ] لا تسرب اتصالات PostgreSQL.
- [ ] مراقبة الموارد ضمن الحدود.

---

# المرحلة 10 — التحويل النهائي Cutover

## قبل النافذة

- أبقِ Render يعمل.
- أوقف أي نشر تلقائي أو تغييرات schema.
- أعلن نافذة صيانة قصيرة.
- خذ نسخة احتياطية نهائية من Oracle قبل الاستعادة النهائية إن كانت تحتوي بيانات اختبار مطلوبة.

## أثناء النافذة

1. حوّل الواجهة مؤقتًا إلى وضع صيانة أو امنع العمليات الكتابية.
2. تأكد من توقف الكتابة إلى Render.
3. أوقف backend على Oracle واترك PostgreSQL يعمل:

```bash
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.oracle.yml stop backend
```

4. نفّذ تصديرًا واستعادة نهائيين:

```bash
sudo -E BACKUP_DIR=/var/backups/snad/postgres \
  deploy/oracle/scripts/migrate-postgres-from-render.sh
```

5. شغّل كامل stack:

```bash
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.oracle.yml up -d --build
```

6. نفّذ smoke tests.
7. غيّر Production API URL في Vercel إلى Oracle endpoint.
8. أزل وضع الصيانة.
9. راقب لمدة 24–72 ساعة قبل إيقاف Render.

## بوابة القبول G10

- [ ] آخر timestamps والأعداد متطابقة.
- [ ] Production frontend يستخدم Oracle.
- [ ] الدخول والعمليات الكتابية ناجحة.
- [ ] لا ارتفاع مستمر في 5xx.
- [ ] Render ما زال متاحًا للرجوع.

---

# خطة الرجوع Rollback

يتم الرجوع فورًا عند أحد الشروط:

- فشل تسجيل الدخول أو refresh token.
- فساد أو نقص بيانات.
- أخطاء Flyway/Hibernate تمنع التشغيل.
- أخطاء 5xx حرجة أو restart loop.
- مشكلة HTTPS/CORS لا يمكن حلها داخل النافذة.

## خطوات الرجوع

1. أعد Production API URL في Vercel إلى Render.
2. أوقف الكتابة على Oracle لمنع تباعد إضافي.
3. تأكد أن Render backend وقاعدة بياناته يعملان.
4. أعد اختبار تسجيل الدخول والعمليات الأساسية على Render.
5. احتفظ بنسخة Oracle وlogs للتحليل.
6. لا تحاول دمج كتابات Oracle وRender يدويًا دون خطة data reconciliation.

> إذا حدثت كتابات جديدة على Oracle بعد التحويل، فإن الرجوع البسيط إلى Render قد يفقد هذه الكتابات. لذلك يجب تقليل نافذة القرار والاحتفاظ بوضع صيانة أثناء التحقق النهائي.

---

# النسخ الاحتياطي اليومي

اختبار يدوي:

```bash
sudo BACKUP_DIR=/var/backups/snad/postgres \
  RETENTION_DAYS=7 \
  /opt/snad/deploy/oracle/scripts/backup-postgres.sh
```

Cron يومي عند 02:15 UTC:

```bash
sudo crontab -e
```

```cron
15 2 * * * BACKUP_DIR=/var/backups/snad/postgres RETENTION_DAYS=7 /opt/snad/deploy/oracle/scripts/backup-postgres.sh >> /var/log/snad-backup.log 2>&1
```

النسخة على نفس الخادم ليست Disaster Recovery كاملة. استخدم أيضًا Oracle Block/Boot Volume Backup ضمن الحدود المجانية المتاحة، واحتفظ بنسخة مشفرة خارج الخادم عند توفر وجهة مجانية موثوقة.

اختبر الاستعادة دوريًا؛ وجود ملف dump فقط لا يثبت قابليته للاستعادة.

---

# تشغيل وتحديث النظام لاحقًا

```bash
cd /opt/snad
git fetch origin
git checkout infra/oracle-cloud-migration
git pull --ff-only

docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  build --pull backend

docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  up -d

docker compose \
  --env-file deploy/oracle/.env \
  -f deploy/oracle/docker-compose.oracle.yml \
  ps
```

قبل كل تحديث إنتاجي:

1. backup verified.
2. مراجعة Flyway migrations.
3. build ناجح.
4. health check ناجح.
5. rollback commit معروف.

---

# أوامر التشخيص

```bash
# حالة الخدمات
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.oracle.yml ps

# السجلات
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.oracle.yml logs --tail=200 backend
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.oracle.yml logs --tail=200 postgres
docker compose --env-file deploy/oracle/.env -f deploy/oracle/docker-compose.oracle.yml logs --tail=200 caddy

# الموارد
docker stats --no-stream
free -h
df -h

# المنافذ
sudo ss -lntup

# Health
curl -fsS http://127.0.0.1:18080/actuator/health
curl -fsS https://$SANAD_API_DOMAIN/actuator/health
```

---

# تعريف الاكتمال

لا يُعد الانتقال مكتملًا إلا عند تحقق جميع النقاط:

- [ ] Oracle instance موسوم Always Free Eligible.
- [ ] Compose يعمل على ARM64.
- [ ] PostgreSQL persistent وغير مكشوف.
- [ ] HTTPS صالح.
- [ ] migration rehearsal ناجحة.
- [ ] final migration ناجحة.
- [ ] اختبارات الدخول وtenant isolation والعمليات الكتابية ناجحة.
- [ ] backup يومي واختبار استعادة موثق.
- [ ] مراقبة 24–72 ساعة دون عطل حرج.
- [ ] إغلاق Render بعد نهاية فترة الرجوع فقط.
