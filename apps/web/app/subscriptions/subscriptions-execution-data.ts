/**
 * Subscriptions Execution Data
 * ----------------------------
 * Business data for Subscriptions execution groups and tasks.
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

// ── Subscriptions-Specific Task Type ────────────────────────────────────────

/**
 * Subscriptions Task — Business data for a subscriptions task.
 *
 * NOTE: This is NOT an ExecutionTask. The SubscriptionsExecutionProvider
 * converts this to ExecutionTask when providing data to the framework.
 */
export interface SubscriptionsTask {
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

// ── Subscriptions Execution Groups ──────────────────────────────────────────

/**
 * Subscriptions Execution Groups — Business data for group definitions.
 *
 * NOTE: This is NOT an ExecutionGroup[]. The SubscriptionsExecutionProvider
 * converts this to ExecutionGroup[] when providing data to the framework.
 */
export const SUBSCRIPTIONS_GROUP_DATA = [
  {
    code: "G0",
    titleAr: "الأساس: خطط الاشتراك ومستويات الأسعار",
    titleEn: "Foundation: Subscription Plans & Pricing Tiers",
    purposeAr: "تأسيس نظام خطط الاشتراك ومستويات الأسعار الأساسية.",
    purposeEn: "Establish the core subscription plans and pricing tiers system.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: [],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G1",
    titleAr: "الميزات الأساسية: دورة حياة الاشتراك والتجديد",
    titleEn: "Core Features: Subscription Lifecycle & Renewals",
    purposeAr: "تنفيذ إدارة دورة حياة الاشتراك الكاملة من الإنشاء إلى التجديد.",
    purposeEn: "Implement full subscription lifecycle management from creation to renewal.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G0"],
    canParallelizeWith: ["G2"],
    stageReport: null,
  },
  {
    code: "G2",
    titleAr: "تكامل الفواتير: الفواتير والمدفوعات",
    titleEn: "Billing Integration: Invoicing & Payments",
    purposeAr: "تنفيذ إنشاء الفواتير المعتمدة على الاشتراك و.gateway المدفوعات.",
    purposeEn: "Implement subscription-based invoicing and payment gateway integration.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G0"],
    canParallelizeWith: ["G1"],
    stageReport: null,
  },
  {
    code: "G3",
    titleAr: "بوابة العميل: الخدمة الذاتية وتتبع الاستخدام",
    titleEn: "Customer Portal: Self-Service & Usage Tracking",
    purposeAr: "تنفيذ بوابة العميل للخدمة الذاتية مع تتبع الاستخدام والميزة.",
    purposeEn: "Implement customer self-service portal with usage and feature tracking.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1", "G2"],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G4",
    titleAr: "التحليلات وإدارة فقدان العملاء",
    titleEn: "Analytics & Churn Management",
    purposeAr: "تنفيذ تحليلات الاشتراك وإدارة فقدان العملاء والاسترداد.",
    purposeEn: "Implement subscription analytics, churn management, and recovery.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G3"],
    canParallelizeWith: [],
    stageReport: null,
  },
];

// ── Subscriptions Tasks ─────────────────────────────────────────────────────

export const SUBSCRIPTIONS_TASKS: SubscriptionsTask[] = [
  // ── G0: Foundation: Subscription Plans & Pricing Tiers ───────────────────────
  {
    id: "G0-T01",
    number: "G0-01",
    nameAr: "إنشاء جدول خطط الاشتراك",
    nameEn: "Create subscription plans table",
    groupCode: "G0",
    descriptionAr: "إنشاء جدول subscription_plans مع الحقول الأساسية: المعرف، الاسم، السعر، الفئة، الحدود، الحالة",
    descriptionEn: "Create subscription_plans table with core fields: id, name, price, category, limits, status",
    type: "Database",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: [],
    acceptanceCriteriaAr: "جدول subscription_plans موجود مع tenant_id و CHECK constraints على السعر والحد الأقصى",
    implementationNotesAr: "يجب أن يتضمن tenant_id UUID NOT NULL وقيود على الأسعار الإيجابية",
  },
  {
    id: "G0-T02",
    number: "G0-02",
    nameAr: "إنشاء جدول مستويات الأسعار",
    nameEn: "Create pricing tiers table",
    groupCode: "G0",
    descriptionAr: "إنشاء جدول pricing_tiers لمستويات الأسعار المتعددة لكل خطة مع الحدود والخصومات",
    descriptionEn: "Create pricing_tiers table for multiple pricing tiers per plan with limits and discounts",
    type: "Database",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T01"],
    acceptanceCriteriaAr: "جدول pricing_tiers موجود مع علاقات صحيحة مع subscription_plans وقيود الحد الأدنى/الأقصى",
    implementationNotesAr: "relationships مع subscription_plans عبر plan_id UUID FK",
  },
  {
    id: "G0-T03",
    number: "G0-03",
    nameAr: "تنفيذ CRUD خطط الاشتراك",
    nameEn: "Implement subscription plans CRUD",
    groupCode: "G0",
    descriptionAr: "تنفيذ واجهة برمجة التطبيقات لإنشاء وقراءة وتحديث وحذف خطط الاشتрак",
    descriptionEn: "Implement API for creating, reading, updating, and deleting subscription plans",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T01"],
    acceptanceCriteriaAr: "4 endpoints تعمل: POST, GET, PUT, DELETE مع صلاحيات RBAC",
    implementationNotesAr: "REST endpoints مع validation و RBAC permissions",
  },
  {
    id: "G0-T04",
    number: "G0-04",
    nameAr: "إنشاء واجهة إدارة الخطط",
    nameEn: "Create plans management UI",
    groupCode: "G0",
    descriptionAr: "إنشاء واجهة مستخدم لإدارة خطط الاشتراك ومستويات الأسعار",
    descriptionEn: "Create user interface for managing subscription plans and pricing tiers",
    type: "Frontend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G0-T03"],
    acceptanceCriteriaAr: "واجهة تعرض الخطط بالقائمة والتفاصيل مع إمكانية الإنشاء والتعديل",
    implementationNotesAr: "React components مع form validation و RTL support",
  },

  // ── G1: Core Features: Subscription Lifecycle & Renewals ─────────────────────
  {
    id: "G1-T01",
    number: "G1-01",
    nameAr: "إنشاء جدول الاشتراكات النشطة",
    nameEn: "Create active subscriptions table",
    groupCode: "G1",
    descriptionAr: "إنشاء جدول customer_subscriptions للاشتراكات النشطة مع الحالةTarikh والتواريخ",
    descriptionEn: "Create customer_subscriptions table for active subscriptions with status and dates",
    type: "Database",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: [],
    acceptanceCriteriaAr: "جدول customer_subscriptions موجود مع tenant_id وقيود الحالة والتاريخ",
    implementationNotesAr: "status ENUM مع dates: started_at, current_period_end, cancelled_at",
  },
  {
    id: "G1-T02",
    number: "G1-02",
    nameAr: "تنفيذ محرك دورة حياة الاشتراك",
    nameEn: "Implement subscription lifecycle engine",
    groupCode: "G1",
    descriptionAr: "تنفيذ المنطق التجاري لإنشاء واشتراك وتغيير الخطة وإلغاء وتفعيل",
    descriptionEn: "Implement business logic for subscribe, upgrade/downgrade, cancel, and reactivate",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G1-T01", "G0-T01"],
    acceptanceCriteriaAr: "5 عمليات حياة اشتراك تعمل: Subscribe, Upgrade, Downgrade, Cancel, Reactivate",
    implementationNotesAr: "state machine مع transitions و proration logic",
  },
  {
    id: "G1-T03",
    number: "G1-03",
    nameAr: "تنفيذ نظام التجديد التلقائي",
    nameEn: "Implement auto-renewal system",
    groupCode: "G1",
    descriptionAr: "تنفيذ آليات التجديد التلقائي مع الإشعارات والتذكيرات قبل انتهاء الفترة",
    descriptionEn: "Implement auto-renewal mechanisms with notifications and reminders before period end",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G1-T02"],
    acceptanceCriteriaAr: "التجديد التلقائي يعمل مع 3 تذكيرات: 7 أيام، 3 أيام، يوم واحد",
    implementationNotesAr: "scheduled jobs مع notification service integration",
  },
  {
    id: "G1-T04",
    number: "G1-04",
    nameAr: "إنشاء واجهة إدارة الاشتراكات",
    nameEn: "Create subscription management UI",
    groupCode: "G1",
    descriptionAr: "إنشاء واجهة لعرض وإدارة اشتراكات العملاء مع التفاصيل والتاريخ",
    descriptionEn: "Create UI for viewing and managing customer subscriptions with details and history",
    type: "Frontend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G1-T02"],
    acceptanceCriteriaAr: "قائمة الاشتراكات مع التفاصيل والإجراءات المتاحة حسب الحالة",
    implementationNotesAr: "subscription detail page مع timeline و action buttons",
  },

  // ── G2: Billing Integration: Invoicing & Payments ───────────────────────────
  {
    id: "G2-T01",
    number: "G2-01",
    nameAr: "إنشاء جدول الفواتير المعتمدة على الاشتراك",
    nameEn: "Create subscription-based invoices table",
    groupCode: "G2",
    descriptionAr: "إنشاء جدول subscription_invoices لإصدار الفواتير التلقائية بناءً على دورة الاشتراك",
    descriptionEn: "Create subscription_invoices table for automatic invoice generation based on subscription cycle",
    type: "Database",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: [],
    acceptanceCriteriaAr: "جدول subscription_invoices موجود مع foreign keys لـ customer_subscriptions",
    implementationNotesAr: "invoice_number generator مع tax calculations",
  },
  {
    id: "G2-T02",
    number: "G2-02",
    nameAr: "تنفيذ محرك إنشاء الفواتير",
    nameEn: "Implement invoice generation engine",
    groupCode: "G2",
    descriptionAr: "تنفيذ محرك إنشاء الفواتير التلقائية مع الضرائب والخصومات",
    descriptionEn: "Implement automatic invoice generation engine with taxes and discounts",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G2-T01", "G1-T01"],
    acceptanceCriteriaAr: "الفواتير تُنشأ تلقائياً عند كل تجديد مع حسابات الضرائب الصحيحة",
    implementationNotesAr: "tax calculation service مع support for multi-currency",
  },
  {
    id: "G2-T03",
    number: "G2-03",
    nameAr: "تكامل بوابة الدفع",
    nameEn: "Payment gateway integration",
    groupCode: "G2",
    descriptionAr: "تنفيذ تكامل بوابة الدفع للمدفوعات المتكررة",
    descriptionEn: "Implement payment gateway integration for recurring payments",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G2-T02"],
    acceptanceCriteriaAr: "المدفوعات المتكررة تعمل مع gateway واحد على الأقل",
    implementationNotesAr: "webhook handler لحالات: paid, failed, refunded",
  },
  {
    id: "G2-T04",
    number: "G2-04",
    nameAr: "إنشاء واجهة الفواتير والمدفوعات",
    nameEn: "Create invoices & payments UI",
    groupCode: "G2",
    descriptionAr: "إنشاء واجهة لعرض الفواتير وتفاصيل المدفوعات",
    descriptionEn: "Create UI for viewing invoices and payment details",
    type: "Frontend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G2-T02"],
    acceptanceCriteriaAr: "صفحة الفواتير مع التفاصيل وحالة الدفع",
    implementationNotesAr: "invoice list و detail views مع payment status badges",
  },

  // ── G3: Customer Portal: Self-Service & Usage Tracking ───────────────────────
  {
    id: "G3-T01",
    number: "G3-01",
    nameAr: "إنشاء بوابة العميل للخدمة الذاتية",
    nameEn: "Create customer self-service portal",
    groupCode: "G3",
    descriptionAr: "إنشاء واجهة بوابة العميل للعرض والتعديل الذاتي للاشتراك",
    descriptionEn: "Create customer portal interface for viewing and self-managing subscriptions",
    type: "Frontend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: [],
    acceptanceCriteriaAr: "العميل يرى اشتراكته ويستطيع تغيير الخطة والإلغاء",
    implementationNotesAr: "customer portal page مع subscription card و action buttons",
  },
  {
    id: "G3-T02",
    number: "G3-02",
    nameAr: "تنفيذ تتبع الاستخدام",
    nameEn: "Implement usage tracking",
    groupCode: "G3",
    descriptionAr: "تنفيذ نظام تتبع استخدام الميزات لكل اشتراك",
    descriptionEn: "Implement feature usage tracking system per subscription",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G1-T01"],
    acceptanceCriteriaAr: "استخدام كل ميزة مسجل مع الحدود والتنبيهات عند التجاوز",
    implementationNotesAr: "usage metering service مع quota enforcement",
  },
  {
    id: "G3-T03",
    number: "G3-03",
    nameAr: "تنفيذ إدارة مفاتيح API",
    nameEn: "Implement API key management",
    groupCode: "G3",
    descriptionAr: "تنفيذ إنشاء وإدارة مفاتيح API الخاصة بالاشتراكات",
    descriptionEn: "Implement subscription API key creation and management",
    type: "Backend",
    priority: "Medium",
    status: "NOT_STARTED",
    dependencies: ["G1-T01"],
    acceptanceCriteriaAr: "إنشاء مفتاح API مع صلاحيات مبنية على خطة الاشتراك",
    implementationNotesAr: "API keys مع plan-based permissions و rate limiting",
  },
  {
    id: "G3-T04",
    number: "G3-04",
    nameAr: "إنشاء واجهة تتبع الاستخدام",
    nameEn: "Create usage tracking UI",
    groupCode: "G3",
    descriptionAr: "إنشاء واجهة لعرض استخدام الميزات والحدود المتبقية",
    descriptionEn: "Create UI for viewing feature usage and remaining quotas",
    type: "Frontend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G3-T02"],
    acceptanceCriteriaAr: "عرض الاستخدام مع رسوم بيانية وتنبيهات الحد",
    implementationNotesAr: "usage dashboard مع charts و progress bars",
  },

  // ── G4: Analytics & Churn Management ────────────────────────────────────────
  {
    id: "G4-T01",
    number: "G4-01",
    nameAr: "تنفيذ تحليلات الاشتراك",
    nameEn: "Implement subscription analytics",
    groupCode: "G4",
    descriptionAr: "تنفيذ تحليلات: MRR, ARR, LTV, معدل التحويل، معدل الاحتفاظ",
    descriptionEn: "Implement analytics: MRR, ARR, LTV, conversion rate, retention rate",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: [],
    acceptanceCriteriaAr: "6 مؤشرات رئيسية محسوبة: MRR, ARR, LTV, Churn Rate, Conversion, Retention",
    implementationNotesAr: "analytics aggregation service مع cached calculations",
  },
  {
    id: "G4-T02",
    number: "G4-02",
    nameAr: "تنفيذ إدارة فقدان العملاء",
    nameEn: "Implement churn management",
    groupCode: "G4",
    descriptionAr: "تنفيذ نظام كشف الإلغاءات المحتملة وإدارة حملات الاسترداد",
    descriptionEn: "Implement potential cancellation detection and recovery campaign management",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G4-T01"],
    acceptanceCriteriaAr: "كشف 80% من الإلغاءات المحتملة قبل 30 يوم",
    implementationNotesAr: "ML-based churn prediction مع rule-based fallback",
  },
  {
    id: "G4-T03",
    number: "G4-03",
    nameAr: "إنشاء لوحة تحليلات الاشتراك",
    nameEn: "Create subscription analytics dashboard",
    groupCode: "G4",
    descriptionAr: "إنشاء لوحة تحكم للتحليلات مع الرسوم البيانية والتقارير",
    descriptionEn: "Create analytics dashboard with charts and reports",
    type: "Frontend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G4-T01"],
    acceptanceCriteriaAr: "لوحة تعرض MRR, ARR, churn rate, top plans مع رسوم بيانية",
    implementationNotesAr: "dashboard widgets مع recharts و date range filters",
  },
  {
    id: "G4-T04",
    number: "G4-04",
    nameAr: "تنفيذ تقارير الاشتراك الشهرية",
    nameEn: "Implement monthly subscription reports",
    groupCode: "G4",
    descriptionAr: "تنفيذ إنشاء تقارير شهرية تلقائية لل подписين والإيرادات",
    descriptionEn: "Implement automatic monthly reports for subscribers and revenue",
    type: "Report",
    priority: "Medium",
    status: "NOT_STARTED",
    dependencies: ["G4-T01"],
    acceptanceCriteriaAr: "تقرير شهري يُنشأ تلقائياً مع ملخص المؤشرات الرئيسية",
    implementationNotesAr: "scheduled report generation مع email delivery",
  },
];
