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
    status: "APPROVED" as GroupStatus,
    dependencies: ["G1"],
    canParallelizeWith: [],
    stageReport: "G3-STAGE-REPORT-V1 — معتمدة. 4 كيانات أساسية (Accounts, Contacts, Leads, Opportunities) مع CRUD كامل، تفاصيل ديناميكية، بحث، وعرض Kanban. الإنتاج: G3-CRM-ENTITIES-CERTIFIED.",
  },
  {
    code: "G4",
    titleAr: "الفرص البيعية وخط الأنابيب",
    titleEn: "Opportunities & Pipeline",
    purposeAr: "تنفيذ الفرص البيعية، مراحل البيع، Kanban.",
    purposeEn: "Implement sales opportunities, pipeline stages, Kanban.",
    status: "APPROVED" as GroupStatus,
    dependencies: ["G3"],
    canParallelizeWith: ["G5"],
    stageReport: "G4-STAGE-REPORT-V1 — معتمدة. عرض Kanban للفرص البيعية مع Pipelines وStages. الإنتاج: G4-PIPELINE-CERTIFIED.",
  },
  {
    code: "G5",
    titleAr: "المهام والتحويلات والموظفين",
    titleEn: "Tasks, Transfers & Employees",
    purposeAr: "تنفيذ المهام، تحويل العملاء والفرص.",
    purposeEn: "Implement tasks, transfers.",
    status: "APPROVED" as GroupStatus,
    dependencies: ["G3"],
    canParallelizeWith: ["G4"],
    stageReport: "G5-STAGE-REPORT-V1 — معتمدة. صفحة المهام مع CRUD كامل وفلترة الحالة (OPEN/IN_PROGRESS/COMPLETED/CANCELLED). الإنتاج: G5-TASKS-CERTIFIED.",
  },
  {
    code: "G6",
    titleAr: "التقارير والتحليلات",
    titleEn: "Reports & Analytics",
    purposeAr: "تنفيذ تقارير CRM ولوحات التحليل.",
    purposeEn: "Implement CRM reports and analytics.",
    status: "APPROVED" as GroupStatus,
    dependencies: ["G3", "G4", "G5"],
    canParallelizeWith: [],
    stageReport: "G6-STAGE-REPORT-V1 — معتمدة. لوحة تقارير CRM مع Sales Pipeline وLead Analytics وActivity Summary. الإنتاج: G6-REPORTS-CERTIFIED.",
  },
  {
    code: "G7",
    titleAr: "أساس الجوال بدون اتصال",
    titleEn: "Mobile Offline Foundation",
    purposeAr: "تجهيز APIs والجداول الخاصة بتطبيق الجوال.",
    purposeEn: "Prepare mobile APIs and tables.",
    status: "APPROVED" as GroupStatus,
    dependencies: ["G1", "G3"],
    canParallelizeWith: [],
    stageReport: "G7-STAGE-REPORT-V1 — معتمدة. أساس الجوال بدون اتصال: مزامنة دفع/سحب بأعمدة مُسموحة، تعارضات عبر ETag/If-Match (412)، قسائم/تفريغ، وعزل RLS لكل مستأجر. الإنتاج: G7-MOBILE-FOUNDATION-CERTIFIED.",
  },
  {
    code: "G8",
    titleAr: "معرفة المتصل",
    titleEn: "Caller Identification",
    purposeAr: "تجهيز معرفة بيانات العميل عند الاتصال.",
    purposeEn: "Prepare caller identification.",
    status: "IN_PROGRESS" as GroupStatus,
    dependencies: ["G7"],
    canParallelizeWith: [],
    stageReport: "G8-STAGE-REPORT-V5 (2026-08-21) — EXECUTION BOARD RECONCILIATION: Tracks A-D = COMPLETE ومثبتة في main عبر PR 891؛ Track E Android = BLOCKED حتى إغلاق بوابة PHYSICAL_DEVICE على جهاز Android فعلي (PR 893 مفتوح، اختبارات الكود والبناء لا تستبدل الدليل الفيزيائي)؛ Tracks F iOS / G PBX-VoIP / H Security-Privacy formal closure / I Caller UI / J Test-Release-Production = NOT_STARTED كمسارات إغلاق مستقلة. التقدم المحسوب من المهام = 4/10 = 40%. G8 تبقى IN_PROGRESS وليست APPROVED/COMPLETED/CLOSED حتى إغلاق AG-01..AG-18 وTrack J.",
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
  { id: "G2-T05", number: "G2-05", nameAr: "تطبيق رموز الهوية", nameEn: "Apply brand tokens", groupCode: "G2", descriptionAr: "snad-tokens.css و theme.css مع var(--snad-color-brand-primary) و var(--snad-color-brand-accent)", descriptionEn: "snad-tokens.css and theme.css with var(--snad-color-brand-primary) and var(--snad-color-brand-accent)", type: "Frontend", priority: "High", status: "DONE", dependencies: [], acceptanceCriteriaAr: "328 مرجع CSS للرموز", implementationNotesAr: "snad-tokens.css, theme.css" },
  { id: "G2-T06", number: "G2-06", nameAr: "دمج useCrmI18n في 16 ملف", nameEn: "Integrate useCrmI18n in 16 consumer files", groupCode: "G2", descriptionAr: "16 ملف تستورد useCrmI18n", descriptionEn: "16 files importing useCrmI18n", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G2-T02"], acceptanceCriteriaAr: "جميع ملفات CRM تستخدم useCrmI18n", implementationNotesAr: "16 consumer files" },
  { id: "G2-T07", number: "G2-07", nameAr: "كتابة اختبارات Vitest", nameEn: "Write Vitest tests", groupCode: "G2", descriptionAr: "4 ملفات اختبار Vitest مع CrmI18nProvider", descriptionEn: "4 Vitest test files with CrmI18nProvider", type: "Test", priority: "Critical", status: "DONE", dependencies: ["G2-T01"], acceptanceCriteriaAr: "جميع اختبارات Vitest تمر", implementationNotesAr: "4 Vitest test files" },
  { id: "G2-T08", number: "G2-08", nameAr: "كتابة اختبار Playwright RTL", nameEn: "Write Playwright RTL test", groupCode: "G2", descriptionAr: "اختبار Playwright لـ RTL", descriptionEn: "Playwright test for RTL", type: "Test", priority: "High", status: "DONE", dependencies: ["G2-T04"], acceptanceCriteriaAr: "اختبار RTL يمر", implementationNotesAr: "1 Playwright RTL test" },
  { id: "G2-T09", number: "G2-09", nameAr: "تطبيق CRM-003R keyset pagination", nameEn: "Implement CRM-003R keyset pagination", groupCode: "G2", descriptionAr: "ترجمة pagination حقيقية لـ 9 عمليات CRM v2", descriptionEn: "Real keyset pagination for 9 CRM v2 collection operations", type: "Backend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "9 عمليات pagination تعمل بـ keyset حقيقي", implementationNotesAr: "CRM-003R corrective closure" },
  { id: "G2-T10", number: "G2-10", nameAr: "إنشاء تقرير المرحلة G2", nameEn: "Create G2 stage report", groupCode: "G2", descriptionAr: "تقرير المرحلة النهائي G2", descriptionEn: "Final G2 stage report", type: "Report", priority: "Critical", status: "DONE", dependencies: ["G2-T09"], acceptanceCriteriaAr: "CRM-G2-STAGE-REPORT.md موجود", implementationNotesAr: "docs/crm/stage-reports/CRM-G2-STAGE-REPORT.md" },

  // ── G3: Core CRM Entities ────────────────────────────────────────────────
  { id: "G3-T01", number: "G3-01", nameAr: "إنشاء صفحة الحسابات", nameEn: "Create Accounts page", groupCode: "G3", descriptionAr: "صفحة /crm/accounts مع CRUD كامل", descriptionEn: "Accounts page with full CRUD", type: "Frontend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "الحسابات تُنشأ وتُعرض وتُحدّث وتُحذف", implementationNotesAr: "crmApi.accounts() مع بحث وأرشفة" },
  { id: "G3-T02", number: "G3-02", nameAr: "إنشاء صفحة جهات الاتصال", nameEn: "Create Contacts page", groupCode: "G3", descriptionAr: "صفحة /crm/contacts مع CRUD كامل", descriptionEn: "Contacts page with full CRUD", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G3-T01"], acceptanceCriteriaAr: "جهات الاتصال تُنشأ وتُعرض وتُحدّث وتُحذف", implementationNotesAr: "crmApi.contacts() مع ربط بالحسابات" },
  { id: "G3-T03", number: "G3-03", nameAr: "إنشاء صفحة العملاء المحتملين", nameEn: "Create Leads page", groupCode: "G3", descriptionAr: "صفحة /crm/leads مع فلترة الحالة", descriptionEn: "Leads page with status filtering", type: "Frontend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "العملاء المحتملون يُعرضون مع فلترة NEW/QUALIFIED/DISQUALIFIED/CONVERTED/ARCHIVED", implementationNotesAr: "crmApi Leads مع 5 حالات" },
  { id: "G3-T04", number: "G3-04", nameAr: "إنشاء صفحة الفرص البيعية", nameEn: "Create Opportunities page", groupCode: "G3", descriptionAr: "صفحة /crm/opportunities مع عرض Kanban", descriptionEn: "Opportunities page with Kanban view", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G3-T01"], acceptanceCriteriaAr: "الفرص البيعية تُعرض بجدول وKanban", implementationNotesAr: "CrmPipelineBoard + CrmVirtualTable" },
  { id: "G3-T05", number: "G3-05", nameAr: "إنشاء صفحات التفاصيل الديناميكية", nameEn: "Create dynamic detail pages", groupCode: "G3", descriptionAr: "صفحات [accountId], [contactId], [leadId], [opportunityId]", descriptionEn: "Dynamic detail pages for all entities", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G3-T01", "G3-T02", "G3-T03", "G3-T04"], acceptanceCriteriaAr: "كل كيان له صفحة تفاصيل ديناميكية", implementationNotesAr: "4 dynamic routes مع [id]" },
  { id: "G3-T06", number: "G3-06", nameAr: "إنشاء تقرير المرحلة G3", nameEn: "Create G3 stage report", groupCode: "G3", descriptionAr: "تقرير المرحلة النهائي G3", descriptionEn: "Final G3 stage report", type: "Report", priority: "Critical", status: "DONE", dependencies: ["G3-T05"], acceptanceCriteriaAr: "G3-STAGE-REPORT-V1 موجود", implementationNotesAr: "G3-CRM-ENTITIES-CERTIFIED" },

  // ── G4: Opportunities & Pipeline ──────────────────────────────────────────
  { id: "G4-T01", number: "G4-01", nameAr: "إنشاء نظام الأنبوب البيعي", nameEn: "Create sales pipeline system", groupCode: "G4", descriptionAr: "نظام الأنبوب البيعي مع مراحل التحويل", descriptionEn: "Sales pipeline with conversion stages", type: "Backend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "الأنبوب البيعي يدعم مراحل التحويل", implementationNotesAr: "Pipeline stages مع ConversionService" },
  { id: "G4-T02", number: "G4-02", nameAr: "إنشاء نظام التحويل", nameEn: "Create conversion system", groupCode: "G4", descriptionAr: "تحويل العملاء المحتملين إلى فرص بيعية", descriptionEn: "Convert leads to opportunities", type: "Backend", priority: "Critical", status: "DONE", dependencies: ["G4-T01"], acceptanceCriteriaAr: "التحويل يُنشئ فرصة من عميل محتمل", implementationNotesAr: "Lead → Opportunity conversion" },
  { id: "G4-T03", number: "G4-03", nameAr: "إنشاء واجهة السحب والإفلات", nameEn: "Create drag-and-drop interface", groupCode: "G4", descriptionAr: "واجهة سحب وإفلات للأنبوب البيعي", descriptionEn: "Drag-and-drop for sales pipeline", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G4-T02"], acceptanceCriteriaAr: "يمكن سحب الفرص بين المراحل", implementationNotesAr: "CrmPipelineBoard مع drag-and-drop" },
  { id: "G4-T04", number: "G4-04", nameAr: "إنشاء تقرير المرحلة G4", nameEn: "Create G4 stage report", groupCode: "G4", descriptionAr: "تقرير المرحلة النهائي G4", descriptionEn: "Final G4 stage report", type: "Report", priority: "Critical", status: "DONE", dependencies: ["G4-T03"], acceptanceCriteriaAr: "G4-STAGE-REPORT-V1 موجود", implementationNotesAr: "G4-PIPELINE-CERTIFIED" },

  // ── G5: Tasks, Transfers & Employees ──────────────────────────────────────
  { id: "G5-T01", number: "G5-01", nameAr: "إنشاء نظام المهام", nameEn: "Create task system", groupCode: "G5", descriptionAr: "نظام المهام مع الأولوية والمواعيد النهائية", descriptionEn: "Task system with priority and deadlines", type: "Backend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "المهام تُنشأ وتُدار ب الأولوية والمواعيد", implementationNotesAr: "TaskService مع Priority وDueDate" },
  { id: "G5-T02", number: "G5-02", nameAr: "إنشاء نظام التحويلات", nameEn: "Create transfer system", groupCode: "G5", descriptionAr: "تحويل المهام والفرص بين المستخدمين", descriptionEn: "Transfer tasks and opportunities between users", type: "Backend", priority: "High", status: "DONE", dependencies: ["G5-T01"], acceptanceCriteriaAr: "يمكن تحويل المهام بين المستخدمين", implementationNotesAr: "TransferService مع AuditTrail" },
  { id: "G5-T03", number: "G5-03", nameAr: "إنشاء نظام الموظفين", nameEn: "Create employee system", groupCode: "G5", descriptionAr: "إدارة الموظفين والأدوار والصلاحيات", descriptionEn: "Employee management with roles and permissions", type: "Backend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "الموظفون يُدرَسون بالأدوار والصلاحيات", implementationNotesAr: "EmployeeService مع RBAC" },
  { id: "G5-T04", number: "G5-04", nameAr: "إنشاء واجهة إدارة المهام", nameEn: "Create task management UI", groupCode: "G5", descriptionAr: "واجهة لإدارة المهام والتحويلات", descriptionEn: "UI for task and transfer management", type: "Frontend", priority: "High", status: "DONE", dependencies: ["G5-T02", "G5-T03"], acceptanceCriteriaAr: "يمكن إدارة المهام والتحويلات من الواجهة", implementationNotesAr: "TaskBoard + TransferDialog" },
  { id: "G5-T05", number: "G5-05", nameAr: "إنشاء تقرير المرحلة G5", nameEn: "Create G5 stage report", groupCode: "G5", descriptionAr: "تقرير المرحلة النهائي G5", descriptionEn: "Final G5 stage report", type: "Report", priority: "Critical", status: "DONE", dependencies: ["G5-T04"], acceptanceCriteriaAr: "G5-STAGE-REPORT-V1 موجود", implementationNotesAr: "G5-TASKS-TRANSFER-EMPLOYEES-CERTIFIED" },

  // ── G6: Reports & Analytics ───────────────────────────────────────────────
  { id: "G6-T01", number: "G6-01", nameAr: "إنشاء نظام التقارير", nameEn: "Create reporting system", groupCode: "G6", descriptionAr: "نظام التقارير مع أنواع متعددة", descriptionEn: "Reporting system with multiple report types", type: "Backend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "التقارير تُنشأ بأنواع مختلفة", implementationNotesAr: "ReportService مع ReportTypes" },
  { id: "G6-T02", number: "G6-02", nameAr: "إنشاء لوحة التحليلات", nameEn: "Create analytics dashboard", groupCode: "G6", descriptionAr: "لوحة تحكم بالتحليلات مع الرسوم البيانية", descriptionEn: "Analytics dashboard with charts", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G6-T01"], acceptanceCriteriaAr: "لوحة التحليلات تعرض الرسوم البيانية", implementationNotesAr: "AnalyticsDashboard مع Charts" },
  { id: "G6-T03", number: "G6-03", nameAr: "إنشاء تصدير البيانات", nameEn: "Create data export", groupCode: "G6", descriptionAr: "تصدير البيانات بصيغ CSV وExcel", descriptionEn: "Export data in CSV and Excel formats", type: "Backend", priority: "High", status: "DONE", dependencies: ["G6-T01"], acceptanceCriteriaAr: "يمكن تصدير البيانات بـ CSV وExcel", implementationNotesAr: "ExportService مع CSV/Excel" },
  { id: "G6-T04", number: "G6-04", nameAr: "إنشاء تقرير المرحلة G6", nameEn: "Create G6 stage report", groupCode: "G6", descriptionAr: "تقرير المرحلة النهائي G6", descriptionEn: "Final G6 stage report", type: "Report", priority: "Critical", status: "DONE", dependencies: ["G6-T03"], acceptanceCriteriaAr: "G6-STAGE-REPORT-V1 موجود", implementationNotesAr: "G6-REPORTS-ANALYTICS-CERTIFIED" },

  // ── G7: Mobile Offline Foundation ─────────────────────────────────────────
  { id: "G7-T01", number: "G7-01", nameAr: "إنشاء مخطط مزامنة الجوال", nameEn: "Create mobile sync schema", groupCode: "G7", descriptionAr: "جداول وسجلات المزامنة والتعارض والأجهزة مع Flyway وعزل RLS", descriptionEn: "Mobile sync, conflict and device schema with Flyway and RLS", type: "Database", priority: "Critical", status: "DONE", dependencies: ["G1-T05"], acceptanceCriteriaAr: "تُطبق ترحيلات G7 على PostgreSQL Direct وتكون جداول المزامنة معزولة حسب tenant_id", implementationNotesAr: "V20260812_1..3 + CrmPostgresMigrationTest + RLS runtime closure" },
  { id: "G7-T02", number: "G7-02", nameAr: "تنفيذ مزامنة الدفع الآمنة", nameEn: "Implement secure push sync", groupCode: "G7", descriptionAr: "دفع تغييرات الجوال بدفعات مع allowlist للأعمدة وidempotency وعزل فشل كل mutation", descriptionEn: "Batch mobile push with column allowlists, idempotency and per-mutation failure isolation", type: "Backend", priority: "Critical", status: "DONE", dependencies: ["G7-T01"], acceptanceCriteriaAr: "تقبل عمليات push المسموحة فقط وتُرفض المدخلات غير الصالحة دون إسقاط بقية الدفعة", implementationNotesAr: "PushSyncService + G7DefectFixesTest + G7PushSyncFailureIsolationPostgresTest" },
  { id: "G7-T03", number: "G7-03", nameAr: "تنفيذ مزامنة السحب والدلتا وإعادة المزامنة", nameEn: "Implement pull, delta and full resync", groupCode: "G7", descriptionAr: "سحب incremental بالـ cursor مع delta وfull-resync وحالة المزامنة", descriptionEn: "Cursor-based incremental pull with delta, full resync and sync status", type: "Backend", priority: "Critical", status: "DONE", dependencies: ["G7-T01"], acceptanceCriteriaAr: "تعمل مسارات pull/delta/full-resync/status بعقد متسق وقابل لإعادة المحاولة", implementationNotesAr: "PullSyncService + sync controllers + production smoke probes" },
  { id: "G7-T04", number: "G7-04", nameAr: "تنفيذ كشف وحل تعارضات النسخ", nameEn: "Implement optimistic concurrency conflicts", groupCode: "G7", descriptionAr: "ETag/If-Match وversion checks وإرجاع 412 عند النسخة القديمة مع سجل تعارضات", descriptionEn: "ETag/If-Match version checks, HTTP 412 for stale writes, and conflict records", type: "Backend", priority: "Critical", status: "DONE", dependencies: ["G7-T02", "G7-T03"], acceptanceCriteriaAr: "أي تحديث stale يُرفض بـ 412 ولا يكتب فوق نسخة أحدث", implementationNotesAr: "ADR-G7-001 + ConflictService + G7DefectFixesTest SYNC-010" },
  { id: "G7-T05", number: "G7-05", nameAr: "تطبيق المصادقة وعزل المستأجر للمزامنة", nameEn: "Enforce sync auth and tenant isolation", groupCode: "G7", descriptionAr: "JWT وTenantContextPort وRBAC وسياسات FORCE RLS على عمليات المزامنة", descriptionEn: "JWT, TenantContextPort, RBAC and FORCE RLS for sync operations", type: "Security", priority: "Critical", status: "DONE", dependencies: ["G7-T01"], acceptanceCriteriaAr: "لا يمكن لمستخدم أو مستأجر الوصول إلى بيانات مزامنة مستأجر آخر وتُرفض الطلبات غير المصادق عليها", implementationNotesAr: "JwtAuthenticationFilter + TenantContextPort + V20260812_3 FORCE ROW LEVEL SECURITY" },
  { id: "G7-T06", number: "G7-06", nameAr: "تجهيز طبقة الجوال للعمل بدون اتصال", nameEn: "Deliver offline mobile client foundation", groupCode: "G7", descriptionAr: "تخزين محلي مشفر، queue للعمليات، sync client، retry/backoff واسترداد الفساد", descriptionEn: "Encrypted local storage, mutation queue, sync client, retry/backoff and corruption recovery", type: "Frontend", priority: "Critical", status: "DONE", dependencies: ["G7-T02", "G7-T03", "G7-T04"], acceptanceCriteriaAr: "يستطيع العميل العمل offline ثم مزامنة التغييرات بأمان عند عودة الاتصال", implementationNotesAr: "apps/mobile G7 offline/sync foundation + AES-256-GCM + mobile test suites" },
  { id: "G7-T07", number: "G7-07", nameAr: "إغلاق اختبارات G7 على PostgreSQL Direct", nameEn: "Close G7 runtime verification on PostgreSQL Direct", groupCode: "G7", descriptionAr: "تشغيل اختبارات backend/mobile وFlyway/RLS/sync بدون Docker أو Testcontainers", descriptionEn: "Run backend/mobile, Flyway, RLS and sync verification without Docker/Testcontainers", type: "Test", priority: "Critical", status: "DONE", dependencies: ["G7-T02", "G7-T03", "G7-T04", "G7-T05", "G7-T06"], acceptanceCriteriaAr: "جميع بوابات G7 الفعالة تمر بلا BLOCKED أو FAIL على PostgreSQL Direct", implementationNotesAr: "Post-Merge Main Verification 32356360019 + later full Maven PostgreSQL Direct regression gate" },
  { id: "G7-T08", number: "G7-08", nameAr: "اعتماد وإغلاق G7 في الإنتاج", nameEn: "Certify and close G7 in production", groupCode: "G7", descriptionAr: "توثيق RELEASE_GATE=PASS والتحقق من Render smoke ثم اعتماد المرحلة", descriptionEn: "Record RELEASE_GATE=PASS, verify production smoke, and certify the stage", type: "Report", priority: "Critical", status: "DONE", dependencies: ["G7-T07"], acceptanceCriteriaAr: "G7_MOBILE_FOUNDATION = COMPLETED / CLOSED والإنتاج يحمل G7-MOBILE-FOUNDATION-CERTIFIED", implementationNotesAr: "G7 closure addendum 2026-08-20 + Backend Production Smoke 32360346278 + G7-STAGE-REPORT-V1" },

  // ── G8: Caller Identification ─────────────────────────────────────────────
  { id: "G8-T01", number: "G8-A", nameAr: "المصدر القانوني للهواتف ومحرك المطابقة", nameEn: "Canonical data & matching engine", groupCode: "G8", descriptionAr: "مطابقة عكسية حتمية على crm_communication_methods مع تطبيع الهاتف وسياسة EXACT/AMBIGUOUS/UNKNOWN/RESTRICTED", descriptionEn: "Deterministic reverse lookup over canonical communication methods with normalization and tiered matching", type: "Backend", priority: "Critical", status: "DONE", dependencies: [], acceptanceCriteriaAr: "المطابقة حتمية ومعزولة حسب tenant ولا تستخدم fuzzy/random matching وتغطي حالات التطابق المعتمدة", implementationNotesAr: "Track A merged to main in PR 891 / commit 3acd2957; G8 baseline + EXECUTION 02/04 evidence" },
  { id: "G8-T02", number: "G8-B", nameAr: "واجهة API لمعرفة المتصل", nameEn: "Caller identification API", groupCode: "G8", descriptionAr: "POST /api/v2/crm/caller-identification/lookup مع RBAC وعزل tenant وrate limiting وإخفاء البيانات الحساسة", descriptionEn: "Caller lookup API with RBAC, tenant isolation, rate limiting and server-side masking", type: "API", priority: "Critical", status: "DONE", dependencies: ["G8-T01"], acceptanceCriteriaAr: "الـ API يمر بحالات EXACT/UNKNOWN/AMBIGUOUS/RESTRICTED ويرفض المدخلات والصلاحيات غير المسموحة دون تسريب رقم كامل", implementationNotesAr: "Track B merged to main in PR 891; committed OpenAPI includes caller-identification lookup" },
  { id: "G8-T03", number: "G8-C", nameAr: "أحداث المكالمات وإسقاطات النشاط والخط الزمني", nameEn: "Call events & CRM projections", groupCode: "G8", descriptionAr: "crm_call_events كحقيقة المكالمة مع idempotency وحالة أحادية الاتجاه وإسقاط Activity/Timeline", descriptionEn: "Call-event aggregate with idempotency, monotonic lifecycle and Activity/Timeline projections", type: "Backend", priority: "Critical", status: "DONE", dependencies: ["G8-T01", "G8-T02"], acceptanceCriteriaAr: "تسجيل الحدث مكررًا لا يضاعف النشاط ولا تتراجع الحالة وتبقى البيانات معزولة حسب tenant", implementationNotesAr: "Track C merged to main in PR 891; migrations V20260820_11..13 retained in authoritative Flyway ledger" },
  { id: "G8-T04", number: "G8-D", nameAr: "بيانات معرفة المتصل دون اتصال", nameEn: "Offline caller dataset", groupCode: "G8", descriptionAr: "إسقاط محلي مشفر مع HMAC token وdelta sync وtombstones وfull-resync وعزل tenant", descriptionEn: "Encrypted offline caller projection with HMAC lookup tokens, delta sync, tombstones and full resync", type: "Mobile", priority: "Critical", status: "DONE", dependencies: ["G8-T02", "G8-T03"], acceptanceCriteriaAr: "تعمل المطابقة المحلية EXACT/AMBIGUOUS/UNKNOWN/RESTRICTED دون اتصال مع parity مع الخادم وتطهير البيانات عند تبديل tenant أو logout", implementationNotesAr: "Track D merged to main in PR 891; shared normalization/HMAC parity vectors and mobile regression evidence" },
  { id: "G8-T05", number: "G8-E", nameAr: "تكامل Android الأصلي لمعرفة المتصل", nameEn: "Android native caller identification", groupCode: "G8", descriptionAr: "CallScreeningService وROLE_CALL_SCREENING ومسار local-first أصلي واختبارات جهاز فعلي", descriptionEn: "Native CallScreeningService, call-screening role, local-first resolver and physical-device acceptance", type: "Mobile", priority: "Critical", status: "BLOCKED", dependencies: ["G8-T04"], acceptanceCriteriaAr: "تُثبت مكالمات خلوية واردة فعلية على جهاز Android حالات foreground/background/cold/locked/network-off ضمن SLO وقيود الخصوصية", implementationNotesAr: "PR 893 contains code/build/JVM/mobile evidence; PHYSICAL_DEVICE_1 remains BLOCKED and device runtime latency is not measured, لذلك لا يُحسب COMPLETE" },
  { id: "G8-T06", number: "G8-F", nameAr: "تكامل iOS الأصلي", nameEn: "iOS platform integration", groupCode: "G8", descriptionAr: "Call Directory Extension وentitlements/App Group وآلية تحديث الدليل مع بوابة توزيع Apple", descriptionEn: "Call Directory Extension, entitlements/App Group and directory update mechanism with Apple distribution gate", type: "Mobile", priority: "High", status: "NOT_STARTED", dependencies: ["G8-T04"], acceptanceCriteriaAr: "ينفذ المسار المدعوم رسميًا على iOS أو يوثق قرار المنصة النهائي بأدلة توزيع واختبار", implementationNotesAr: "لا يوجد دليل إغلاق Track F حتى 2026-08-21" },
  { id: "G8-T07", number: "G8-G", nameAr: "حدود تكامل PBX وVoIP", nameEn: "PBX/VoIP adapter boundary", groupCode: "G8", descriptionAr: "CallSourceAdapter وحدود تحقق مزود دون ربط مزود تجاري داخل G8", descriptionEn: "CallSourceAdapter boundary and provider verification skeleton without binding a commercial provider", type: "Backend", priority: "High", status: "NOT_STARTED", dependencies: ["G8-T03"], acceptanceCriteriaAr: "توجد واجهة مزود مع تحقق أمني واختبارات دون إدخال اعتماد خاص بمزود واحد", implementationNotesAr: "لا يوجد دليل إغلاق Track G حتى 2026-08-21" },
  { id: "G8-T08", number: "G8-H", nameAr: "إغلاق الأمن والخصوصية وRLS", nameEn: "Security, privacy & RLS closure", groupCode: "G8", descriptionAr: "إغلاق مستقل لقدرات RBAC والقوالب وRLS والتنقيح وPDPL وأمن webhooks واختبارات الخصوصية", descriptionEn: "Formal closure of RBAC/templates, RLS, redaction, PDPL, webhook security and privacy tests", type: "Security", priority: "Critical", status: "NOT_STARTED", dependencies: ["G8-T01", "G8-T03", "G8-T04"], acceptanceCriteriaAr: "تغلق جميع متطلبات Track H بدليل مستقل؛ تنفيذ ضوابط جزئية داخل A-D لا يُحتسب إغلاقًا للمسار", implementationNotesAr: "ضوابط أمنية متعددة موجودة داخل A-D، لكن لا يوجد تقرير/بوابة مستقلة تثبت COMPLETE لـ Track H؛ لذلك لم يُرفع الإنجاز اصطناعيًا" },
  { id: "G8-T09", number: "G8-I", nameAr: "واجهة بطاقة المتصل وما بعد المكالمة", nameEn: "Caller UI & post-call workflow", groupCode: "G8", descriptionAr: "بطاقة متصل أساسية وتدفق المتصل المجهول وإجراءات ما بعد المكالمة", descriptionEn: "Caller card, unknown-caller flow and post-call actions", type: "Frontend", priority: "High", status: "NOT_STARTED", dependencies: ["G8-T02", "G8-T04"], acceptanceCriteriaAr: "تعمل واجهة المستخدم بالعربية والإنجليزية وتلتزم بحجب البيانات المقيدة وتدفقات ما بعد المكالمة", implementationNotesAr: "SanadCallerIdActivity في Track E بطاقة أصلية دنيا وليست إغلاق Track I الكامل" },
  { id: "G8-T10", number: "G8-J", nameAr: "الاختبار والإصدار واعتماد الإنتاج", nameEn: "Test, release & production acceptance", groupCode: "G8", descriptionAr: "مصفوفة الاختبار الكاملة وقياسات SLO والمراقبة وCI والنشر وsmoke وإغلاق AG-01..AG-18", descriptionEn: "Full test matrix, SLO measurements, observability, CI, deployment, smoke and AG-01..AG-18 closure", type: "Test", priority: "Critical", status: "NOT_STARTED", dependencies: ["G8-T01", "G8-T02", "G8-T03", "G8-T04", "G8-T05", "G8-T06", "G8-T07", "G8-T08", "G8-T09"], acceptanceCriteriaAr: "تمر AG-01..AG-18 وتثبت قياسات الأداء والإنتاج قبل تحويل G8 إلى APPROVED/CLOSED", implementationNotesAr: "بوابة الاعتماد النهائي غير منفذة؛ لا يجوز اعتماد G8 قبل Track J" },
];