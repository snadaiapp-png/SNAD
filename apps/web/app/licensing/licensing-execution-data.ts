/**
 * Licensing Execution Data
 * ------------------------
 * Business data for Licensing execution groups and tasks.
 *
 * IMPORTANT: This file contains ONLY business data.
 * All execution logic (types, calculators, validators, constants)
 * MUST be imported from the shared framework: @/lib/execution
 */

import type {
  GroupStatus,
  TaskType,
  TaskPriority,
  TaskStatus,
} from "../../lib/execution";

// ── Licensing-Specific Task Type ───────────────────────────────────────────

/**
 * Licensing Task — Business data for a licensing task.
 *
 * NOTE: This is NOT an ExecutionTask. The LicensingExecutionProvider
 * converts this to ExecutionTask when providing data to the framework.
 */
export interface LicensingTask {
  id: string;
  number: string;
  nameAr: string;
  nameEn: string;
  groupCode: string;
  descriptionAr: string;
  descriptionEn: string;
  type: TaskType;
  priority: TaskPriority;
  status: TaskStatus;
  dependencies: string[];
  acceptanceCriteriaAr: string;
  implementationNotesAr: string;
}

// ── Licensing Execution Groups ─────────────────────────────────────────────

/**
 * Licensing Execution Groups — Business data for group definitions.
 *
 * NOTE: This is NOT an ExecutionGroup[]. The LicensingExecutionProvider
 * converts this to ExecutionGroup[] when providing data to the framework.
 */
export const LICENSING_GROUP_DATA = [
  {
    code: "G0",
    titleAr: "الأساس والنمط",
    titleEn: "Foundation & License Models",
    purposeAr: "تحديد نماذج التراخيص وقواعد التسعير والهيكل الأساسي للنظام.",
    purposeEn: "Define license models, pricing rules, and foundational system structure.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: [],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G1",
    titleAr: "الميزات الأساسية",
    titleEn: "Core Features — Generation & Validation",
    purposeAr: "تنفيذ توليد التراخيص والتحقق منها وأمان التشفير.",
    purposeEn: "Implement license generation, validation, and cryptographic security.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G0"],
    canParallelizeWith: ["G2"],
    stageReport: null,
  },
  {
    code: "G2",
    titleAr: "إدارة دورة حياة الترخيص",
    titleEn: "License Lifecycle & Renewal Management",
    purposeAr: "إدارة التجديد والإلغاء والانتهاء والامتداد لدورة حياة الترخيص.",
    purposeEn: "Manage renewal, cancellation, expiry, and extension of license lifecycle.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G0"],
    canParallelizeWith: ["G1"],
    stageReport: null,
  },
  {
    code: "G3",
    titleAr: "التكامل والتقارير",
    titleEn: "Integration & Reporting",
    purposeAr: "ربط التراخيص مع الاشتراكات وإنشاء تقارير الامتثال والاستخدام.",
    purposeEn: "Integrate licenses with subscriptions and generate compliance and usage reports.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1", "G2"],
    canParallelizeWith: [],
    stageReport: null,
  },
];

// ── Licensing Tasks ────────────────────────────────────────────────────────

export const LICENSING_TASKS: LicensingTask[] = [
  // ── G0: Foundation & License Models ────────────────────────────────────────
  {
    id: "G0-T01",
    number: "G0-01",
    nameAr: "تحديد نماذج التراخيص",
    nameEn: "Define license models",
    groupCode: "G0",
    descriptionAr: "تحديد أنواع التراخيص (سنوية، شهرية، مدى الحياة) وخصائص كل نموذج",
    descriptionEn: "Define license types (annual, monthly, lifetime) and properties for each model",
    type: "Design",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: [],
    acceptanceCriteriaAr: "3 نماذج تراخيص محددة بوضوح مع خصائصها",
    implementationNotesAr: "يجب أن يدعم النظام مرونة في إضافة نماذج جديدة",
  },
  {
    id: "G0-T02",
    number: "G0-02",
    nameAr: "تصميم هيكل قاعدة البيانات للتراخيص",
    nameEn: "Design licensing database schema",
    groupCode: "G0",
    descriptionAr: "إنشاء جداول licenses, license_models, pricing_tiers مع العلاقات",
    descriptionEn: "Create licenses, license_models, pricing_tiers tables with relationships",
    type: "Database",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T01"],
    acceptanceCriteriaAr: "3 جداول أساسية مع tenant_id وقيود التحقق",
    implementationNotesAr: "يتطلب عزل المستأجرين وفهرسة performance",
  },
  {
    id: "G0-T03",
    number: "G0-03",
    nameAr: "تحديد قواعد التسعير",
    nameEn: "Define pricing rules engine",
    groupCode: "G0",
    descriptionAr: "تنفيذ محرك قواعد التسعير مع دعم الخصومات والعروض",
    descriptionEn: "Implement pricing rules engine with discount and promotion support",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G0-T02"],
    acceptanceCriteriaAr: "محرك التسعير يدعم 3 نماذج وخصومات مخصصة",
    implementationNotesAr: "يجب أن يكون قابل للتوسع لإضافة قواعد جديدة",
  },
  {
    id: "G0-T04",
    number: "G0-04",
    nameAr: "إنشاء تقرير مرحلة G0",
    nameEn: "Create G0 stage report",
    groupCode: "G0",
    descriptionAr: "تقرير شامل لمرحلة الأساس يغطي النماذج والقاعدة والتسعير",
    descriptionEn: "Comprehensive foundation stage report covering models, schema, and pricing",
    type: "Report",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T01", "G0-T02", "G0-T03"],
    acceptanceCriteriaAr: "تقرير G0 كامل يوثق جميع القرارات التصميمية",
    implementationNotesAr: "يجب أن يشمل مخطط قاعدة البيانات وقواعد التسعير",
  },

  // ── G1: Core Features — Generation & Validation ───────────────────────────
  {
    id: "G1-T01",
    number: "G1-01",
    nameAr: "تنفيذ خوارزمية توليد التراخيص",
    nameEn: "Implement license generation algorithm",
    groupCode: "G1",
    descriptionAr: "خوارزمية توليد مفاتيح التراخيص الفريدة مع التشفير",
    descriptionEn: "Algorithm for generating unique license keys with encryption",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T02"],
    acceptanceCriteriaAr: "مفتاح ترخيص فريد لكل عملية شراء",
    implementationNotesAr: "يجب أن يستخدم تشفير قوي ولا تتكرر المفاتيح",
  },
  {
    id: "G1-T02",
    number: "G1-02",
    nameAr: "تنفيذ نظام التحقق من التراخيص",
    nameEn: "Implement license validation system",
    groupCode: "G1",
    descriptionAr: "API للتحقق من صحة الترخيص وصلاحيته وحالة انتهاء الصلاحية",
    descriptionEn: "API for validating license validity, entitlements, and expiry status",
    type: "API",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G1-T01"],
    acceptanceCriteriaAr: "التحقق يشمل الحالة والصلاحيات وانتهاء الصلاحية",
    implementationNotesAr: "يجب أن يكون سريعاً (< 100ms) للاستخدام في الوقت الفعلي",
  },
  {
    id: "G1-T03",
    number: "G1-03",
    nameAr: "تنفيذ آليات أمان التراخيص",
    nameEn: "Implement license security mechanisms",
    groupCode: "G1",
    descriptionAr: "حماية ضد التلاعب والنسخ غير المصرح به وقيود عدد الأجهزة",
    descriptionEn: "Tamper protection, unauthorized copying prevention, device limits",
    type: "Security",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G1-T01"],
    acceptanceCriteriaAr: "الترخيص مقاوم للتلاعب ومقيد بعدد أجهزة محدد",
    implementationNotesAr: "يجب أن يتضمن تشفير التوقيع الرقمي",
  },
  {
    id: "G1-T04",
    number: "G1-04",
    nameAr: "إنشاء تقرير مرحلة G1",
    nameEn: "Create G1 stage report",
    groupCode: "G1",
    descriptionAr: "تقرير الميزات الأساسية يغطي التوليد والتحقق والأمان",
    descriptionEn: "Core features report covering generation, validation, and security",
    type: "Report",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G1-T01", "G1-T02", "G1-T03"],
    acceptanceCriteriaAr: "تقرير G1 يوثق آليات التوليد والتحقق",
    implementationNotesAr: "يجب أن يشمل نتائج اختبارات الأمان",
  },

  // ── G2: License Lifecycle & Renewal Management ────────────────────────────
  {
    id: "G2-T01",
    number: "G2-01",
    nameAr: "تنفيذ إدارة التجديد",
    nameEn: "Implement renewal management",
    groupCode: "G2",
    descriptionAr: "نظام تجديد تلقائي مع إشعارات قبل انتهاء الصلاحية",
    descriptionEn: "Automatic renewal system with pre-expiry notifications",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T02"],
    acceptanceCriteriaAr: "التجديد التلقائي يعمل مع إشعارات قبل 30/7/1 يوم",
    implementationNotesAr: "يجب أن يدعم التجديد اليدوي والآلي",
  },
  {
    id: "G2-T02",
    number: "G2-02",
    nameAr: "تنفيذ إدارة الإلغاء والامتداد",
    nameEn: "Implement cancellation & extension",
    groupCode: "G2",
    descriptionAr: "إلغاء التراخيص وامتداد الصلاحية مع حساب المستحققات",
    descriptionEn: "License cancellation and extension with proration calculation",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G2-T01"],
    acceptanceCriteriaAr: "الإلغاء والامتداد يحسبان المستحققات بدقة",
    implementationNotesAr: "يجب أن يدعم الإلغاء الجزئي والكامل",
  },
  {
    id: "G2-T03",
    number: "G2-03",
    nameAr: "تنفيذ تتبع حالة الترخيص",
    nameEn: "Implement license status tracking",
    groupCode: "G2",
    descriptionAr: "تتبع حالة الترخيص عبر دورة حياته كاملة مع audit trail",
    descriptionEn: "Track license status throughout lifecycle with audit trail",
    type: "Backend",
    priority: "Medium",
    status: "NOT_STARTED",
    dependencies: ["G2-T01"],
    acceptanceCriteriaAr: "سجل كامل لتغيرات حالة الترخيص",
    implementationNotesAr: "يجب أن يسجل كل تغيير مع الوقت والمستخدم",
  },
  {
    id: "G2-T04",
    number: "G2-04",
    nameAr: "إنشاء تقرير مرحلة G2",
    nameEn: "Create G2 stage report",
    groupCode: "G2",
    descriptionAr: "تقرير إدارة دورة حياة الترخيص يغطي التجديد والإلغاء والتتبع",
    descriptionEn: "License lifecycle report covering renewal, cancellation, and tracking",
    type: "Report",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G2-T01", "G2-T02", "G2-T03"],
    acceptanceCriteriaAr: "تقرير G2 يوثق آليات دورة الحياة",
    implementationNotesAr: "يجب أن يشمل تدفقات عمل الإلغاء والتجديد",
  },

  // ── G3: Integration & Reporting ───────────────────────────────────────────
  {
    id: "G3-T01",
    number: "G3-01",
    nameAr: "دمج التراخيص مع الاشتراكات",
    nameEn: "Integrate licenses with subscriptions",
    groupCode: "G3",
    descriptionAr: "ربط نظام التراخيص بنظام الاشتراكات مع تحديث تلقائي",
    descriptionEn: "Link licensing system with subscriptions with automatic sync",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G1-T02", "G2-T01"],
    acceptanceCriteriaAr: "تغيير الاشتراك يحدث حالة الترخيص تلقائياً",
    implementationNotesAr: "يجب أن يدعم المزامنة في الوقت الفعلي",
  },
  {
    id: "G3-T02",
    number: "G3-02",
    nameAr: "إنشاء تقارير الامتثال",
    nameEn: "Create compliance reports",
    groupCode: "G3",
    descriptionAr: "تقارير امتثال التراخيص و USAGE Reports للعملاء",
    descriptionEn: "License compliance and usage reports for customers",
    type: "Report",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G3-T01"],
    acceptanceCriteriaAr: "تقارير الامتثال تشمل نسبة الاستخدام والانتهاكات",
    implementationNotesAr: "يجب أن تدعم التصدير PDF و CSV",
  },
  {
    id: "G3-T03",
    number: "G3-03",
    nameAr: "إنشاء تقرير مرحلة G3 النهائي",
    nameEn: "Create final G3 stage report",
    groupCode: "G3",
    descriptionAr: "تقرير المرحلة النهائية يغطي التكامل والتقارير",
    descriptionEn: "Final stage report covering integration and reporting",
    type: "Report",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G3-T01", "G3-T02"],
    acceptanceCriteriaAr: "تقرير G3 النهائي يوثق التكامل الكامل",
    implementationNotesAr: "يجب أن يشمل ملخص شامل لكل المراحل",
  },
];
