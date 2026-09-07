import {
  ApiClientError,
  ApiHttpError,
  ApiNetworkError,
  ApiTimeoutError,
} from "@/lib/api/errors";

/**
 * User-facing Arabic error messages for the Workflow module.
 *
 * Contract (Workflow Y2 production hotfix, 2026-09-04):
 *  - The end user NEVER sees raw transport details (HTTP codes, URLs,
 *    backend stack traces). Raw details stay in the browser console with
 *    the request id for support.
 *  - 401/403/5xx map to explicit Arabic guidance with a recovery action.
 *  - A request failure must never be rendered as an "empty state".
 */

export const WORKFLOW_ERROR_MESSAGES = {
  sessionExpired:
    "انتهت صلاحية الجلسة أو لم يتم تسجيل الدخول. سجّل الدخول من جديد ثم أعد المحاولة.",
  forbidden: "لا تملك صلاحية الوصول إلى هذه البيانات. تواصل مع مسؤول النظام إذا كنت تعتقد أن هذا خطأ.",
  notFound: "البيانات المطلوبة غير متوفرة حالياً. قد تكون قد حُذفت أو أن الرابط غير صحيح.",
  conflict: "تم تحديث البيانات من قبل مستخدم آخر. سيتم تحميل النسخة الأحدث.",
  server: "حدث خطأ في الخادم أثناء تنفيذ طلبك. أعد المحاولة بعد قليل، وإذا استمرت المشكلة تواصل مع الدعم.",
  unknownStatus: "تعذر إكمال الطلب. أعد المحاولة، وإذا استمرت المشكلة تواصل مع الدعم.",
  timeout: "استغرق الطلب وقتاً أطول من المعتاد وتم إلغاؤه. أعد المحاولة.",
  network: "تعذر الاتصال بالخادم. تحقق من اتصال الشبكة ثم أعد المحاولة.",
  unexpected: "حدث خطأ غير متوقع. أعد المحاولة، وإذا استمرت المشكلة تواصل مع الدعم.",
} as const;

/**
 * Map any thrown value to a safe, user-facing Arabic message.
 * The second argument is the module-specific fallback shown for
 * unclassified failures (e.g. "تعذر تحميل المهام").
 */
export function describeWorkflowError(error: unknown, fallback: string): string {
  if (error instanceof ApiTimeoutError) {
    console.error("[workflow] request timed out", error.toSafeSummary());
    return WORKFLOW_ERROR_MESSAGES.timeout;
  }
  if (error instanceof ApiNetworkError) {
    console.error("[workflow] network failure", error.toSafeSummary());
    return WORKFLOW_ERROR_MESSAGES.network;
  }
  if (error instanceof ApiHttpError) {
    // Raw transport details for diagnostics only — never rendered to users.
    console.error("[workflow] http failure", error.toSafeSummary());
    const status = error.status;
    if (status === 401) return WORKFLOW_ERROR_MESSAGES.sessionExpired;
    if (status === 403) return WORKFLOW_ERROR_MESSAGES.forbidden;
    if (status === 404) return WORKFLOW_ERROR_MESSAGES.notFound;
    if (status === 409) return WORKFLOW_ERROR_MESSAGES.conflict;
    if (status >= 500) return WORKFLOW_ERROR_MESSAGES.server;
    return fallback || WORKFLOW_ERROR_MESSAGES.unknownStatus;
  }
  if (error instanceof ApiClientError) {
    console.error("[workflow] api failure", error.toSafeSummary());
    return fallback || WORKFLOW_ERROR_MESSAGES.unknownStatus;
  }
  console.error("[workflow] unexpected failure", error);
  return WORKFLOW_ERROR_MESSAGES.unexpected;
}
