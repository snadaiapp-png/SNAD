/**
 * SNAD i18n — Arabic (ar) translation dictionary
 * ----------------------------------------------------------------------------
 * Arabic is the default locale. Every key here MUST have a matching entry
 * in `en.ts`. The CI script `check-i18n-keys.py` enforces key parity.
 *
 * Naming convention:
 *   <domain>.<context>.<item>
 *   e.g. auth.login.title, nav.workspace, form.validation.required
 */
import type { TranslationDictionary } from "../types";

export const ar: TranslationDictionary = {
  // === Brand ===
  "brand.name": "سند",
  "brand.fullName": "نظام تشغيل الأعمال سند",
  "brand.tagline": "منصة ذكاء الأعمال الموحدة",

  // === Auth — Login ===
  "auth.login.title": "تسجيل الدخول",
  "auth.login.subtitle": "ادخل بياناتك للوصول إلى مساحة العمل",
  "auth.login.email": "البريد الإلكتروني",
  "auth.login.emailPlaceholder": "name@company.com",
  "auth.login.password": "كلمة المرور",
  "auth.login.passwordPlaceholder": "••••••••",
  "auth.login.submit": "تسجيل الدخول",
  "auth.login.submitting": "جارٍ تسجيل الدخول…",
  "auth.login.forgotPassword": "نسيت كلمة المرور؟",
  "auth.login.rememberMe": "تذكّرني",

  // === Auth — Tenant Picker ===
  "auth.tenant.title": "اختر المؤسسة",
  "auth.tenant.subtitle": "لديك صلاحية على أكثر من مؤسسة. اختر واحدة للمتابعة.",
  "auth.tenant.select": "اختر المؤسسة",
  "auth.tenant.continue": "متابعة",
  "auth.tenant.cancel": "إلغاء",

  // === Auth — Credential Rotation ===
  "auth.rotation.title": "تحديث بيانات الاعتماد",
  "auth.rotation.subtitle": "يرجى تعيين كلمة مرور جديدة لإكمال تسجيل الدخول.",
  "auth.rotation.currentPassword": "كلمة المرور الحالية",
  "auth.rotation.newPassword": "كلمة المرور الجديدة",
  "auth.rotation.confirmPassword": "تأكيد كلمة المرور",
  "auth.rotation.submit": "تحديث وكلمة المرور",
  "auth.rotation.cancel": "إلغاء",

  // === Auth — Errors ===
  "auth.error.invalidCredentials": "البريد الإلكتروني أو كلمة المرور غير صحيحة.",
  "auth.error.network": "تعذّر الاتصال بالخادم. تحقق من اتصالك بالإنترنت.",
  "auth.error.unknown": "حدث خطأ غير متوقع. حاول مرة أخرى.",
  "auth.error.expired": "انتهت جلستك. سجّل الدخول مرة أخرى.",
  "auth.error.rateLimited": "محاولات كثيرة. حاول بعد دقيقة.",

  // === Auth — Loading ===
  "auth.loading.restoring": "جارٍ استعادة الجلسة…",
  "auth.loading.redirecting": "جارٍ التحويل إلى مساحة العمل…",

  // === Navigation ===
  "nav.workspace": "مساحة العمل",
  "nav.controlPlane": "لوحة التحكم",
  "nav.crm": "إدارة العملاء",
  "nav.settings": "الإعدادات",
  "nav.profile": "الملف الشخصي",
  "nav.logout": "تسجيل الخروج",

  // === Workspace ===
  "workspace.title": "مساحة العمل",
  "workspace.welcome": "أهلاً، {name}",
  "workspace.overview": "نظرة عامة",
  "workspace.recentActivity": "النشاط الأخير",
  "workspace.quickActions": "إجراءات سريعة",
  "workspace.stats.totalMembers": "إجمالي الأعضاء",
  "workspace.stats.activeTenants": "المؤسسات النشطة",
  "workspace.stats.pendingTasks": "المهام المعلّقة",

  // === Control Plane ===
  "controlPlane.title": "لوحة التحكم",
  "controlPlane.tenants": "المؤسسات",
  "controlPlane.users": "المستخدمون",
  "controlPlane.roles": "الأدوار",
  "controlPlane.auditLog": "سجل التدقيق",
  "controlPlane.settings": "الإعدادات",

  // === CRM ===
  "crm.title": "إدارة العملاء",
  "crm.contacts": "جهات الاتصال",
  "crm.deals": "الصفقات",
  "crm.pipeline": "خط الصفقات",
  "crm.activities": "الأنشطة",

  // === Forms — Labels ===
  "form.label.email": "البريد الإلكتروني",
  "form.label.password": "كلمة المرور",
  "form.label.name": "الاسم",
  "form.label.phone": "رقم الهاتف",
  "form.label.organization": "المؤسسة",
  "form.label.role": "الدور",
  "form.label.status": "الحالة",
  "form.label.createdAt": "تاريخ الإنشاء",
  "form.label.updatedAt": "تاريخ التحديث",

  // === Forms — Actions ===
  "form.action.save": "حفظ",
  "form.action.cancel": "إلغاء",
  "form.action.delete": "حذف",
  "form.action.edit": "تعديل",
  "form.action.create": "إنشاء",
  "form.action.update": "تحديث",
  "form.action.confirm": "تأكيد",
  "form.action.back": "رجوع",
  "form.action.next": "التالي",
  "form.action.previous": "السابق",

  // === Form Validation ===
  "form.validation.required": "هذا الحقل مطلوب.",
  "form.validation.email": "أدخل بريداً إلكترونياً صالحاً.",
  "form.validation.minLength": "يجب أن يكون {min} أحرف على الأقل.",
  "form.validation.maxLength": "يجب ألا يتجاوز {max} حرفاً.",
  "form.validation.passwordMatch": "كلمتا المرور غير متطابقتين.",
  "form.validation.phone": "أدخل رقم هاتف صالح.",

  // === Loading States ===
  "loading.default": "جارٍ التحميل…",
  "loading.data": "جارٍ تحميل البيانات…",
  "loading.saving": "جارٍ الحفظ…",
  "loading.processing": "جارٍ المعالجة…",

  // === Errors ===
  "error.title": "حدث خطأ",
  "error.generic": "حدث خطأ غير متوقع. حاول مرة أخرى لاحقاً.",
  "error.notFound": "الصفحة غير موجودة.",
  "error.unauthorized": "غير مصرّح لك بالوصول.",
  "error.forbidden": "ليست لديك صلاحية كافية.",
  "error.server": "خطأ في الخادم. حاول مرة أخرى لاحقاً.",
  "error.network": "تعذّر الاتصال بالخادم.",
  "error.retry": "إعادة المحاولة",

  // === Empty States ===
  "empty.default": "لا توجد بيانات لعرضها.",
  "empty.search": "لا توجد نتائج مطابقة لبحثك.",
  "empty.list": "القائمة فارغة.",
  "empty.createFirst": "ابدأ بإنشاء أول عنصر.",

  // === Toasts ===
  "toast.success": "تمت العملية بنجاح.",
  "toast.error": "فشلت العملية.",
  "toast.saved": "تم الحفظ بنجاح.",
  "toast.deleted": "تم الحذف بنجاح.",
  "toast.updated": "تم التحديث بنجاح.",
  "toast.created": "تم الإنشاء بنجاح.",

  // === Modals ===
  "modal.close": "إغلاق",
  "modal.confirm": "تأكيد",
  "modal.confirmDelete": "تأكيد الحذف",
  "modal.deleteWarning": "هذا الإجراء لا يمكن التراجع عنه. هل أنت متأكد؟",
  "modal.cancel": "إلغاء",

  // === Tables ===
  "table.actions": "إجراءات",
  "table.rowsPerPage": "صفوف لكل صفحة",
  "table.page": "صفحة",
  "table.of": "من",
  "table.empty": "لا توجد بيانات.",
  "table.search": "بحث…",
  "table.sortBy": "ترتيب حسب",
  "table.filter": "تصفية",

  // === Filters ===
  "filter.title": "تصفية",
  "filter.apply": "تطبيق",
  "filter.clear": "مسح",
  "filter.clearAll": "مسح الكل",
  "filter.active": "نشط",
  "filter.inactive": "غير نشط",
  "filter.all": "الكل",

  // === Theme & Language (switcher labels) ===
  "theme.label": "المظهر",
  "theme.light": "فاتح",
  "theme.dark": "داكن",
  "theme.system": "النظام",
  "language.label": "اللغة",
  "language.arabic": "العربية",
  "language.english": "English",

  // === Common ===
  "common.yes": "نعم",
  "common.no": "لا",
  "common.ok": "موافق",
  "common.close": "إغلاق",
  "common.back": "رجوع",
  "common.next": "التالي",
  "common.previous": "السابق",
  "common.search": "بحث",
  "common.actions": "إجراءات",
  "common.optional": "اختياري",
  "common.required": "مطلوب",

  // === Login Form (migrated from hardcoded strings) ===
  "auth.login.welcomeTitle": "مرحبًا بعودتك",
  "auth.login.welcomeSubtitle": "سجّل دخولك للوصول إلى منصة تشغيل الأعمال.",
  "auth.login.emailRequired": "البريد الإلكتروني مطلوب.",
  "auth.login.emailInvalid": "صيغة البريد الإلكتروني غير صالحة.",
  "auth.login.passwordRequired": "كلمة المرور مطلوبة.",
  "auth.login.sessionExpiredTitle": "انتهت الجلسة",
  "auth.login.sessionExpiredMessage": "انتهت صلاحية جلستك. يرجى تسجيل الدخول مرة أخرى.",
  "auth.login.logoAlt": "شعار سند — SNAD Business Operating System",
  "auth.login.showPassword": "إظهار كلمة المرور",
  "auth.login.hidePassword": "إخفاء كلمة المرور",
  "auth.login.goToWorkspace": "الذهاب إلى مساحة العمل",
  "auth.login.helpLink": "تحتاج مساعدة في الدخول؟",
  "auth.login.helpText": "تواصل مع مسؤول النظام أو فريق الدعم لاستعادة الوصول إلى حسابك. إذا استلمت رابط استرداد آمنًا، افتح الرابط نفسه لإكمال العملية.",

  // === Tenant Picker (migrated) ===
  "auth.tenant.welcomeTitle": "اختيار مساحة العمل",
  "auth.tenant.welcomeSubtitle": "البريد مرتبط بأكثر من مساحة عمل. اختر المساحة المطلوبة.",
  "auth.tenant.workspaceLabel": "مساحات العمل",
  "auth.tenant.workspacePrefix": "مساحة عمل",
  "auth.tenant.entering": "جارٍ الدخول…",
  "auth.tenant.backToLogin": "العودة إلى تسجيل الدخول",

  // === Workspace (migrated) ===
  "workspace.defaultUser": "المستخدم",
  "workspace.loginSuccess": "تم تسجيل الدخول بنجاح",
  "workspace.userInfo": "المستخدم",
  "workspace.tenantInfo": "مساحة العمل",
  "workspace.sessionStatus": "حالة الجلسة",
  "workspace.sessionActive": "نشطة",
  "workspace.openControlPlane": "فتح مركز الإدارة العليا",
};
