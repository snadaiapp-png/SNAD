"use client";
import { createContext, useCallback, useContext, useState, type ReactNode } from "react";

export type CrmLang = "ar" | "en";
export type CrmDir = "rtl" | "ltr";

interface CrmI18nContextValue {
  lang: CrmLang; dir: CrmDir; toggleLang: () => void; setLang: (lang: CrmLang) => void; t: (key: string) => string;
}

const CrmI18nContext = createContext<CrmI18nContextValue | null>(null);
const STORAGE_KEY = "snad-crm-lang";

const translations: Record<string, { ar: string; en: string }> = {
  "crm.title.ar": { ar: "مركز قيادة CRM", en: "CRM Command Center" },
  "crm.subtitle": { ar: "نظام إدارة علاقات العملاء المتكامل", en: "Integrated Customer Relationship Management System" },
  "crm.backToWorkspace": { ar: "مساحة العمل", en: "Workspace" },
  "crm.langToggle": { ar: "English", en: "العربية" },
  "tab.overview": { ar: "النظرة العامة", en: "Overview" },
  "tab.leads": { ar: "العملاء المحتملون", en: "Leads" },
  "tab.customers": { ar: "العملاء", en: "Customers" },
  "tab.contacts": { ar: "جهات الاتصال", en: "Contacts" },
  "tab.opportunities": { ar: "الفرص البيعية", en: "Opportunities" },
  "tab.pipeline": { ar: "خط الأنابيب", en: "Pipeline" },
  "tab.tasks": { ar: "المهام", en: "Tasks" },
  "tab.transfers": { ar: "التحويلات", en: "Transfers" },
  "tab.employees": { ar: "الموظفون", en: "Employees" },
  "tab.reports": { ar: "التقارير", en: "Reports" },
  "tab.mobileSync": { ar: "مزامنة الجوال", en: "Mobile Sync" },
  "tab.callerId": { ar: "معرفة المتصل", en: "Caller Identification" },
  "tab.aiCrm": { ar: "ذكاء CRM", en: "AI CRM" },
  "tab.billing": { ar: "الفوترة واستخدام AI", en: "Billing & AI Usage" },
  "tab.settings": { ar: "الإعدادات", en: "Settings" },
  "tab.executionBoard": { ar: "لوحة تنفيذ CRM", en: "CRM Execution Board" },
  "empty.title": { ar: "هذه الوحدة قيد البناء", en: "This module is under construction" },
  "empty.subtitle": { ar: "سيتم تنفيذ هذه الوحدة في مرحلة لاحقة", en: "This module will be implemented in a later phase" },
  "empty.checkBoard": { ar: "تحقق من لوحة التنفيذ", en: "Check the Execution Board" },
  "empty.leads": { ar: "وحدة العملاء المحتملين قيد البناء", en: "Leads module is under construction" },
  "empty.customers": { ar: "وحدة العملاء قيد البناء", en: "Customers module is under construction" },
  "empty.contacts": { ar: "وحدة جهات الاتصال قيد البناء", en: "Contacts module is under construction" },
  "empty.opportunities": { ar: "وحدة الفرص البيعية قيد البناء", en: "Opportunities module is under construction" },
  "empty.pipeline": { ar: "وحدة خط الأنابيب قيد البناء", en: "Pipeline module is under construction" },
  "empty.tasks": { ar: "وحدة المهام قيد البناء", en: "Tasks module is under construction" },
  "empty.transfers": { ar: "وحدة التحويلات قيد البناء", en: "Transfers module is under construction" },
  "empty.employees": { ar: "وحدة الموظفين قيد البناء", en: "Employees module is under construction" },
  "empty.reports": { ar: "وحدة التقارير قيد البناء", en: "Reports module is under construction" },
  "empty.mobileSync": { ar: "وحدة مزامنة الجوال قيد البناء", en: "Mobile Sync module is under construction" },
  "empty.callerId": { ar: "وحدة معرفة المتصل قيد البناء", en: "Caller Identification module is under construction" },
  "empty.aiCrm": { ar: "وحدة ذكاء CRM قيد البناء", en: "AI CRM module is under construction" },
  "empty.billing": { ar: "وحدة الفوترة قيد البناء", en: "Billing module is under construction" },
  "empty.settings": { ar: "وحدة الإعدادات قيد البناء", en: "Settings module is under construction" },
  "sidebar.main": { ar: "الرئيسي", en: "Main" },
  "sidebar.advanced": { ar: "متقدم", en: "Advanced" },
  "sidebar.execution": { ar: "التنفيذ", en: "Execution" },
  "overview.welcome": { ar: "مرحباً بك في مركز قيادة CRM", en: "Welcome to CRM Command Center" },
  "overview.description": { ar: "هذا المركز المستقل لإدارة علاقات العملاء", en: "Independent CRM management center" },
  "overview.kpi.leads": { ar: "العملاء المحتملون", en: "Leads" },
  "overview.kpi.customers": { ar: "العملاء", en: "Customers" },
  "overview.kpi.opportunities": { ar: "الفرص النشطة", en: "Active Opportunities" },
  "overview.kpi.pipelineValue": { ar: "قيمة خط الأنابيب", en: "Pipeline Value" },
  "overview.underConstruction": { ar: "قيد البناء", en: "Under Construction" },
  "overview.executionSummary": { ar: "ملخص تنفيذ المشروع", en: "Project Execution Summary" },
  "overview.totalGroups": { ar: "إجمالي المجموعات", en: "Total Groups" },
  "overview.totalTasks": { ar: "إجمالي المهام", en: "Total Tasks" },
  "overview.completedTasks": { ar: "المهام المكتملة", en: "Completed Tasks" },
  "overview.blockedTasks": { ar: "المهام المحظورة", en: "Blocked Tasks" },
  "overview.overallProgress": { ar: "نسبة الإنجاز الكلية", en: "Overall Progress" },
  "board.title": { ar: "لوحة تنفيذ CRM", en: "CRM Execution Board" },
  "board.description": { ar: "مركز متابعة تنفيذ مشروع CRM", en: "CRM project execution tracking center" },
  "board.group": { ar: "المجموعة", en: "Group" },
  "board.status": { ar: "الحالة", en: "Status" },
  "board.progress": { ar: "نسبة الإنجاز", en: "Progress" },
  "board.tasks": { ar: "المهام", en: "Tasks" },
  "board.completed": { ar: "مكتملة", en: "Completed" },
  "board.remaining": { ar: "متبقية", en: "Remaining" },
  "board.blocked": { ar: "محظورة", en: "Blocked" },
  "board.dependencies": { ar: "الاعتماديات", en: "Dependencies" },
  "board.purpose": { ar: "الغرض", en: "Purpose" },
  "board.canParallelize": { ar: "قابل للموازاة مع", en: "Can parallelize with" },
  "board.stageReport": { ar: "تقرير المرحلة", en: "Stage Report" },
  "board.noReport": { ar: "لا يوجد تقرير بعد", en: "No report yet" },
  "board.noDependencies": { ar: "لا توجد", en: "None" },
  "board.parallelExecutionPlan": { ar: "خطة التنفيذ المتوازي", en: "Parallel Execution Plan" },
  "board.parallelPlanDesc": { ar: "المجموعات القابلة للتنفيذ بالتوازي", en: "Groups that can be executed in parallel" },
  "board.sequence": { ar: "تسلسل", en: "Sequence" },
  "status.NOT_STARTED": { ar: "لم تبدأ", en: "Not Started" },
  "status.IN_PROGRESS": { ar: "قيد التنفيذ", en: "In Progress" },
  "status.BLOCKED": { ar: "محظورة", en: "Blocked" },
  "status.DONE": { ar: "مكتملة", en: "Done" },
  "status.NEEDS_REVIEW": { ar: "بانتظار المراجعة", en: "Needs Review" },
  "status.APPROVED": { ar: "معتمدة", en: "Approved" },
  "status.REJECTED": { ar: "مرفوضة", en: "Rejected" },
  "type.Backend": { ar: "خلفية", en: "Backend" },
  "type.Frontend": { ar: "واجهة", en: "Frontend" },
  "type.Database": { ar: "قاعدة بيانات", en: "Database" },
  "type.API": { ar: "API", en: "API" },
  "type.Security": { ar: "أمن", en: "Security" },
  "type.Test": { ar: "اختبار", en: "Test" },
  "type.Report": { ar: "تقرير", en: "Report" },
  "type.Mobile": { ar: "جوال", en: "Mobile" },
  "type.AI": { ar: "ذكاء اصطناعي", en: "AI" },
  "type.Billing": { ar: "فوترة", en: "Billing" },
  "priority.Critical": { ar: "حرجة", en: "Critical" },
  "priority.High": { ar: "عالية", en: "High" },
  "priority.Medium": { ar: "متوسطة", en: "Medium" },
  "priority.Low": { ar: "منخفضة", en: "Low" },
  "common.loading": { ar: "جاري التحميل...", en: "Loading..." },
  "common.na": { ar: "—", en: "—" },
  "common.expand": { ar: "عرض التفاصيل", en: "Expand" },
  "common.collapse": { ar: "إخفاء التفاصيل", en: "Collapse" },
};

export function CrmI18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<CrmLang>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY) as CrmLang | null;
      if (stored === "ar" || stored === "en") return stored;
    } catch {}
    return "ar";
  });
  const setLang = useCallback((newLang: CrmLang) => {
    setLangState(newLang);
    try { localStorage.setItem(STORAGE_KEY, newLang); } catch {}
  }, []);
  const toggleLang = useCallback(() => setLang(lang === "ar" ? "en" : "ar"), [lang, setLang]);
  const t = useCallback((key: string) => {
    const entry = translations[key];
    if (!entry) return key;
    return entry[lang];
  }, [lang]);
  const dir: CrmDir = lang === "ar" ? "rtl" : "ltr";
  return <CrmI18nContext.Provider value={{ lang, dir, toggleLang, setLang, t }}>{children}</CrmI18nContext.Provider>;
}

export function useCrmI18n(): CrmI18nContextValue {
  const ctx = useContext(CrmI18nContext);
  if (!ctx) throw new Error("useCrmI18n must be used within a CrmI18nProvider");
  return ctx;
}
