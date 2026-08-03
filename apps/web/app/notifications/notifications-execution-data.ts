/**
 * Notifications Execution Data
 * ----------------------------
 * Business data for Notifications execution groups and tasks.
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

// ── Notifications-Specific Task Type ──────────────────────────────────────

/**
 * Notifications Task — Business data for a notifications task.
 *
 * NOTE: This is NOT an ExecutionTask. The NotificationsExecutionProvider
 * converts this to ExecutionTask when providing data to the framework.
 */
export interface NotificationsTask {
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

// ── Notifications Execution Groups ────────────────────────────────────────

/**
 * Notifications Execution Groups — Business data for group definitions.
 *
 * NOTE: This is NOT an ExecutionGroup[]. The NotificationsExecutionProvider
 * converts this to ExecutionGroup[] when providing data to the framework.
 */
export const NOTIFICATIONS_GROUP_DATA = [
  {
    code: "G0",
    titleAr: "أساسيات البنية التحتية للإشعارات",
    titleEn: "Notifications Infrastructure Foundation",
    purposeAr: "تأسيس بنية الإشعارات الأساسية ونظام التوصيل الأساسي.",
    purposeEn: "Establish core notification infrastructure and basic delivery system.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: [],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G1",
    titleAr: "قنوات التوصيل الأساسية",
    titleEn: "Core Delivery Channels",
    purposeAr: "تنفيذ قنوات البريد الإلكتروني والرسائل النصية والإشعارات الفورية.",
    purposeEn: "Implement email, SMS, and push notification channels.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G0"],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G2",
    titleAr: "القوالب والجدولة المتقدمة",
    titleEn: "Templates & Scheduling",
    purposeAr: "تنفيذ نظام القوالب وجدولة الإشعارات المتقدمة.",
    purposeEn: "Implement template system and advanced notification scheduling.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1"],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G3",
    titleAr: "التكامل والتحسين",
    titleEn: "Integration & Optimization",
    purposeAr: "تكامل الإشعارات مع الوحدات الأخرى وتحسين الأداء والتحليلات.",
    purposeEn: "Integrate notifications with other modules and optimize performance and analytics.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1", "G2"],
    canParallelizeWith: [],
    stageReport: null,
  },
];

// ── Notifications Tasks ───────────────────────────────────────────────────

export const NOTIFICATIONS_TASKS: NotificationsTask[] = [
  // ── G0: Notifications Infrastructure Foundation ─────────────────────────────
  {
    id: "G0-T01",
    number: "G0-01",
    nameAr: "إنشاء جدول الإشعارات الأساسي",
    nameEn: "Create core notifications table",
    groupCode: "G0",
    descriptionAr: "إنشاء جدول notifications الأساسي مع tenant_id وعمود JSONB للبيانات الوصفية",
    descriptionEn: "Create core notifications table with tenant_id and JSONB metadata column",
    type: "Database",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: [],
    acceptanceCriteriaAr: "جدول notifications موجود مع tenant_id UUID NOT NULL وعمود payload JSONB",
    implementationNotesAr: "Flyway migration V20260803_1",
  },
  {
    id: "G0-T02",
    number: "G0-02",
    nameAr: "إنشاء جدول سجل التوصيل",
    nameEn: "Create delivery log table",
    groupCode: "G0",
    descriptionAr: "إنشاء جدول notification_delivery_log لتتبع حالة كل إشعار",
    descriptionEn: "Create notification_delivery_log table to track each notification status",
    type: "Database",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T01"],
    acceptanceCriteriaAr: "جدول notification_delivery_log مع فهارس على notification_id وchannel وstatus",
    implementationNotesAr: "يتطلب G0-T01 كمرجع خارجي",
  },
  {
    id: "G0-T03",
    number: "G0-03",
    nameAr: "إنشاء NotificationService الأساسي",
    nameEn: "Create core NotificationService",
    groupCode: "G0",
    descriptionAr: "إنشاء NotificationService مع واجهة send وgetByUser وmarkAsRead",
    descriptionEn: "Create NotificationService with send, getByUser, markAsRead interface",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T01", "G0-T02"],
    acceptanceCriteriaAr: "NotificationService يوفر send وgetByUser وmarkAsRead",
    implementationNotesAr: "Java/Kotlin service with Spring Boot",
  },
  {
    id: "G0-T04",
    number: "G0-04",
    nameAr: "إنشاء واجهة REST للإشعارات",
    nameEn: "Create notifications REST API",
    groupCode: "G0",
    descriptionAr: "إنشاء endpoints: GET /notifications, POST /notifications, PATCH /notifications/{id}/read",
    descriptionEn: "Create endpoints: GET /notifications, POST /notifications, PATCH /notifications/{id}/read",
    type: "API",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T03"],
    acceptanceCriteriaAr: "3 endpoints تعمل مع tenant isolation",
    implementationNotesAr: "RESTful API with OpenAPI documentation",
  },

  // ── G1: Core Delivery Channels ──────────────────────────────────────────────
  {
    id: "G1-T01",
    number: "G1-01",
    nameAr: "تنفيذ محرك البريد الإلكتروني",
    nameEn: "Implement email delivery engine",
    groupCode: "G1",
    descriptionAr: "تنفيذ EmailNotificationSender باستخدام SMTP/API مع دعم HTML",
    descriptionEn: "Implement EmailNotificationSender using SMTP/API with HTML support",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T03"],
    acceptanceCriteriaAr: "EmailNotificationSender يرسل إشعارات بريد إلكتروني بنجاح",
    implementationNotesAr: "Support SendGrid or AWS SES",
  },
  {
    id: "G1-T02",
    number: "G1-02",
    nameAr: "تنفيذ محرك الرسائل النصية",
    nameEn: "Implement SMS delivery engine",
    groupCode: "G1",
    descriptionAr: "تنفيذ SmsNotificationSender باستخدام Twilio أو مزود محلي",
    descriptionEn: "Implement SmsNotificationSender using Twilio or local provider",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G0-T03"],
    acceptanceCriteriaAr: "SmsNotificationSender يرسل رسائل SMS بنجاح",
    implementationNotesAr: "Support Twilio and local Saudi providers",
  },
  {
    id: "G1-T03",
    number: "G1-03",
    nameAr: "تنفيذ محرك الإشعارات الفورية",
    nameEn: "Implement push notification engine",
    groupCode: "G1",
    descriptionAr: "تنفيذ PushNotificationSender لـ Firebase Cloud Messaging",
    descriptionEn: "Implement PushNotificationSender for Firebase Cloud Messaging",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G0-T03"],
    acceptanceCriteriaAr: "PushNotificationSender يرسل إشعارات FCM بنجاح",
    implementationNotesAr: "Firebase Admin SDK integration",
  },
  {
    id: "G1-T04",
    number: "G1-04",
    nameAr: "تنفيذ NotificationRouter المتعدد القنوات",
    nameEn: "Implement multi-channel NotificationRouter",
    groupCode: "G1",
    descriptionAr: "إنشاء NotificationRouter يوزع الإشعارات على القنوات حسب تفضيلات المستخدم",
    descriptionEn: "Create NotificationRouter that distributes notifications to channels based on user preferences",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G1-T01", "G1-T02", "G1-T03"],
    acceptanceCriteriaAr: "NotificationRouter يوزع الإشعارات على 3 قنوات حسب التفضيلات",
    implementationNotesAr: "Strategy pattern for channel selection",
  },
  {
    id: "G1-T05",
    number: "G1-05",
    nameAr: "كتابة اختبارات التوصيل",
    nameEn: "Write delivery tests",
    groupCode: "G1",
    descriptionAr: "كتابة اختبارات وحدة لكل محرك توصيل مع Testcontainers",
    descriptionEn: "Write unit tests for each delivery engine with Testcontainers",
    type: "Test",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G1-T04"],
    acceptanceCriteriaAr: "جميع اختبارات التوصيل تمر على PostgreSQL 16",
    implementationNotesAr: "Testcontainers with postgres:16-alpine",
  },

  // ── G2: Templates & Scheduling ──────────────────────────────────────────────
  {
    id: "G2-T01",
    number: "G2-01",
    nameAr: "إنشاء نظام القوالب",
    nameEn: "Create template system",
    groupCode: "G2",
    descriptionAr: "إنشاء NotificationTemplate entity مع دعم المتغيرات والترجمة ثنائية اللغة",
    descriptionEn: "Create NotificationTemplate entity with variable support and bilingual translation",
    type: "Backend",
    priority: "Critical",
    status: "NOT_STARTED",
    dependencies: ["G1-T04"],
    acceptanceCriteriaAr: "NotificationTemplate يدعم القوالب ثنائية اللغة مع متغيرات ديناميكية",
    implementationNotesAr: "Handlebars or Mustache templating",
  },
  {
    id: "G2-T02",
    number: "G2-02",
    nameAr: "تنفيذ جدولة الإشعارات",
    nameEn: "Implement notification scheduling",
    groupCode: "G2",
    descriptionAr: "تنفيذ NotificationScheduler لجدولة الإشعارات بوقت محدد أو دوري",
    descriptionEn: "Implement NotificationScheduler for scheduling notifications at specific time or recurring",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G1-T04"],
    acceptanceCriteriaAr: "NotificationScheduler يجدول إشعارات بوقت محدد ودوري",
    implementationNotesAr: "Cron-based scheduler with persistence",
  },
  {
    id: "G2-T03",
    number: "G2-03",
    nameAr: "إنشاء واجهة إدارة القوالب",
    nameEn: "Create template management UI",
    groupCode: "G2",
    descriptionAr: "إنشاء صفحة إدارة القوالب مع محرر قوالب ثنائي اللغة",
    descriptionEn: "Create template management page with bilingual template editor",
    type: "Frontend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G2-T01"],
    acceptanceCriteriaAr: "صفحة إدارة القوالب تعمل مع RTL/LTR و304 مفتاح ترجمة",
    implementationNotesAr: "React component with form validation",
  },
  {
    id: "G2-T04",
    number: "G2-04",
    nameAr: "كتابة اختبارات القوالب والجدولة",
    nameEn: "Write template and scheduling tests",
    groupCode: "G2",
    descriptionAr: "كتابة اختبارات وحدة لنظام القوالب والجدولة",
    descriptionEn: "Write unit tests for template system and scheduling",
    type: "Test",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G2-T01", "G2-T02"],
    acceptanceCriteriaAr: "جميع اختبارات القوالب والجدولة تمر",
    implementationNotesAr: "Vitest with mock services",
  },

  // ── G3: Integration & Optimization ──────────────────────────────────────────
  {
    id: "G3-T01",
    number: "G3-01",
    nameAr: "تكامل مع CRM",
    nameEn: "CRM integration",
    groupCode: "G3",
    descriptionAr: "تكامل إشعارات CRM مع NotificationService (Lead, Opportunity events)",
    descriptionEn: "Integrate CRM notifications with NotificationService (Lead, Opportunity events)",
    type: "Backend",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G1-T04", "G2-T01"],
    acceptanceCriteriaAr: "أحداث CRM ترسل إشعارات عبر NotificationService",
    implementationNotesAr: "Event-driven integration via application events",
  },
  {
    id: "G3-T02",
    number: "G3-02",
    nameAr: "تحسين أداء الإشعارات المجمعة",
    nameEn: "Batch notification performance optimization",
    groupCode: "G3",
    descriptionAr: "تحسين أداء الإرسال المجمّع مع batch processing وasync processing",
    descriptionEn: "Optimize bulk sending with batch processing and async processing",
    type: "Backend",
    priority: "Medium",
    status: "NOT_STARTED",
    dependencies: ["G1-T04"],
    acceptanceCriteriaAr: "إرسال 1000 إشعار في أقل من 30 ثانية",
    implementationNotesAr: "Spring Async + thread pool configuration",
  },
  {
    id: "G3-T03",
    number: "G3-03",
    nameAr: "إنشاء لوحة تحليلات الإشعارات",
    nameEn: "Create notifications analytics dashboard",
    groupCode: "G3",
    descriptionAr: "إنشاء لوحة تحليلات تفاعلية لمعدلات الفتح والاستجابة",
    descriptionEn: "Create interactive analytics dashboard for open and response rates",
    type: "Frontend",
    priority: "Medium",
    status: "NOT_STARTED",
    dependencies: ["G2-T03"],
    acceptanceCriteriaAr: "لوحة التحليلات تعرض معدلات الفتح والاستجابة لكل قناة",
    implementationNotesAr: "Chart.js or Recharts with RTL support",
  },
  {
    id: "G3-T04",
    number: "G3-04",
    nameAr: "إعداد بوابة CI للإشعارات",
    nameEn: "Set up notifications CI gate",
    groupCode: "G3",
    descriptionAr: "إنشاء GitHub Actions workflow لاختبار الإشعارات وCI gate",
    descriptionEn: "Create GitHub Actions workflow for notification tests and CI gate",
    type: "DevOps",
    priority: "High",
    status: "NOT_STARTED",
    dependencies: ["G1-T05", "G2-T04"],
    acceptanceCriteriaAr: "notifications-ci.yml يمر مع جميع الاختبارات",
    implementationNotesAr: "GitHub Actions with Testcontainers",
  },
];
