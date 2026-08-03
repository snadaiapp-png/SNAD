/**
 * CRM Execution Data
 * ------------------
 * Business data for CRM execution groups and tasks.
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

// ── CRM-Specific Task Type ────────────────────────────────────────────────

/**
 * CRM Task — Business data for a CRM task.
 *
 * NOTE: This is NOT an ExecutionTask. The CrmExecutionProvider
 * converts this to ExecutionTask when providing data to the framework.
 */
export interface CrmTask {
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

// ── CRM Execution Groups ──────────────────────────────────────────────────

/**
 * CRM Execution Groups — Business data for group definitions.
 *
 * NOTE: This is NOT an ExecutionGroup[]. The CrmExecutionProvider
 * converts this to ExecutionGroup[] when providing data to the framework.
 */
export const CRM_GROUP_DATA = [
  {
    code: "G0",
    titleAr: "التحكم بالتنفيذ ولوحة CRM",
    titleEn: "Execution Control & CRM Dashboard",
    purposeAr: "تأسيس لوحة CRM المستقلة ولوحة متابعة التنفيذ.",
    purposeEn: "Establish the independent CRM dashboard and execution tracking board.",
    status: "APPROVED" as GroupStatus,
    dependencies: [],
    canParallelizeWith: [],
    stageReport: "G0-STAGE-REPORT-V1 — معتمد. يغطي مسار /crm، 16 تبويب، Empty States، RTL/LTR، ألوان سند.",
  },
  {
    code: "G1",
    titleAr: "قاعدة البيانات والأساس متعدد المستأجرين",
    titleEn: "Database & Multi-Tenant Foundation",
    purposeAr: "إنشاء جداول CRM الأساسية، العلاقات، العزل بين المؤسسات.",
    purposeEn: "Create core CRM tables, relations, tenant isolation.",
    status: "APPROVED" as GroupStatus,
    dependencies: ["G0"],
    canParallelizeWith: ["G2"],
    stageReport: "G1-STAGE-REPORT-V1 — معتمدة. 8 جداول، 26 فهرسًا، 8 علاقات مستأجر، عزل متعدد المستأجرين متحقق. الإنتاج: CRM-G1G2-CERTIFIED.",
  },
  {
    code: "G2",
    titleAr: "التدويل وRTL/LTR وهيكلة الواجهة",
    titleEn: "i18n, RTL/LTR & UI Shell",
    purposeAr: "دعم العربية والإنجليزية، RTL وLTR.",
    purposeEn: "Support Arabic/English, RTL/LTR.",
    status: "APPROVED" as GroupStatus,
    dependencies: ["G0"],
    canParallelizeWith: ["G1"],
    stageReport: "G2-STAGE-REPORT-V1 — معتمدة. 304 مفتاح ترجمة، RTL/LTR، رموز الهوية. الإنتاج: CRM-G1G2-CERTIFIED.",
  },
  {
    code: "G3",
    titleAr: "كيانات CRM الأساسية",
    titleEn: "Core CRM Entities",
    purposeAr: "تنفيذ Leads, Customers, Contacts, Customer 360.",
    purposeEn: "Implement Leads, Customers, Contacts, Customer 360.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1"],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G4",
    titleAr: "الفرص البيعية وخط الأنابيب",
    titleEn: "Opportunities & Pipeline",
    purposeAr: "تنفيذ الفرص البيعية، مراحل البيع، Kanban.",
    purposeEn: "Implement sales opportunities, pipeline stages, Kanban.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G3"],
    canParallelizeWith: ["G5"],
    stageReport: null,
  },
  {
    code: "G5",
    titleAr: "المهام والتحويلات والموظفين",
    titleEn: "Tasks, Transfers & Employees",
    purposeAr: "تنفيذ المهام، تحويل العملاء والفرص.",
    purposeEn: "Implement tasks, transfers.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G3"],
    canParallelizeWith: ["G4"],
    stageReport: null,
  },
  {
    code: "G6",
    titleAr: "التقارير والتحليلات",
    titleEn: "Reports & Analytics",
    purposeAr: "تنفيذ تقارير CRM ولوحات التحليل.",
    purposeEn: "Implement CRM reports and analytics.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G3", "G4", "G5"],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G7",
    titleAr: "أساس الجوال بدون اتصال",
    titleEn: "Mobile Offline Foundation",
    purposeAr: "تجهيز APIs والجداول الخاصة بتطبيق الجوال.",
    purposeEn: "Prepare mobile APIs and tables.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1", "G3"],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G8",
    titleAr: "معرفة المتصل",
    titleEn: "Caller Identification",
    purposeAr: "تجهيز معرفة بيانات العميل عند الاتصال.",
    purposeEn: "Prepare caller identification.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G7"],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G9",
    titleAr: "الذكاء الاصطناعي المجاني والمدفوع",
    titleEn: "AI CRM Free & Paid Billing",
    purposeAr: "تنفيذ طبقات الذكاء الاصطناعي المجانية والمدفوعة.",
    purposeEn: "Implement free and paid AI layers.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1", "G3"],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G10",
    titleAr: "الجودة والأمن والاعتماد",
    titleEn: "QA, Security & Acceptance",
    purposeAr: "اختبار شامل، تحقق أمني، واعتماد نهائي.",
    purposeEn: "Comprehensive testing, security verification, final acceptance.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8", "G9"],
    canParallelizeWith: [],
    stageReport: null,
  },
];

// ── CRM Tasks ─────────────────────────────────────────────────────────────

export const CRM_TASKS: CrmTask[] = [
  // ── G0: Execution Control & CRM Dashboard ──────────────────────────────────
  { id: "G0-T01", number: "G0-01", nameAr: "إنشاء مسار مركز قيادة CRM", nameEn: "Create CRM Command Center route", groupCode: "G0", descriptionAr: "إنشاء صفحة /crm", descriptionEn: "Create /crm page", type: "Frontend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "المسار /crm يفتح مركز قيادة CRM", implementationNotesAr: "تم" },
  { id: "G0-T02", number: "G0-02", nameAr: "إضافة رابط CRM في القائمة الرئيسية", nameEn: "Add CRM link in main menu", groupCode: "G0", descriptionAr: "إضافة رابط", descriptionEn: "Add link", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G0-T01"], acceptanceCriteriaAr: "الرابط يظهر", implementationNotesAr: "تم" },
  { id: "G0-T03", number: "G0-03", nameAr: "إنشاء تخطيط CRM مستقل", nameEn: "Create independent CRM layout", groupCode: "G0", descriptionAr: "تخطيط مستقل", descriptionEn: "Independent layout", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G0-T01"], acceptanceCriteriaAr: "التخطيط يحتوي شريط جانبي", implementationNotesAr: "تم" },
  { id: "G0-T04", number: "G0-04", nameAr: "إنشاء صفحة النظرة العامة", nameEn: "Create Overview page", groupCode: "G0", descriptionAr: "صفحة Overview", descriptionEn: "Overview page", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G0-T03"], acceptanceCriteriaAr: "عرض KPIs", implementationNotesAr: "تم" },
  { id: "G0-T05", number: "G0-05", nameAr: "إنشاء صفحة لوحة التنفيذ", nameEn: "Create Execution Board page", groupCode: "G0", descriptionAr: "صفحة لوحة التنفيذ", descriptionEn: "Execution Board page", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G0-T03"], acceptanceCriteriaAr: "عرض G0-G10", implementationNotesAr: "تم" },
  { id: "G0-T06", number: "G0-06", nameAr: "إنشاء صفحات CRM الفارغة", nameEn: "Create empty CRM pages", groupCode: "G0", descriptionAr: "14 تبويب فارغ", descriptionEn: "14 empty tabs", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G0-T03"], acceptanceCriteriaAr: "كل تبويب يفتح", implementationNotesAr: "تم" },
  { id: "G0-T07", number: "G0-07", nameAr: "إضافة Empty States", nameEn: "Add Empty States", groupCode: "G0", descriptionAr: "Empty States احترافية", descriptionEn: "Professional Empty States", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G0-T06"], acceptanceCriteriaAr: "مكون موحد", implementationNotesAr: "تم" },
  { id: "G0-T08", number: "G0-08", nameAr: "إضافة KPIs placeholders", nameEn: "Add KPI placeholders", groupCode: "G0", descriptionAr: "KPIs بدون بيانات وهمية", descriptionEn: "KPIs without mock data", type: "Frontend", priority: "Medium", status: "DONE", dependencies: ["G0-T04"], acceptanceCriteriaAr: "عرض شرطات", implementationNotesAr: "تم" },
  { id: "G0-T09", number: "G0-09", nameAr: "دعم RTL", nameEn: "RTL support", groupCode: "G0", descriptionAr: "RTL للعربية", descriptionEn: "RTL for Arabic", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G0-T03"], acceptanceCriteriaAr: "RTL يعمل", implementationNotesAr: "تم" },
  { id: "G0-T10", number: "G0-10", nameAr: "دعم LTR", nameEn: "LTR support", groupCode: "G0", descriptionAr: "LTR للإنجليزية", descriptionEn: "LTR for English", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G0-T09"], acceptanceCriteriaAr: "LTR يعمل", implementationNotesAr: "تم" },
  { id: "G0-T11", number: "G0-11", nameAr: "تطبيق ألوان هوية سند", nameEn: "Apply SNAD brand colors", groupCode: "G0", descriptionAr: "ألوان سند", descriptionEn: "SNAD brand colors", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G0-T03"], acceptanceCriteriaAr: "متغيرات snad-tokens.css", implementationNotesAr: "تم" },
  { id: "G0-T12", number: "G0-12", nameAr: "إنشاء سجل مجموعات التنفيذ", nameEn: "Create execution groups registry", groupCode: "G0", descriptionAr: "سجل G0-G10", descriptionEn: "G0-G10 registry", type: "Frontend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "11 مجموعة", implementationNotesAr: "تم" },
  { id: "G0-T13", number: "G0-13", nameAr: "إنشاء سجل المهام", nameEn: "Create task registry", groupCode: "G0", descriptionAr: "سجل مهام تفصيلي", descriptionEn: "Detailed task registry", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G0-T12"], acceptanceCriteriaAr: "كل مهمة لها معيار قبول", implementationNotesAr: "تم" },
  { id: "G0-T14", number: "G0-14", nameAr: "إنشاء حالة لكل مجموعة", nameEn: "Create status for each group", groupCode: "G0", descriptionAr: "حالات المجموعات", descriptionEn: "Group statuses", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G0-T12"], acceptanceCriteriaAr: "7 حالات", implementationNotesAr: "تم" },
  { id: "G0-T15", number: "G0-15", nameAr: "إنشاء تقرير G0", nameEn: "Create G0 stage report", groupCode: "G0", descriptionAr: "تقرير G0", descriptionEn: "G0 report", type: "Report", priority: "Critical", status: "DONE", dependencies: ["G0-T01", "G0-T05", "G0-T06"], acceptanceCriteriaAr: "تقرير كامل", implementationNotesAr: "تم" },

  // ── G1: Database & Multi-Tenant Foundation ──────────────────────────────────
  { id: "G1-T01", number: "G1-01", nameAr: "إنشاء 8 جداول امتداد CRM", nameEn: "Create 8 CRM extension tables", groupCode: "G1", descriptionAr: "crm_tasks, crm_notes, crm_assignments, crm_transfers, crm_audit_logs, crm_reports, crm_phone_numbers, crm_contact_lookup_index", descriptionEn: "crm_tasks, crm_notes, crm_assignments, crm_transfers, crm_audit_logs, crm_reports, crm_phone_numbers, crm_contact_lookup_index", type: "Database", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "8 جداول مع tenant_id UUID NOT NULL", implementationNotesAr: "V20260716_1, V20260716_2, V20260717_6" },
  { id: "G1-T02", number: "G1-02", nameAr: "إنشاء 26 فهرس أداء", nameEn: "Create 26 performance indexes", groupCode: "G1", descriptionAr: "فهرسة tenant_id كعمود رئيسي في جميع الفهارس", descriptionEn: "tenant_id as leading column on all indexes", type: "Database", priority: "Critical", status: "DONE", dependencies: ["G1-T01"], acceptanceCriteriaAr: "26 فهرس مع tenant_id رئيسي", implementationNotesAr: "V20260717_6, V20260718_1" },
  { id: "G1-T03", number: "G1-03", nameAr: "تطبيق عزل المستأجرين", nameEn: "Implement tenant isolation", groupCode: "G1", descriptionAr: "8 علاقات مستأجر + 2 علاقات مركبة بنفس المستأجر", descriptionEn: "8 tenant FKs + 2 same-tenant composite FKs", type: "Database", priority: "Critical", status: "DONE", dependencies: ["G1-T01"], acceptanceCriteriaAr: "PostgreSQL يرفض كتابات عبر المستأجرين", implementationNotesAr: "CrmG1TenantIsolationPostgresTest" },
  { id: "G1-T04", number: "G1-04", nameAr: "إضافة قيود التحقق وال Unique", nameEn: "Add CHECK and UNIQUE constraints", groupCode: "G1", descriptionAr: "23 قيد CHECK + 8 قيود UNIQUE", descriptionEn: "23 CHECK constraints + 8 UNIQUE constraints", type: "Database", priority: "High", status: "DONE", dependencies: ["G1-T01"], acceptanceCriteriaAr: "جميع القيود مطبقة", implementationNotesAr: "V20260717_6" },
  { id: "G1-T05", number: "G1-05", nameAr: "إنشاء ترحيلات Flyway", nameEn: "Create Flyway migrations", groupCode: "G1", descriptionAr: "4 ملفات ترحيل + ملف تسوية", descriptionEn: "4 migration files + reconciliation file", type: "Database", priority: "Critical", status: "DONE", dependencies: ["G1-T01"], acceptanceCriteriaAr: "Flyway يطبق جميع الترحيلات بنجاح", implementationNotesAr: "V20260716_1, V20260716_2, V20260717_6, V20260718_1" },
  { id: "G1-T06", number: "G1-06", nameAr: "كتابة اختبارات Testcontainers", nameEn: "Write Testcontainers tests", groupCode: "G1", descriptionAr: "4 ملفات اختبار + 22 طريقة اختبار على PostgreSQL 16", descriptionEn: "4 test files + 22 test methods on postgres:16-alpine", type: "Test", priority: "Critical", status: "DONE", dependencies: ["G1-T05"], acceptanceCriteriaAr: "جميع الاختبارات تمر على PostgreSQL 16", implementationNotesAr: "CrmPostgresMigrationTest, CrmFlywayHistoryAssertionTest, CrmG1TenantIsolationPostgresTest" },
  { id: "G1-T07", number: "G1-07", nameAr: "اختبار عزل المستأجرين عبر PostgreSQL", nameEn: "Cross-tenant isolation test via PostgreSQL", groupCode: "G1", descriptionAr: "اختبار سلوك PostgreSQL الفعلي لرفض الكتابات عبر المستأجرين", descriptionEn: "Actual PostgreSQL write rejection behavior test", type: "Test", priority: "Critical", status: "DONE", dependencies: ["G1-T06"], acceptanceCriteriaAr: "PostgreSQL يرفض INSERT/UPDATE عبر المستأجرين", implementationNotesAr: "CrmG1TenantIsolationPostgresTest — إثبات سلوكي وليس فهرسي" },
  { id: "G1-T08", number: "G1-08", nameAr: "إنشاء بوابة CI للمخطط", nameEn: "Create CI schema gate", groupCode: "G1", descriptionAr: "GitHub Actions workflow لتحقق 8 جداول + 26 فهرس + عزل المستأجرين", descriptionEn: "GitHub Actions workflow verifying 8 tables + 26 indexes + tenant isolation", type: "Security", priority: "High", status: "DONE", dependencies: ["G1-T06"], acceptanceCriteriaAr: "crm-g1-schema-isolation.yml يمر", implementationNotesAr: "crm-g1-schema-isolation.yml" },
  { id: "G1-T09", number: "G1-09", nameAr: "إنشاء بوابة إغلاق الإنتاج", nameEn: "Create production closure gate", groupCode: "G1", descriptionAr: "GitHub Actions workflow للتحقق من الإنتاج (Flyway read-only)", descriptionEn: "GitHub Actions workflow for production verification (Flyway read-only)", type: "Security", priority: "High", status: "DONE", dependencies: ["G1-T08"], acceptanceCriteriaAr: "crm-g1-production-closure.yml يمر", implementationNotesAr: "crm-g1-production-closure.yml" },
  { id: "G1-T10", number: "G1-10", nameAr: "توثيق إغلاق الإنتاج", nameEn: "Document production closure evidence", groupCode: "G1", descriptionAr: "Flyway 20260721.1 مطبق، Contact Create=201، Tenant B=404", descriptionEn: "Flyway 20260721.1 applied, Contact Create=201, Tenant B=404", type: "Report", priority: "Critical", status: "DONE", dependencies: ["G1-T09"], acceptanceCriteriaAr: "CRM-G1-FINAL-PRODUCTION-CLOSURE.md موجود", implementationNotesAr: "docs/crm/evidence/CRM-G1-FINAL-PRODUCTION-CLOSURE.md" },
  { id: "G1-T11", number: "G1-11", nameAr: "إنشاء 8 controllers ملكية", nameEn: "Create 8 ownership controllers", groupCode: "G1", descriptionAr: "8 ownership controllers مع 41 endpoint ملكية", descriptionEn: "8 ownership controllers with 41 ownership endpoints", type: "Backend", priority: "High", status: "DONE", dependencies: ["G1-T01"], acceptanceCriteriaAr: "41 endpoint ملكية يعمل", implementationNotesAr: "8 ownership controllers" },
  { id: "G1-T12", number: "G1-12", nameAr: "إنشاء تقرير المرحلة G1", nameEn: "Create G1 stage report", groupCode: "G1", descriptionAr: "تقرير المرحلة النهائي G1", descriptionEn: "Final G1 stage report", type: "Report", priority: "Critical", status: "DONE", dependencies: ["G1-T10"], acceptanceCriteriaAr: "CRM-G1-FINAL-STAGE-REPORT.md موجود", implementationNotesAr: "docs/crm/stage-reports/CRM-G1-FINAL-STAGE-REPORT.md" },

  // ── G2: i18n, RTL/LTR & UI Shell ───────────────────────────────────────────
  { id: "G2-T01", number: "G2-01", nameAr: "إنشاء CrmI18nProvider", nameEn: "Create CrmI18nProvider", groupCode: "G2", descriptionAr: "مكون React Context للتدويل", descriptionEn: "React Context component for i18n", type: "Frontend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "CrmI18nProvider يلف CRM shell", implementationNotesAr: "crm-i18n.tsx" },
  { id: "G2-T02", number: "G2-02", nameAr: "إنشاء useCrmI18n hook", nameEn: "Create useCrmI18n hook", groupCode: "G2", descriptionAr: "hook يرجع lang, dir, toggleLang, setLang, t", descriptionEn: "hook returning lang, dir, toggleLang, setLang, t", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G2-T01"], acceptanceCriteriaAr: "useCrmI18n يعمل في جميع المكونات", implementationNotesAr: "crm-i18n.tsx line 352" },
  { id: "G2-T03", number: "G2-03", nameAr: "إنشاء 304 مفتاح ترجمة ثنائي اللغة", nameEn: "Create 304 bilingual translation keys", groupCode: "G2", descriptionAr: "304 مفتاح بتنسيق { ar: string; en: string }", descriptionEn: "304 keys with { ar: string; en: string } format", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G2-T01"], acceptanceCriteriaAr: "304 مفتاح عربي/إنجليزي", implementationNotesAr: "crm-i18n.tsx" },
  { id: "G2-T04", number: "G2-04", nameAr: "تطبيق RTL/LTR مع حفظ محلي", nameEn: "Implement RTL/LTR with localStorage", groupCode: "G2", descriptionAr: "تبديل الاتجاه حسب اللغة مع حفظ في localStorage", descriptionEn: "Direction switching based on language with localStorage persistence", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G2-T02"], acceptanceCriteriaAr: "RTL يعمل للعربية، LTR للإنجليزية", implementationNotesAr: "crm-i18n.tsx line 348" },
  { id: "G2-T05", number: "G2-05", nameAr: "تطبيق رموز الهوية", nameEn: "Apply brand tokens", groupCode: "G2", descriptionAr: "snad-tokens.css و theme.css مع #0E3D38 و #D4AF37", descriptionEn: "snad-tokens.css and theme.css with #0E3D38 and #D4AF37", type: "Frontend", priority: "High", status: "DONE", dependencies: [], acceptanceCriteriaAr: "328 مرجع CSS للرموز", implementationNotesAr: "snad-tokens.css, theme.css" },
  { id: "G2-T06", number: "G2-06", nameAr: "دمج useCrmI18n في 16 ملف", nameEn: "Integrate useCrmI18n in 16 consumer files", groupCode: "G2", descriptionAr: "16 ملف تستورد useCrmI18n", descriptionEn: "16 files importing useCrmI18n", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G2-T02"], acceptanceCriteriaAr: "جميع ملفات CRM تستخدم useCrmI18n", implementationNotesAr: "16 consumer files" },
  { id: "G2-T07", number: "G2-07", nameAr: "كتابة اختبارات Vitest", nameEn: "Write Vitest tests", groupCode: "G2", descriptionAr: "4 ملفات اختبار Vitest مع CrmI18nProvider", descriptionEn: "4 Vitest test files with CrmI18nProvider", type: "Test", priority: "Critical", status: "DONE", dependencies: ["G2-T01"], acceptanceCriteriaAr: "جميع اختبارات Vitest تمر", implementationNotesAr: "4 Vitest test files" },
  { id: "G2-T08", number: "G2-08", nameAr: "كتابة اختبار Playwright RTL", nameEn: "Write Playwright RTL test", groupCode: "G2", descriptionAr: "اختبار Playwright لـ RTL", descriptionEn: "Playwright test for RTL", type: "Test", priority: "High", status: "DONE", dependencies: ["G2-T04"], acceptanceCriteriaAr: "اختبار RTL يمر", implementationNotesAr: "1 Playwright RTL test" },
  { id: "G2-T09", number: "G2-09", nameAr: "تطبيق CRM-003R keyset pagination", nameEn: "Implement CRM-003R keyset pagination", groupCode: "G2", descriptionAr: "ترجمة pagination حقيقية لـ 9 عمليات CRM v2", descriptionEn: "Real keyset pagination for 9 CRM v2 collection operations", type: "Backend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "9 عمليات pagination تعمل بـ keyset حقيقي", implementationNotesAr: "CRM-003R corrective closure" },
  { id: "G2-T10", number: "G2-10", nameAr: "إنشاء تقرير المرحلة G2", nameEn: "Create G2 stage report", groupCode: "G2", descriptionAr: "تقرير المرحلة النهائي G2", descriptionEn: "Final G2 stage report", type: "Report", priority: "Critical", status: "DONE", dependencies: ["G2-T09"], acceptanceCriteriaAr: "CRM-G2-STAGE-REPORT.md موجود", implementationNotesAr: "docs/crm/stage-reports/CRM-G2-STAGE-REPORT.md" },
];
