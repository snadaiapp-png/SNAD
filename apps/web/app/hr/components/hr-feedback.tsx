"use client";

/**
 * Shared HR feedback states — WS5 Task 8.
 *
 * Loading, empty, forbidden and error states for every HR surface, in Arabic.
 * Error rendering understands the canonical v2 structured error envelope
 * (WS5 Task 2: HRM_* codes) and degrades to safe Arabic messages — internal
 * stack traces, SQL, or raw backend details are never displayed.
 */

import type { ReactNode } from "react";
import { isApiHttpError } from "@/lib/api/errors";
import { HrmV2ApiError, parseHrmV2Error } from "@/lib/api/hr-v2-api";
import styles from "../hr.module.css";

// ---------------------------------------------------------------------------
// Safe Arabic messages for canonical HRM v2 error codes / HTTP statuses.
// The list mirrors the WS5 Task 2 error model. Unknown codes fall back to a
// status-based safe message; nothing backend-internal is ever rendered.
// ---------------------------------------------------------------------------

const HRM_CODE_AR: Record<string, string> = {
  HRM_VALIDATION_FAILED: "تحقّق من البيانات المدخلة — بعض الحقول غير صحيحة.",
  HRM_CONCURRENCY_CONFLICT: "تم تعديل السجل من مستخدم آخر. حدّث الصفحة وأعد المحاولة دون الكتابة فوق التغييرات.",
  HRM_IDEMPOTENCY_CONFLICT: "تم إرسال الطلب نفسه مسبقًا بمحتوى مختلف. أعد المحاولة بطلب جديد.",
  HRM_MIGRATION_REQUIRED: "بيانات هذا الموظف غير مكتملة الترحيل بعد، ولا يمكن تنفيذ العملية حتى إكمال الترحيل.",
  HRM_INVALID_STATE_TRANSITION: "لا يمكن تنفيذ هذه العملية في حالة السجل الحالية.",
  HRM_ACTIVATION_BLOCKED: "لا يمكن التنشيط — هناك متطلبات لم تكتمل بعد.",
  HRM_POSITION_OCCUPIED: "المنصب مشغول حاليًا بموظف آخر.",
  HRM_ASSIGNMENT_OVERLAP: "توجد إسناد ساري في نفس الفترة. عدّل الفترة الفعلية ثم أعد المحاولة.",
  HRM_SCOPE_DENIED: "لا تملك الصلاحية اللازمة لهذه العملية.",
  HRM_COUNTRY_PACK_NOT_CERTIFIED: "حزمة الالتزام المحلية غير معتمدة بعد لهذه العملية.",
  HRM_COMPLIANCE_BLOCKED: "عملية محظورة بموجب قاعدة التزام قانونية، ولا يمكن تجاوزها.",
  HRM_OVERRIDE_APPROVAL_REQUIRED: "هذه العملية تحتاج موافقة تجاوز من موافقٍ مستقل قبل التنفيذ.",
  HRM_LEGAL_REVIEW_REQUIRED: "هذه العملية معلّقة بانتظار المراجعة القانونية.",
};

const STATUS_AR: Record<number, string> = {
  400: "تحقّق من البيانات المدخلة — بعض الحقول غير صحيحة.",
  401: "انتهت صلاحية الجلسة. أعد تسجيل الدخول ثم حاول مجددًا.",
  403: "لا تملك الصلاحية اللازمة لهذه العملية.",
  404: "العنصر المطلوب غير موجود، أو لا تملك صلاحية الاطلاع عليه.",
  409: "تعذر تنفيذ العملية بسبب تعارض في البيانات أو في حالة السجل.",
  422: "لا يمكن تنفيذ العملية بسبب حالة التزام أو سياسة نظام.",
  500: "حدث خطأ غير متوقع في الخدمة. حاول مجددًا بعد قليل.",
};

const FALLBACK_AR = "تعذر إكمال العملية. حاول مجددًا بعد قليل، وإذا استمرت المشكلة تواصل مع مسؤول النظام.";

/** Map any thrown value to a safe, user-facing Arabic message. */
export function hrmErrorMessage(err: unknown): { message: string; kind: "forbidden" | "notfound" | "conflict" | "compliance" | "validation" | "auth" | "server" | "generic" } {
  const hrm = parseHrmV2Error(err);
  if (hrm) {
    const kind: ReturnType<typeof hrmErrorMessage>["kind"] =
      hrm.status === 403 || hrm.code === "HRM_SCOPE_DENIED" ? "forbidden"
        : hrm.status === 404 ? "notfound"
        : hrm.status === 409 ? "conflict"
        : hrm.status === 422 ? "compliance"
        : hrm.status === 400 ? "validation"
        : hrm.status === 401 ? "auth"
        : "server";
    return { message: HRM_CODE_AR[hrm.code] ?? STATUS_AR[hrm.status] ?? FALLBACK_AR, kind };
  }
  if (isApiHttpError(err)) {
    const status = err.status;
    const kind: ReturnType<typeof hrmErrorMessage>["kind"] =
      status === 403 ? "forbidden" : status === 404 ? "notfound" : status === 409 ? "conflict"
        : status === 422 ? "compliance" : status === 400 ? "validation" : status === 401 ? "auth" : "server";
    return { message: STATUS_AR[status] ?? FALLBACK_AR, kind };
  }
  return { message: FALLBACK_AR, kind: "generic" };
}

// ---------------------------------------------------------------------------
// State components
// ---------------------------------------------------------------------------

export function HrLoading({ label = "جارٍ التحميل…" }: { label?: string }) {
  return (
    <div role="status" aria-live="polite" className={styles.feedbackLoading}>
      <span className={styles.feedbackSpinner} aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function HrEmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className={styles.feedbackEmpty}>
      <p className={styles.feedbackEmptyTitle}>{title}</p>
      {description ? <p className={styles.feedbackEmptyDescription}>{description}</p> : null}
      {action ? <div className={styles.feedbackEmptyAction}>{action}</div> : null}
    </div>
  );
}

/** UX-only capability gate message — backend 403 remains authoritative. */
export function HrForbidden({ description = "لا تملك الصلاحية اللازمة لعرض هذا القسم. تواصل مع مسؤول النظام إذا كنت تحتاج وصولًا." }: { description?: string }) {
  return (
    <div role="alert" className={styles.feedbackForbidden}>
      <p className={styles.feedbackTitle}>القسم غير متاح</p>
      <p>{description}</p>
    </div>
  );
}

/**
 * Error state for any HR operation. Renders the safe Arabic message only;
 * violations (field errors) are listed without exposing backend internals.
 */
export function HrErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { message, kind } = hrmErrorMessage(error);
  const violations = error instanceof HrmV2ApiError ? error.violations ?? [] : [];
  return (
    <div role="alert" className={styles.feedbackError} data-kind={kind}>
      <p className={styles.feedbackTitle}>
        {kind === "forbidden" ? "عملية غير مصرّح بها"
          : kind === "notfound" ? "غير موجود"
          : kind === "conflict" ? "تعارض في البيانات"
          : kind === "compliance" ? "قيود التزام"
          : "حدث خطأ"}
      </p>
      <p>{message}</p>
      {violations.length > 0 ? (
        <ul className={styles.feedbackViolations}>
          {violations.map((v, i) => (
            <li key={`${v.field}-${i}`}>{v.message}</li>
          ))}
        </ul>
      ) : null}
      {onRetry ? (
        <button type="button" className={styles.feedbackRetry} onClick={onRetry}>
          إعادة المحاولة
        </button>
      ) : null}
    </div>
  );
}
