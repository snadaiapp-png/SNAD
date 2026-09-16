import type { ReactNode } from "react";
import styles from "../workflow.module.css";

export function WorkflowSectionHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <div className={styles.sectionHeader}>
      <div className={styles.sectionHeaderText}>
        {eyebrow ? <span className={styles.sectionEyebrow}>{eyebrow}</span> : null}
        <h2 className={styles.sectionTitle}>{title}</h2>
        {description ? <p className={styles.sectionDescription}>{description}</p> : null}
      </div>
      {actions ? <div className={styles.sectionActions}>{actions}</div> : null}
    </div>
  );
}

export function WorkflowEmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className={styles.emptyState}>
      <span className={styles.emptyIcon} aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none">
          <path d="M6 7.5h12M6 12h8M6 16.5h5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <path d="M4.75 3.75h14.5a1 1 0 0 1 1 1v14.5a1 1 0 0 1-1 1H4.75a1 1 0 0 1-1-1V4.75a1 1 0 0 1 1-1Z" stroke="currentColor" strokeWidth="1.5" />
        </svg>
      </span>
      <h3 className={styles.emptyTitle}>{title}</h3>
      <p className={styles.emptyDescription}>{description}</p>
      {action}
    </div>
  );
}

export function StatusBadge({
  value,
  label,
}: {
  value: string | null | undefined;
  label?: string;
}) {
  const normalized = (value ?? "").toUpperCase();
  const className = [
    styles.badge,
    toneClass(normalized),
  ].join(" ");

  return (
    <span className={className} title={value || undefined}>
      {label ?? formatWorkflowStatus(value)}
    </span>
  );
}

function toneClass(value: string) {
  if (["OK", "HEALTHY", "COMPLETED", "COMPLETE", "PUBLISHED", "APPROVED", "RESOLVED", "ACTIVE", "SUCCESS"].includes(value)) {
    return styles.badgeSuccess;
  }
  if (["FAILED", "ERROR", "CRITICAL", "REJECTED", "CANCELLED", "EXPIRED"].includes(value)) {
    return styles.badgeError;
  }
  if (["OVERDUE", "PENDING", "OPEN", "HIGH", "ACKNOWLEDGED", "DRAFT", "ASSIGNEE_UNAVAILABLE"].includes(value)) {
    return styles.badgeWarning;
  }
  if (["RUNNING", "IN_PROGRESS", "CLAIMED", "ASSIGNED", "MEDIUM", "Y2"].includes(value)) {
    return styles.badgeInfo;
  }
  return styles.badgeNeutral;
}

const STATUS_LABELS: Record<string, string> = {
  OK: "سليم",
  HEALTHY: "سليم",
  COMPLETED: "مكتمل",
  COMPLETE: "مكتمل",
  RUNNING: "قيد التنفيذ",
  IN_PROGRESS: "قيد التنفيذ",
  FAILED: "فشل",
  ERROR: "خطأ",
  DRAFT: "مسودة",
  PUBLISHED: "منشور",
  RETIRED: "متقاعد",
  PENDING: "بانتظار الإجراء",
  APPROVED: "تمت الموافقة",
  REJECTED: "مرفوض",
  OPEN: "مفتوح",
  ACKNOWLEDGED: "تم الإقرار",
  RESOLVED: "محلول",
  CLAIMED: "مستلمة",
  ASSIGNED: "مُعيّنة",
  AVAILABLE: "متاحة",
  ACTIVE: "نشط",
  CANCELLED: "ملغي",
  EXPIRED: "منتهي",
  SUCCESS: "ناجح",
  CRITICAL: "حرج",
  HIGH: "مرتفع",
  MEDIUM: "متوسط",
  LOW: "منخفض",
  ASSIGNEE_UNAVAILABLE: "المُسند إليه غير متاح",
  BUSINESS: "سير عمل أعمال",
  SYSTEM_CANARY: "اختبار نظامي",
};

export function formatWorkflowStatus(value: string | null | undefined) {
  if (!value) return "—";
  return STATUS_LABELS[value.toUpperCase()] ?? value;
}

export function formatWorkflowDate(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("ar-SA", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

export function formatPriority(priority: number) {
  if (priority >= 80) return "حرجة";
  if (priority >= 50) return "مرتفعة";
  if (priority >= 20) return "متوسطة";
  return "عادية";
}
