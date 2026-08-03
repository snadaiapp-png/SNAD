/**
 * Workflow Execution Data
 * -----------------------
 * Business data for Workflow Automation execution groups and tasks.
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

// ── Workflow-Specific Task Type ──────────────────────────────────────────────

/**
 * Workflow Task — Business data for a workflow automation task.
 *
 * NOTE: This is NOT an ExecutionTask. The WorkflowExecutionProvider
 * converts this to ExecutionTask when providing data to the framework.
 */
export interface WorkflowTask {
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

// ── Workflow Execution Groups ────────────────────────────────────────────────

/**
 * Workflow Execution Groups — Business data for group definitions.
 *
 * NOTE: This is NOT an ExecutionGroup[]. The WorkflowExecutionProvider
 * converts this to ExecutionGroup[] when providing data to the framework.
 */
export const WORKFLOW_GROUP_DATA = [
  {
    code: "G0",
    titleAr: "الأساس والمحرك",
    titleEn: "Foundation & Engine",
    purposeAr: "تأسيس محرك سير العمل وتعريفات المكونات الأساسية.",
    purposeEn: "Establish the workflow engine and core component definitions.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: [],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G1",
    titleAr: "الميزات الأساسية",
    titleEn: "Core Features",
    purposeAr: "إدارة المهام وتحويلات التدفق.",
    purposeEn: "Task management and flow routing.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G0"],
    canParallelizeWith: ["G2"],
    stageReport: null,
  },
  {
    code: "G2",
    titleAr: "الميزات المتقدمة",
    titleEn: "Advanced Features",
    purposeAr: "الشروط المنطقية والتنفيذ المتوازي.",
    purposeEn: "Logical conditions and parallel execution.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G0"],
    canParallelizeWith: ["G1"],
    stageReport: null,
  },
  {
    code: "G3",
    titleAr: "التكامل",
    titleEn: "Integration",
    purposeAr: "واجهة API للمطورين والخطافات.",
    purposeEn: "Developer API and webhooks.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1"],
    canParallelizeWith: ["G4"],
    stageReport: null,
  },
  {
    code: "G4",
    titleAr: "المراقبة والتحليلات",
    titleEn: "Monitoring & Analytics",
    purposeAr: "تتبع الأداء والتحليلات التفصيلية.",
    purposeEn: "Performance tracking and detailed analytics.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G3"],
    canParallelizeWith: [],
    stageReport: null,
  },
];

// ── Workflow Tasks ───────────────────────────────────────────────────────────

export const WORKFLOW_TASKS: WorkflowTask[] = [
  // ── G0: Foundation & Engine ───────────────────────────────────────────────
  {
    id: "G0-T01",
    number: "G0-01",
    nameAr: "تعريف نموذج WorkflowDefinition",
    nameEn: "Define WorkflowDefinition model",
    groupCode: "G0",
    descriptionAr: "إنشاء نموذج البيانات الأساسي لتعريف سير العمل مع الحقول المطلوبة",
    descriptionEn: "Create the base data model for workflow definitions with required fields",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: [],
    acceptanceCriteriaAr: "WorkflowDefinition يحتوي على id, name, version, steps, variables, triggers",
    implementationNotesAr: "نموذج TypeScript interfaces مع دعم JSON Schema للتحقق",
  },
  {
    id: "G0-T02",
    number: "G0-02",
    nameAr: "بناء محرك تنفيذ سير العمل",
    nameEn: "Build workflow execution engine",
    groupCode: "G0",
    descriptionAr: "تنفيذ محرك يقرأ تعريف سير العمل ويexecutes الخطوات بالتسلسل",
    descriptionEn: "Implement engine that reads workflow definitions and executes steps sequentially",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T01"],
    acceptanceCriteriaAr: "المحرك ينفذ خطوة واحدة على الأقل بنجاح",
    implementationNotesAr: "محرك state machine مع دعم checkpoints",
  },
  {
    id: "G0-T03",
    number: "G0-03",
    nameAr: "إنشاء قاعدة بيانات جداول سير العمل",
    nameEn: "Create workflow database tables",
    groupCode: "G0",
    descriptionAr: "إنشاء جداول PostgreSQL لتعريفات وحالات سير العمل",
    descriptionEn: "Create PostgreSQL tables for workflow definitions and states",
    type: "Database",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: [],
    acceptanceCriteriaAr: "جدول workflow_definitions, workflow_instances, workflow_steps",
    implementationNotesAr: "Flyway migrations مع tenant isolation",
  },
  {
    id: "G0-T04",
    number: "G0-04",
    nameAr: "إنشاء واجهة مستخدم محرر سير العمل",
    nameEn: "Create workflow editor UI",
    groupCode: "G0",
    descriptionAr: "بناء واجهة سحب وإفلات لتصميم خطوات سير العمل",
    descriptionEn: "Build drag-and-drop interface for designing workflow steps",
    type: "Frontend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G0-T01"],
    acceptanceCriteriaAr: "المستخدم يستطيع إضافة 3 خطوات على الأقل",
    implementationNotesAr: "React component مع react-flow لرسم المخططات",
  },

  // ── G1: Core Features ─────────────────────────────────────────────────────
  {
    id: "G1-T01",
    number: "G1-01",
    nameAr: "تنفيذ إدارة المهام",
    nameEn: "Implement task management",
    groupCode: "G1",
    descriptionAr: "إنشاء نظام لإدارة مهام سير العمل مع الأولوية والتعيين",
    descriptionEn: "Create system for managing workflow tasks with priority and assignment",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T02"],
    acceptanceCriteriaAr: "إنشاء وتعيين وإكمال المهام يعمل",
    implementationNotesAr: "REST endpoints مع RBAC",
  },
  {
    id: "G1-T02",
    number: "G1-02",
    nameAr: "تنفيذ مسارات التحويل",
    nameEn: "Implement routing paths",
    groupCode: "G1",
    descriptionAr: "إنشاء منطق التحويل بين خطوات سير العمل بناءً على شروط",
    descriptionEn: "Create routing logic between workflow steps based on conditions",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T02"],
    acceptanceCriteriaAr: "تحويل إلى الخطوة التالية أو تخطي يعمل",
    implementationNotesAr: "State machine pattern مع conditional routing",
  },
  {
    id: "G1-T03",
    number: "G1-03",
    nameAr: "إنشاء واجهة إدارة المهام",
    nameEn: "Create task management UI",
    groupCode: "G1",
    descriptionAr: "بناء لوحة متابعة لعرض وتعديل مهام سير العمل",
    descriptionEn: "Build dashboard to view and edit workflow tasks",
    type: "Frontend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G1-T01"],
    acceptanceCriteriaAr: "قائمة المهام تعرض الحالة والأولوية",
    implementationNotesAr: "React table مع فلترة وفرز",
  },
  {
    id: "G1-T04",
    number: "G1-04",
    nameAr: "تنفيذ نظام الإشعارات",
    nameEn: "Implement notification system",
    groupCode: "G1",
    descriptionAr: "إنشاء نظام إشعارات للمهام المنتظرة والمواعيد النهائية",
    descriptionEn: "Create notification system for pending tasks and deadlines",
    type: "Backend",
    priority: "Medium",
    status: "NOT_STARTED",
    dependencies: ["G1-T01"],
    acceptanceCriteriaAr: "إشعار يُرسل عند تعيين مهمة جديدة",
    implementationNotesAr: "WebSocket + email fallback",
  },

  // ── G2: Advanced Features ─────────────────────────────────────────────────
  {
    id: "G2-T01",
    number: "G2-01",
    nameAr: "تنفيذ محرك الشروط",
    nameEn: "Implement conditions engine",
    groupCode: "G2",
    descriptionAr: "بناء محرك ي evaluating شروط معقدة مع دعم AND/OR/NOT",
    descriptionEn: "Build engine evaluating complex conditions with AND/OR/NOT support",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T02"],
    acceptanceCriteriaAr: "شروط متعددة المنطقية تعمل بشكل صحيح",
    implementationNotesAr: "Expression parser مع tree evaluation",
  },
  {
    id: "G2-T02",
    number: "G2-02",
    nameAr: "تنفيذ التنفيذ المتوازي",
    nameEn: "Implement parallel execution",
    groupCode: "G2",
    descriptionAr: "دعم تشغيل عدة خطوات سير العمل بشكل متوازي",
    descriptionEn: "Support running multiple workflow steps in parallel",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T02"],
    acceptanceCriteriaAr: "خطوتان على الأقل تبدآن وتنتهيان بشكل متوازي",
    implementationNotesAr: "Promise.all مع fork/join pattern",
  },
  {
    id: "G2-T03",
    number: "G2-03",
    nameAr: "تنفيذ نقاط التفتيش",
    nameEn: "Implement checkpoints",
    groupCode: "G2",
    descriptionAr: "إنشاء نقاط حفظ حالة سير العمل للتعافي من الأخطاء",
    descriptionEn: "Create state saving points for error recovery",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G2-T02"],
    acceptanceCriteriaAr: "استئناف من نقطة تفتيش يعمل",
    implementationNotesAr: "State snapshots مع versioning",
  },
  {
    id: "G2-T04",
    number: "G2-04",
    nameAr: "تنفيذ الحلقات والتكرارات",
    nameEn: "Implement loops and iterations",
    groupCode: "G2",
    descriptionAr: "دعم الحلقات في سير العمل مع حد أقصى للتكرارات",
    descriptionEn: "Support loops in workflows with maximum iteration limits",
    type: "Backend",
    priority: "Medium",
    status: "NOT_STARTED",
    dependencies: ["G2-T01"],
    acceptanceCriteriaAr: "حلقة تكرارية تعمل مع حد أقصى 100",
    implementationNotesAr: "For-each و while loop patterns",
  },

  // ── G3: Integration ───────────────────────────────────────────────────────
  {
    id: "G3-T01",
    number: "G3-01",
    nameAr: "بناء واجهة برمجة التطبيقات",
    nameEn: "Build REST API",
    groupCode: "G3",
    descriptionAr: "إنشاء endpoints REST لإدارة سير العمل عبر API",
    descriptionEn: "Create REST endpoints for managing workflows via API",
    type: "API",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G1-T01"],
    acceptanceCriteriaAr: "CREATE/READ/UPDATE/DELETE يعمل للتعريفات والتنفيذ",
    implementationNotesAr: "OpenAPI 3.0 spec مع Swagger UI",
  },
  {
    id: "G3-T02",
    number: "G3-02",
    nameAr: "تنفيذ خطافات الأحداث",
    nameEn: "Implement event webhooks",
    groupCode: "G3",
    descriptionAr: "إرسال إشعارات إلى خدمات خارجية عند أحداث سير العمل",
    descriptionEn: "Send notifications to external services on workflow events",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G3-T01"],
    acceptanceCriteriaAr: "webhook يُرسل عند اكتمال المهمة",
    implementationNotesAr: "HTTP client مع retry و logging",
  },
  {
    id: "G3-T03",
    number: "G3-03",
    nameAr: "تنفيذ التكامل مع Slack",
    nameEn: "Implement Slack integration",
    groupCode: "G3",
    descriptionAr: "إرسال تحديثات سير العمل إلى قنوات Slack",
    descriptionEn: "Send workflow updates to Slack channels",
    type: "Integration",
    priority: "Medium",
    status: "NOT_STARTED",
    dependencies: ["G3-T02"],
    acceptanceCriteriaAr: "رسالة Slack تُرسل عند تغيير الحالة",
    implementationNotesAr: "Slack API مع Block Kit",
  },
  {
    id: "G3-T04",
    number: "G3-04",
    nameAr: "تنفيذ التكامل مع البريد الإلكتروني",
    nameEn: "Implement email integration",
    groupCode: "G3",
    descriptionAr: "إرسال إشعارات البريد الإلكتروني لأحداث سير العمل المهمة",
    descriptionEn: "Send email notifications for important workflow events",
    type: "Integration",
    priority: "Medium",
    status: "NOT_STARTED",
    dependencies: ["G3-T02"],
    acceptanceCriteriaAr: "بريد يُرسل عند اكتمال المهمة الحرجة",
    implementationNotesAr: "SMTP مع HTML templates",
  },
  {
    id: "G3-T05",
    number: "G3-05",
    nameAr: "تنفيذ API التكامل الخارجي",
    nameEn: "Implement external integration API",
    groupCode: "G3",
    descriptionAr: "بناء واجهة API للأنظمة الخارجية لتشغيل سير العمل",
    descriptionEn: "Build API for external systems to trigger workflows",
    type: "API",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G3-T01"],
    acceptanceCriteriaAr: "النظام الخارجي يشغّل سير عمل عبر API",
    implementationNotesAr: "Webhook triggers مع HMAC verification",
  },

  // ── G4: Monitoring & Analytics ────────────────────────────────────────────
  {
    id: "G4-T01",
    number: "G4-01",
    nameAr: "إنشاء لوحة متابعة الأداء",
    nameEn: "Create performance dashboard",
    groupCode: "G4",
    descriptionAr: "بناء لوحة متابعة لعرض إحصائيات سير العمل الحية",
    descriptionEn: "Build dashboard showing live workflow statistics",
    type: "Frontend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G3-T01"],
    acceptanceCriteriaAr: "عرض وقت التنفيذ ومعدل الإكمال",
    implementationNotesAr: "React chart components مع real-time updates",
  },
  {
    id: "G4-T02",
    number: "G4-02",
    nameAr: "تنفيذ سجل الأحداث",
    nameEn: "Implement event logging",
    groupCode: "G4",
    descriptionAr: "تسجيل جميع أحداث سير العمل للمراجعة",
    descriptionEn: "Log all workflow events for audit",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G0-T02"],
    acceptanceCriteriaAr: "كل حدث مسجل مع timestamp وuser",
    implementationNotesAr: "Structured logging مع ELK stack",
  },
  {
    id: "G4-T03",
    number: "G4-03",
    nameAr: "تنفيذ تقارير التحليلات",
    nameEn: "Implement analytics reports",
    groupCode: "G4",
    descriptionAr: "إنشاء تقارير تفصيلية عن أداء سير العمل والزمن",
    descriptionEn: "Create detailed reports on workflow performance and timing",
    type: "Report",
    priority: "Medium",
    status: "NOT_STARTED",
    dependencies: ["G4-T01"],
    acceptanceCriteriaAr: "تقرير أسبوعي يعرض сред وقت التنفيذ",
    implementationNotesAr: "PDF/Excel export مع scheduled generation",
  },
];
