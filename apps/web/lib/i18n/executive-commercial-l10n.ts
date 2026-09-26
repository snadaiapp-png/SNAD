import type { TranslationDictionary } from "./types";

export const EXECUTIVE_COMMERCIAL_I18N_AR = {
  "scp.tenants.resumeSubscription": "استئناف الاشتراك",
  "scp.tenants.createSubscription": "إنشاء اشتراك",
  "scp.tenants.createSuccessor": "إنشاء اشتراك لاحق",
  "scp.tenants.commercialBlocked": "الإجراء التجاري غير متاح لهذه الحالة",
  "scp.subscriptions.recurringAmount": "المبلغ المتكرر",
  "scp.subscriptions.annualRecurringAmount": "المبلغ السنوي المتكرر",
  "scp.subscriptions.monthlyRecurringAmount": "المبلغ الشهري المتكرر",
  "scp.subscriptions.create.section": "إنشاء اشتراك للمستأجر",
  "scp.subscriptions.create.plan": "الخطة",
  "scp.subscriptions.create.selectPlan": "اختر خطة نشطة",
  "scp.subscriptions.create.billingCycle": "دورة الفوترة",
  "scp.subscriptions.create.seatQuantity": "عدد المقاعد",
  "scp.subscriptions.create.submit": "إنشاء الاشتراك",
  "scp.subscriptions.create.success": "تم إنشاء الاشتراك بنجاح",
  "scp.subscriptions.createSuccessor.submit": "إنشاء الاشتراك اللاحق",
  "scp.subscriptions.createSuccessor.success": "تم إنشاء الاشتراك اللاحق بنجاح",
  "scp.subscriptions.resume.section": "استئناف الاشتراك",
  "scp.subscriptions.resume.submit": "استئناف الاشتراك",
  "scp.subscriptions.resume.success": "تم استئناف الاشتراك بنجاح",
  "scp.subscriptions.resume.unavailable": "لا يوجد اشتراك ملغى واحد مؤهل للاستئناف",
} satisfies TranslationDictionary;

export const EXECUTIVE_COMMERCIAL_I18N_EN = {
  "scp.tenants.resumeSubscription": "Resume subscription",
  "scp.tenants.createSubscription": "Create subscription",
  "scp.tenants.createSuccessor": "Create successor subscription",
  "scp.tenants.commercialBlocked": "Commercial action is unavailable for this state",
  "scp.subscriptions.recurringAmount": "Recurring amount",
  "scp.subscriptions.annualRecurringAmount": "Annual recurring amount",
  "scp.subscriptions.monthlyRecurringAmount": "Monthly recurring amount",
  "scp.subscriptions.create.section": "Create tenant subscription",
  "scp.subscriptions.create.plan": "Plan",
  "scp.subscriptions.create.selectPlan": "Select an active plan",
  "scp.subscriptions.create.billingCycle": "Billing cycle",
  "scp.subscriptions.create.seatQuantity": "Seat quantity",
  "scp.subscriptions.create.submit": "Create subscription",
  "scp.subscriptions.create.success": "Subscription created successfully",
  "scp.subscriptions.createSuccessor.submit": "Create successor subscription",
  "scp.subscriptions.createSuccessor.success": "Successor subscription created successfully",
  "scp.subscriptions.resume.section": "Resume subscription",
  "scp.subscriptions.resume.submit": "Resume subscription",
  "scp.subscriptions.resume.success": "Subscription resumed successfully",
  "scp.subscriptions.resume.unavailable": "No single cancelled subscription is eligible to resume",
} satisfies TranslationDictionary;

export function executiveCommercialDictionary(locale: "ar" | "en"): TranslationDictionary {
  return locale === "ar" ? EXECUTIVE_COMMERCIAL_I18N_AR : EXECUTIVE_COMMERCIAL_I18N_EN;
}
