# SANAD Backend — دليل التثبيت الدائم

## المشكلة: لماذا يتعطل الـ Backend تلقائياً؟

الـ backend يتعطل لأن:
- الـ SSH session تنقطع → العملية تُقتل
- لا يوجد `systemd` service أو `Windows Service`
- الـ JVM يخرج عند OOM (Out of Memory)

## الحل: تشغيل دائم بالخلفية

### الخيار 1: Windows Service (موصى به لـ Windows)

```powershell
# 1. افتح PowerShell كمسؤول (Administrator)
# 2. اذهب لمجلد المشروع
cd C:\path\to\SNAD

# 3. شغّل سكريبت التثبيت
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser -Force
.\scripts\production\sanad-backend-windows.ps1
```

**النتيجة:**
- ✅ يعمل بالخلفية دائماً
- ✅ يبدأ تلقائياً مع تشغيل الجهاز
- ✅ يعيد التشغيل تلقائياً عند الـ crash
- ✅ لا يحتاج SSH session مفتوح

### الخيار 2: Linux systemd (للخادم Linux)

```bash
# على الخادم (SSH)
sudo bash scripts/production/install-backend.sh
```

### الخيار 3: تشغيل سريع بدون تثبيت (Windows)

```cmd
# انقر مرتين على:
scripts\production\sanad-start.bat
```

---

## أوامر الإدارة — Windows Service

```powershell
# حالة الخدمة
Get-Service SanadBackend

# إيقاف
Stop-Service SanadBackend

# تشغيل
Start-Service SanadBackend

# إعادة تشغيل
Restart-Service SanadBackend

# عرض السجل
Get-Content C:\sanad-platform\logs\stdout.log -Tail 50 -Wait

# فحص الصحة
Invoke-RestMethod http://localhost:8080/actuator/health
```

## أوامر الإدارة — Linux systemd

```bash
# حالة الخدمة
sudo systemctl status sanad-backend

# إيقاف
sudo systemctl stop sanad-backend

# تشغيل
sudo systemctl start sanad-backend

# إعادة تشغيل
sudo systemctl restart sanad-backend

# عرض السجل
sudo journalctl -u sanad-backend -f

# فحص الصحة
curl http://localhost:8080/actuator/health
```

---

## المتطلبات المسبقة

1. **Java 21** مثبت على الجهاز
2. **PostgreSQL** يعمل على `localhost:5432`
3. **JAR file** منسوخ إلى:
   - Windows: `C:\sanad-platform\sanad-platform.jar`
   - Linux: `/opt/sanad-platform/sanad-platform.jar`

## بناء الـ JAR

```bash
cd apps/sanad-platform
mvn clean package -DskipTests
# الناتج: target/sanad-platform-*.jar
# انسخه إلى المسار المطلوب
```
