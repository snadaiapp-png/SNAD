export type GroupStatus = "NOT_STARTED" | "IN_PROGRESS" | "BLOCKED" | "DONE" | "NEEDS_REVIEW" | "APPROVED" | "REJECTED";
export type TaskType = "Backend" | "Frontend" | "Database" | "API" | "Security" | "Test" | "Report" | "Mobile" | "AI" | "Billing";
export type TaskPriority = "Critical" | "High" | "Medium" | "Low";
export type TaskStatus = "NOT_STARTED" | "IN_PROGRESS" | "BLOCKED" | "DONE" | "NEEDS_REVIEW" | "APPROVED";

export interface ExecutionGroup {
  code: string; titleAr: string; titleEn: string; purposeAr: string; purposeEn: string;
  status: GroupStatus; dependencies: string[]; canParallelizeWith: string[]; stageReport: string | null;
}
export interface CrmTask {
  id: string; number: string; nameAr: string; nameEn: string; groupCode: string;
  descriptionAr: string; descriptionEn: string; type: TaskType; priority: TaskPriority;
  status: TaskStatus; dependencies: string[]; acceptanceCriteriaAr: string; implementationNotesAr: string;
}

export const EXECUTION_GROUPS: ExecutionGroup[] = [
  { code: "G0", titleAr: "التحكم بالتنفيذ ولوحة CRM", titleEn: "Execution Control & CRM Dashboard", purposeAr: "تأسيس لوحة CRM المستقلة ولوحة متابعة التنفيذ.", purposeEn: "Establish the independent CRM dashboard and execution tracking board.", status: "APPROVED", dependencies: [], canParallelizeWith: [], stageReport: "G0-STAGE-REPORT-V1 — معتمد. يغطي مسار /crm، 16 تبويب، Empty States، RTL/LTR، ألوان سند." },
  { code: "G1", titleAr: "قاعدة البيانات والأساس متعدد المستأجرين", titleEn: "Database & Multi-Tenant Foundation", purposeAr: "إنشاء جداول CRM الأساسية، العلاقات، العزل بين المؤسسات.", purposeEn: "Create core CRM tables, relations, tenant isolation.", status: "NEEDS_REVIEW", dependencies: ["G0"], canParallelizeWith: ["G2"], stageReport: "G1-STAGE-REPORT-V1 — المصدر مكتمل: 8 جداول امتداد، 26 فهرسًا صريحًا، واختبارات PostgreSQL وPlaywright للعزل. الإغلاق بانتظار نجاح CI على SHA ثابت ودليل Flyway الإنتاجي واعتماد المالك." },
  { code: "G2", titleAr: "التدويل وRTL/LTR وهيكلة الواجهة", titleEn: "i18n, RTL/LTR & UI Shell", purposeAr: "دعم العربية والإنجليزية، RTL وLTR.", purposeEn: "Support Arabic/English, RTL/LTR.", status: "NEEDS_REVIEW", dependencies: ["G0"], canParallelizeWith: ["G1"], stageReport: "G2-STAGE-REPORT-V1 — قاموس ترجمة 80+ مفتاح، دعم RTL/LTR كامل." },
  { code: "G3", titleAr: "كيانات CRM الأساسية", titleEn: "Core CRM Entities", purposeAr: "تنفيذ Leads, Customers, Contacts, Customer 360.", purposeEn: "Implement Leads, Customers, Contacts, Customer 360.", status: "NOT_STARTED", dependencies: ["G1"], canParallelizeWith: [], stageReport: null },
  { code: "G4", titleAr: "الفرص البيعية وخط الأنابيب", titleEn: "Opportunities & Pipeline", purposeAr: "تنفيذ الفرص البيعية، مراحل البيع، Kanban.", purposeEn: "Implement sales opportunities, pipeline stages, Kanban.", status: "NOT_STARTED", dependencies: ["G3"], canParallelizeWith: ["G5"], stageReport: null },
  { code: "G5", titleAr: "المهام والتحويلات والموظفين", titleEn: "Tasks, Transfers & Employees", purposeAr: "تنفيذ المهام، تحويل العملاء والفرص.", purposeEn: "Implement tasks, transfers.", status: "NOT_STARTED", dependencies: ["G3"], canParallelizeWith: ["G4"], stageReport: null },
  { code: "G6", titleAr: "التقارير والتحليلات", titleEn: "Reports & Analytics", purposeAr: "تنفيذ تقارير CRM ولوحات التحليل.", purposeEn: "Implement CRM reports and analytics.", status: "NOT_STARTED", dependencies: ["G3", "G4", "G5"], canParallelizeWith: [], stageReport: null },
  { code: "G7", titleAr: "أساس الجوال بدون اتصال", titleEn: "Mobile Offline Foundation", purposeAr: "تجهيز APIs والجداول الخاصة بتطبيق الجوال.", purposeEn: "Prepare mobile APIs and tables.", status: "NOT_STARTED", dependencies: ["G1", "G3"], canParallelizeWith: [], stageReport: null },
  { code: "G8", titleAr: "معرفة المتصل", titleEn: "Caller Identification", purposeAr: "تجهيز معرفة بيانات العميل عند الاتصال.", purposeEn: "Prepare caller identification.", status: "NOT_STARTED", dependencies: ["G7"], canParallelizeWith: [], stageReport: null },
  { code: "G9", titleAr: "الذكاء الاصطناعي المجاني والمدفوع", titleEn: "AI CRM Free & Paid Billing", purposeAr: "تنفيذ طبقات الذكاء الاصطناعي المجانية والمدفوعة.", purposeEn: "Implement free and paid AI layers.", status: "NOT_STARTED", dependencies: ["G1", "G3"], canParallelizeWith: [], stageReport: null },
  { code: "G10", titleAr: "الجودة والأمن والاعتماد", titleEn: "QA, Security & Acceptance", purposeAr: "اختبار شامل، تحقق أمني، واعتماد نهائي.", purposeEn: "Comprehensive testing, security verification, final acceptance.", status: "NOT_STARTED", dependencies: ["G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8", "G9"], canParallelizeWith: [], stageReport: null },
];

export const CRM_TASKS: CrmTask[] = [
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
];

export function getGroupTasks(groupCode: string): CrmTask[] {
  return CRM_TASKS.filter((t) => t.groupCode === groupCode);
}

export function getGroupProgress(groupCode: string) {
  const tasks = getGroupTasks(groupCode);
  const total = tasks.length;
  const done = tasks.filter((t) => t.status === "DONE").length;
  const approved = tasks.filter((t) => t.status === "APPROVED").length;
  const inProgress = tasks.filter((t) => t.status === "IN_PROGRESS").length;
  const blocked = tasks.filter((t) => t.status === "BLOCKED").length;
  const notStarted = tasks.filter((t) => t.status === "NOT_STARTED").length;
  const needsReview = tasks.filter((t) => t.status === "NEEDS_REVIEW").length;
  const percentage = total > 0 ? Math.round(((done + approved) / total) * 100) : 0;
  return { total, done, inProgress, blocked, notStarted, needsReview, approved, percentage };
}

export function getOverallProgress() {
  const totalGroups = EXECUTION_GROUPS.length;
  const totalTasks = CRM_TASKS.length;
  const completedTasks = CRM_TASKS.filter((t) => t.status === "DONE" || t.status === "APPROVED").length;
  const blockedTasks = CRM_TASKS.filter((t) => t.status === "BLOCKED").length;
  const inProgressGroups = EXECUTION_GROUPS.filter((g) => g.status === "IN_PROGRESS").length;
  const overallPercentage = totalTasks > 0 ? Math.round((completedTasks / totalTasks) * 100) : 0;
  return { totalGroups, totalTasks, completedTasks, blockedTasks, inProgressGroups, overallPercentage };
}

export const GROUP_STATUS_LABELS_AR: Record<GroupStatus, string> = {
  NOT_STARTED: "لم تبدأ", IN_PROGRESS: "قيد التنفيذ", BLOCKED: "محظورة", DONE: "مكتملة", NEEDS_REVIEW: "بانتظار المراجعة", APPROVED: "معتمدة", REJECTED: "مرفوضة",
};
export const GROUP_STATUS_LABELS_EN: Record<GroupStatus, string> = {
  NOT_STARTED: "Not Started", IN_PROGRESS: "In Progress", BLOCKED: "Blocked", DONE: "Done", NEEDS_REVIEW: "Needs Review", APPROVED: "Approved", REJECTED: "Rejected",
};
export const TASK_STATUS_LABELS_AR: Record<TaskStatus, string> = {
  NOT_STARTED: "لم تبدأ", IN_PROGRESS: "قيد التنفيذ", BLOCKED: "محظورة", DONE: "مكتملة", NEEDS_REVIEW: "بانتظار المراجعة", APPROVED: "معتمدة",
};
export const TASK_TYPE_LABELS_AR: Record<TaskType, string> = {
  Backend: "خلفية", Frontend: "واجهة", Database: "قاعدة بيانات", API: "API", Security: "أمن", Test: "اختبار", Report: "تقرير", Mobile: "جوال", AI: "ذكاء اصطناعي", Billing: "فوترة",
};
export const PRIORITY_LABELS_AR: Record<TaskPriority, string> = {
  Critical: "حرجة", High: "عالية", Medium: "متوسطة", Low: "منخفضة",
};
