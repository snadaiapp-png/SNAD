/**
 * POS Execution Data
 * ------------------
 * Business data for POS execution groups and tasks.
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

// ── POS-Specific Task Type ────────────────────────────────────────────────

/**
 * POS Task — Business data for a POS task.
 *
 * NOTE: This is NOT an ExecutionTask. The PosExecutionProvider
 * converts this to ExecutionTask when providing data to the framework.
 */
export interface PosTask {
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

// ── POS Execution Groups ──────────────────────────────────────────────────

/**
 * POS Execution Groups — Business data for group definitions.
 *
 * NOTE: This is NOT an ExecutionGroup[]. The PosExecutionProvider
 * converts this to ExecutionGroup[] when providing data to the framework.
 */
export const POS_GROUP_DATA = [
  {
    code: "G0",
    titleAr: "التأسيس وإعداد المحطة",
    titleEn: "Foundation & Terminal Setup",
    purposeAr: "إعداد البنية التحتية الأساسية لنقطة البيع وكتالوج المنتجات.",
    purposeEn: "Establish POS infrastructure and product catalog foundation.",
    status: "IN_PROGRESS" as GroupStatus,
    dependencies: [],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G1",
    titleAr: "الميزات الأساسية للمبيعات والإرجاع",
    titleEn: "Core Sales & Returns Features",
    purposeAr: "تنفيذ عمليات البيع الأساسية والإرجاع واستبدال المنتجات.",
    purposeEn: "Implement core sales transactions, returns, and product exchanges.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G0"],
    canParallelizeWith: [],
    stageReport: null,
  },
  {
    code: "G2",
    titleAr: "معالجة المدفوعات",
    titleEn: "Payment Processing",
    purposeAr: "تنفيذ بوابات الدفع النقدية والبطاقات والدفع الإلكتروني والمحفظة الرقمية.",
    purposeEn: "Implement cash, card, mobile payment, and digital wallet gateways.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1"],
    canParallelizeWith: ["G3"],
    stageReport: null,
  },
  {
    code: "G3",
    titleAr: "الإيصالات والتقارير",
    titleEn: "Receipts & Reporting",
    purposeAr: "تنفيذ طباعة الإيصالات وتقارير المبيعات اليومية والجرد.",
    purposeEn: "Implement receipt printing, daily sales reports, and inventory reconciliation.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G1"],
    canParallelizeWith: ["G2"],
    stageReport: null,
  },
  {
    code: "G4",
    titleAr: "التكامل والتحسين",
    titleEn: "Integration & Optimization",
    purposeAr: "تكامل مع المخزون وCRM وتحسين الأداء والخبرة.",
    purposeEn: "Integration with Inventory and CRM modules, performance optimization.",
    status: "NOT_STARTED" as GroupStatus,
    dependencies: ["G2", "G3"],
    canParallelizeWith: [],
    stageReport: null,
  },
];

// ── POS Tasks ─────────────────────────────────────────────────────────────

export const POS_TASKS: PosTask[] = [
  // ── G0: Foundation & Terminal Setup ──────────────────────────────────────
  { id: "G0-T01", number: "G0-01", nameAr: "إنشاء مسار /pos", nameEn: "Create /pos route", groupCode: "G0", descriptionAr: "إنشاء الصفحة الرئيسية لنقطة البيع", descriptionEn: "Create POS main page", type: "Frontend", priority: "Critical", status: "IN_PROGRESS", dependencies: [], acceptanceCriteriaAr: "المسار /pos يفتح لوحة نقطة البيع", implementationNotesAr: "إنشاء page.tsx في apps/web/app/pos" },
  { id: "G0-T02", number: "G0-02", nameAr: "إنشاء تخطيط POS مستقل", nameEn: "Create independent POS layout", groupCode: "G0", descriptionAr: "تخطيط مستقل مع شريط جانبي وقائمة التنقل", descriptionEn: "Independent layout with sidebar and navigation", type: "Frontend", priority: "Critical", status: "NOT_STARTED", dependencies: ["G0-T01"], acceptanceCriteriaAr: "التخطيط يحتوي شريط جانبي وقائمة POS", implementationNotesAr: "layout.tsx مستقل للـ POS" },
  { id: "G0-T03", number: "G0-03", nameAr: "إنشاء صفحة الـ Dashboard", nameEn: "Create POS Dashboard page", groupCode: "G0", descriptionAr: "صفحة لوحة التحكم الرئيسية مع KPIs", descriptionEn: "Main dashboard page with KPIs", type: "Frontend", priority: "High", status: "NOT_STARTED", dependencies: ["G0-T02"], acceptanceCriteriaAr: "عرض KPIs: المبيعات اليومية، عدد المعاملات، متوسط السلة", implementationNotesAr: "لوحة تحكم مع إحصائيات حية" },
  { id: "G0-T04", number: "G0-04", nameAr: "إعداد كتالوج المنتجات", nameEn: "Setup product catalog", groupCode: "G0", descriptionAr: "جداول وواجهات إدارة المنتجات والتصنيفات وال Unit of Measure", descriptionEn: "Tables and interfaces for products, categories, UOM management", type: "Database", priority: "Critical", status: "NOT_STARTED", dependencies: [], acceptanceCriteriaAr: "جدول pos_products يدعم SKU والسعر والتصنيف", implementationNotesAr: "依赖 Inventory module للمنتجات" },
  { id: "G0-T05", number: "G0-05", nameAr: "إعداد جدول المحطات", nameEn: "Setup terminal table", groupCode: "G0", descriptionAr: "جدول لتخزين معلومات محطة البيع (رقم المحطة، الموقع، الحالة)", descriptionEn: "Table for storing terminal info (terminal number, location, status)", type: "Database", priority: "High", status: "NOT_STARTED", dependencies: ["G0-T04"], acceptanceCriteriaAr: "جدول pos_terminals مع tenant_id و terminal_number", implementationNotesAr: "دعم متعدد المحطات" },
  { id: "G0-T06", number: "G0-06", nameAr: "إعداد جدول المبيعات", nameEn: "Setup sales table", groupCode: "G0", descriptionAr: "جدول لتسجيل جميع معاملات البيع", descriptionEn: "Table for recording all sales transactions", type: "Database", priority: "Critical", status: "NOT_STARTED", dependencies: ["G0-T04"], acceptanceCriteriaAr: "جدول pos_sales مع total_amount و payment_status", implementationNotesAr: "رئيسي في نظام POS" },
  { id: "G0-T07", number: "G0-07", nameAr: "إعداد جدول بنود المبيعات", nameEn: "Setup sale items table", groupCode: "G0", descriptionAr: "جدول لتخزين بنود كل عملية بيع (المنتج، الكمية، السعر)", descriptionEn: "Table for storing sale line items (product, quantity, price)", type: "Database", priority: "Critical", status: "NOT_STARTED", dependencies: ["G0-T06"], acceptanceCriteriaAr: "جدول pos_sale_items مع product_id و quantity و unit_price", implementationNotesAr: "علاقة مع pos_sales" },
  { id: "G0-T08", number: "G0-08", nameAr: "إنشاء ترحيلات قاعدة البيانات", nameEn: "Create database migrations", groupCode: "G0", descriptionAr: "ملفات ترحيل Flyway لجميع جداول POS", descriptionEn: "Flyway migration files for all POS tables", type: "Database", priority: "Critical", status: "NOT_STARTED", dependencies: ["G0-T04", "G0-T05", "G0-T06", "G0-T07"], acceptanceCriteriaAr: "جميع الترحيلات تطبق بنجاح", implementationNotesAr: "Flyway migrations مع tenant isolation" },
  { id: "G0-T09", number: "G0-09", nameAr: "تطبيق عزل المستأجرين", nameEn: "Implement tenant isolation", groupCode: "G0", descriptionAr: "تطبيق tenant_id على جميع جداول POS مع فهارس", descriptionEn: "Apply tenant_id on all POS tables with indexes", type: "Security", priority: "Critical", status: "NOT_STARTED", dependencies: ["G0-T08"], acceptanceCriteriaAr: "جميع الجداول تدعم العزل بين المؤسسات", implementationNotesAr: "نفس نمط CRM للعزل" },

  // ── G1: Core Sales & Returns Features ────────────────────────────────────
  { id: "G1-T01", number: "G1-01", nameAr: "إنشاء واجهة نقطة البيع", nameEn: "Create POS terminal interface", groupCode: "G1", descriptionAr: "واجهة عرض المنتجات مع البحث والتصنيفات وعربة التسوق", descriptionEn: "Product display interface with search, categories, and cart", type: "Frontend", priority: "Critical", status: "NOT_STARTED", dependencies: ["G0-T02", "G0-T04"], acceptanceCriteriaAr: "عرض المنتجات في شبكة مع بحث فوري", implementationNotesAr: "واجهة عصرية وسريعة" },
  { id: "G1-T02", number: "G1-02", nameAr: "تنفيذ سلة التسوق", nameEn: "Implement shopping cart", groupCode: "G1", descriptionAr: "سلة تسوق تفاعلية مع تعديل الكمية والخصم", descriptionEn: "Interactive cart with quantity adjustment and discounts", type: "Frontend", priority: "Critical", status: "NOT_STARTED", dependencies: ["G1-T01"], acceptanceCriteriaAr: "إضافة منتجات للسلة وتعديل الكمية وتطبيق خصومات", implementationNotesAr: "React state management للسلة" },
  { id: "G1-T03", number: "G1-03", nameAr: "تنفيذ عملية البيع", nameEn: "Implement sale transaction", groupCode: "G1", descriptionAr: "تسجيل عملية البيع في قاعدة البيانات مع تحديث المخزون", descriptionEn: "Record sale transaction in database with inventory update", type: "Backend", priority: "Critical", status: "NOT_STARTED", dependencies: ["G1-T02", "G0-T06", "G0-T07"], acceptanceCriteriaAr: "عملية البيع تحفظ sale + sale_items وتحدث المخزون", implementationNotesAr: "Transaction آمنة عبر PostgreSQL" },
  { id: "G1-T04", number: "G1-04", nameAr: "تنفيذ البحث عن المنتجات", nameEn: "Implement product search", groupCode: "G1", descriptionAr: "بحث سريع بالاسم أو الباركود مع اقتراحات فورية", descriptionEn: "Fast search by name or barcode with instant suggestions", type: "Backend", priority: "High", status: "NOT_STARTED", dependencies: ["G0-T04"], acceptanceCriteriaAr: "بحث يعرض النتائج في أقل من 200ms", implementationNotesAr: "Full-text search + barcode scanner support" },
  { id: "G1-T05", number: "G1-05", nameAr: "تنفيذ الإرجاع والاستبدال", nameEn: "Implement returns & exchanges", groupCode: "G1", descriptionAr: "معالجة إرجاع المنتجات واستبدالها مع رد المبلغ", descriptionEn: "Process product returns and exchanges with refund", type: "Backend", priority: "High", status: "NOT_STARTED", dependencies: ["G1-T03"], acceptanceCriteriaAr: "إرجاع كامل أو جزئي مع تحديث المخزون", implementationNotesAr: "سجل إرجاع مع سبب الإرجاع" },
  { id: "G1-T06", number: "G1-06", nameAr: "تنفيذ الخصومات والكوبونات", nameEn: "Implement discounts & coupons", groupCode: "G1", descriptionAr: "نظام خصومات مرنة مع كوبونات وخصومات خاصة", descriptionEn: "Flexible discount system with coupons and special discounts", type: "Backend", priority: "Medium", status: "NOT_STARTED", dependencies: ["G1-T03"], acceptanceCriteriaAr: "خصم نسبة أو مبلغ ثابت + كوبونات صالحة", implementationNotesAr: "discount_rules table" },

  // ── G2: Payment Processing ──────────────────────────────────────────────
  { id: "G2-T01", number: "G2-01", nameAr: "تنفيذ الدفع النقدي", nameEn: "Implement cash payment", groupCode: "G2", descriptionAr: "معالجة المدفوعات النقدية مع حساب الباقي", descriptionEn: "Process cash payments with change calculation", type: "Backend", priority: "Critical", status: "NOT_STARTED", dependencies: ["G1-T03"], acceptanceCriteriaAr: "إدخال المبلغ المستلم وحساب الباقي تلقائياً", implementationNotesAr: "أبسط طريقة دفع" },
  { id: "G2-T02", number: "G2-02", nameAr: "تنفيذ الدفع بالبطاقات", nameEn: "Implement card payment", groupCode: "G2", descriptionAr: "تكامل مع بوابة الدفع بالبطاقات الائتمانية", descriptionEn: "Integration with credit card payment gateway", type: "API", priority: "Critical", status: "NOT_STARTED", dependencies: ["G2-T01"], acceptanceCriteriaAr: "قبول فيزا، ماستركارد، مدى", implementationNotesAr: "تكامل مع بوابة دفع محلية" },
  { id: "G2-T03", number: "G2-03", nameAr: "تنفيذ الدفع عبر الجوال", nameEn: "Implement mobile payment", groupCode: "G2", descriptionAr: "دعم Apple Pay و Google Pay والدفع بال-QR Code", descriptionEn: "Support Apple Pay, Google Pay, and QR code payments", type: "API", priority: "High", status: "NOT_STARTED", dependencies: ["G2-T01"], acceptanceCriteriaAr: "قبول الدفع عبر التطبيقات المحمولة", implementationNotesAr: "NFC و QR code" },
  { id: "G2-T04", number: "G2-04", nameAr: "تنفيذ المحفظة الرقمية", nameEn: "Implement digital wallet", groupCode: "G2", descriptionAr: "نظام محفظة داخلي للمشاهرين مع شحن وصرف", descriptionEn: "Internal customer wallet system with top-up and spend", type: "Backend", priority: "Medium", status: "NOT_STARTED", dependencies: ["G2-T01"], acceptanceCriteriaAr: "شحن المحفظة وصرفها في المبيعات", implementationNotesAr: "wallet_balance table" },
  { id: "G2-T05", number: "G2-05", nameAr: "تنفيذ سجل المدفوعات", nameEn: "Implement payment records", groupCode: "G2", descriptionAr: "جدول لتسجيل جميع المعاملات المالية لكل عملية بيع", descriptionEn: "Table for recording all financial transactions per sale", type: "Database", priority: "Critical", status: "NOT_STARTED", dependencies: ["G2-T01", "G2-T02"], acceptanceCriteriaAr: "سجل كامل لكل طريقة دفع مستخدمة", implementationNotesAr: "pos_payment_records table" },

  // ── G3: Receipts & Reporting ────────────────────────────────────────────
  { id: "G3-T01", number: "G3-01", nameAr: "تنفيذ إنشاء الإيصال", nameEn: "Implement receipt generation", groupCode: "G3", descriptionAr: "إنشاء إيصال رقمي مع جميع تفاصيل البيع", descriptionEn: "Generate digital receipt with all sale details", type: "Frontend", priority: "Critical", status: "NOT_STARTED", dependencies: ["G1-T03"], acceptanceCriteriaAr: "إيصال يحتوي اسم المتجر والمنتجات وال.total والضريبة", implementationNotesAr: "PDF أو HTML receipt" },
  { id: "G3-T02", number: "G3-02", nameAr: "تنفيذ طباعة الإيصال", nameEn: "Implement receipt printing", groupCode: "G3", descriptionAr: "طباعة الإيصال على طابعة حرارية", descriptionEn: "Print receipt on thermal printer", type: "Frontend", priority: "High", status: "NOT_STARTED", dependencies: ["G3-T01"], acceptanceCriteriaAr: "طباعة ناجحة على طابعة حرارية 80mm", implementationNotesAr: "ESC/POS commands" },
  { id: "G3-T03", number: "G3-03", nameAr: "تنفيذ تقرير المبيعات اليومية", nameEn: "Implement daily sales report", groupCode: "G3", descriptionAr: "تقرير يومي شامل بالمبيعات والإيرادات والضرائب", descriptionEn: "Comprehensive daily report with sales, revenue, taxes", type: "Backend", priority: "Critical", status: "NOT_STARTED", dependencies: ["G1-T03"], acceptanceCriteriaAr: "تقرير يعرض إجمالي المبيعات والضرائب والصافي", implementationNotesAr: "SQL queries للإحصائيات اليومية" },
  { id: "G3-T04", number: "G3-04", nameAr: "تنفيذ تقرير جرد المبيعات", nameEn: "Implement sales reconciliation report", groupCode: "G3", descriptionAr: "تقرير مطابقة المبيعات مع المدفوعات والمحفظة", descriptionEn: "Report matching sales with payments and wallet", type: "Backend", priority: "High", status: "NOT_STARTED", dependencies: ["G3-T03", "G2-T05"], acceptanceCriteriaAr: "تقرير يتحقق من تطابق المبيعات والمدفوعات", implementationNotesAr: "Reconciliation engine" },
  { id: "G3-T05", number: "G3-05", nameAr: "تنفيذ إغلاق يوم POS", nameEn: "Implement POS end-of-day close", groupCode: "G3", descriptionAr: "عملية إغلاق يومية مع عد النقود وإغلاق المحطة", descriptionEn: "Daily close process with cash count and terminal close", type: "Frontend", priority: "High", status: "NOT_STARTED", dependencies: ["G3-T03"], acceptanceCriteriaAr: "إغلاق يومي يحفظ snapshot المبيعات", implementationNotesAr: "end_of_day procedure" },

  // ── G4: Integration & Optimization ──────────────────────────────────────
  { id: "G4-T01", number: "G4-01", nameAr: "تكامل مع وحدة المخزون", nameEn: "Integration with Inventory module", groupCode: "G4", descriptionAr: "مزامنة المنتجات والمخزون مع وحدة المخزون", descriptionEn: "Sync products and stock with Inventory module", type: "API", priority: "Critical", status: "NOT_STARTED", dependencies: ["G1-T03"], acceptanceCriteriaAr: "تحديث المخزون تلقائياً عند كل بيع", implementationNotesAr: "API integration مع Inventory" },
  { id: "G4-T02", number: "G4-02", nameAr: "تكامل مع وحدة CRM", nameEn: "Integration with CRM module", groupCode: "G4", descriptionAr: "ربط المبيعات بملفات العملاء في CRM", descriptionEn: "Link sales to customer profiles in CRM", type: "API", priority: "Medium", status: "NOT_STARTED", dependencies: ["G1-T03"], acceptanceCriteriaAr: "تسجيل عميل في كل عملية بيع اختيارياً", implementationNotesAr: "API integration مع CRM" },
  { id: "G4-T03", number: "G4-03", nameAr: "تحسين أداء الاستعلامات", nameEn: "Optimize query performance", groupCode: "G4", descriptionAr: "تحسين استعلامات قاعدة البيانات للأجهزة المحمولة", descriptionEn: "Optimize database queries for mobile devices", type: "Backend", priority: "Medium", status: "NOT_STARTED", dependencies: ["G0-T08"], acceptanceCriteriaAr: "استعلامات أقل من 100ms", implementationNotesAr: "Connection pooling + query optimization" },
  { id: "G4-T04", number: "G4-04", nameAr: "وضع عدم الاتصال", nameEn: "Offline mode support", groupCode: "G4", descriptionAr: "دعم عمل POS بدون اتصال بالإنترنت مع مزامنة لاحقة", descriptionEn: "Support POS operation offline with later sync", type: "Frontend", priority: "High", status: "NOT_STARTED", dependencies: ["G1-T03"], acceptanceCriteriaAr: "تسجيل مبيعات بدون إنترنت والمزامنة عند الاتصال", implementationNotesAr: "Service Worker + IndexedDB" },
  { id: "G4-T05", number: "G4-05", nameAr: "اختبارات الأداء والقبول", nameEn: "Performance & acceptance tests", groupCode: "G4", descriptionAr: "اختبارات شاملة لأداء POS وقبول المستخدم", descriptionEn: "Comprehensive POS performance and acceptance tests", type: "Test", priority: "High", status: "NOT_STARTED", dependencies: ["G4-T01", "G4-T03"], acceptanceCriteriaAr: "جميع الاختبارات تمر وأداء مقبول", implementationNotesAr: "Vitest + Playwright tests" },
];
