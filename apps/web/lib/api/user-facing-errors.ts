/**
 * User-facing error mapper — converts API client errors into safe,
 * Arabic-language messages suitable for display to end users.
 *
 * Rules:
 * - Never expose stack traces.
 * - Never expose internal URLs.
 * - Never expose request headers.
 * - Never expose raw response bodies.
 * - Never expose tokens, cookies, or authorization data.
 * - Never expose database details.
 * - Never leak backend topology words (Backend / ngrok / Vercel / Next / NEXT_PUBLIC_*).
 * - Never assume a message is safe just because it contains Arabic characters.
 *
 * EXEC-PROMPT-SANAD-FULLSTACK-REMEDIATION-010 changes:
 *   - Backend 401 on login → "تعذر تسجيل الدخول / البريد الإلكتروني أو كلمة المرور غير صحيحة."
 *     (previously "غير مصرح" — too generic, and the backend-supplied Arabic
 *     message could reveal account-state distinctions like "user not found" vs
 *     "password wrong". We now use a fixed, identical message for any invalid
 *     credential case, which closes the account-enumeration side channel).
 *   - 401 elsewhere (post-session expiry) → "انتهت الجلسة" rather than "غير مصرح".
 *   - Configuration errors no longer name "Backend" or "NEXT_PUBLIC_API_BASE_URL";
 *     those tokens are reserved for operator logs.
 *   - Backend-supplied messages are routed through an allowlist of known-safe
 *     prefixes; arbitrary Arabic strings are NOT surfaced to the user any more.
 */

import {
  ApiConfigurationError,
  ApiTimeoutError,
  ApiNetworkError,
  ApiHttpError,
  ApiResponseParseError,
  ApiRequestSerializationError,
  isApiClientError,
} from "./errors";
import { ApiClientCancellation } from "./client";

export interface UserFacingError {
  title: string;
  message: string;
  kind:
    | "configuration"
    | "network"
    | "timeout"
    | "cancellation"
    | "not-found"
    | "conflict"
    | "validation"
    | "invalid-credentials"
    | "session-expired"
    | "server"
    | "parse"
    | "serialization"
    | "unknown";
}

/**
 * Hint to the mapper so the same HTTP 401 can produce two different
 * user-facing messages depending on the operation context:
 *   - login flow → "تعذر تسجيل الدخول" (credential rejection)
 *   - any other authenticated call → "انتهت الجلسة" (session expired)
 */
export interface UserFacingErrorContext {
  /** True when the failing request is a login attempt (POST /auth/login). */
  isLoginAttempt?: boolean;
}

export function toUserFacingError(err: unknown, ctx: UserFacingErrorContext = {}): UserFacingError {
  if (err instanceof ApiClientCancellation) {
    return { title: "تم إلغاء الطلب", message: "تم إلغاء العملية ولم تكتمل.", kind: "cancellation" };
  }
  if (err instanceof ApiConfigurationError) {
    return {
      title: "إعداد غير مكتمل",
      // Operator-only terms (Backend / NEXT_PUBLIC_API_BASE_URL / Vercel / ngrok)
      // are intentionally omitted from the user-facing copy.
      message: "لم يكتمل إعداد خدمة تسجيل الدخول. تواصل مع مدير النظام.",
      kind: "configuration",
    };
  }
  if (err instanceof ApiTimeoutError) {
    return { title: "انتهت مهلة الطلب", message: "استغرق الطلب وقتًا طويلًا. حاول مرة أخرى.", kind: "timeout" };
  }
  if (err instanceof ApiNetworkError) {
    return {
      title: "تعذر الاتصال بالخادم",
      message: "لا يمكن الوصول إلى الخدمة الآن. تحقق من اتصال الشبكة وحاول مرة أخرى.",
      kind: "network",
    };
  }
  if (err instanceof ApiRequestSerializationError) {
    return {
      title: "تعذر تسجيل الطلب",
      message: "تعذر تحويل البيانات إلى صيغة قابلة للإرسال. راجع الحقول وأعد المحاولة.",
      kind: "serialization",
    };
  }
  if (err instanceof ApiResponseParseError) {
    return { title: "استجابة غير صالحة", message: "أرجعت الخدمة بيانات غير صالحة. تواصل مع الدعم الفني.", kind: "parse" };
  }
  if (err instanceof ApiHttpError) {
    return mapHttpError(err, ctx);
  }
  if (isApiClientError(err)) {
    return { title: "خطأ غير متوقع", message: "حدث خطأ غير متوقع أثناء معالجة الطلب.", kind: "unknown" };
  }
  // For arbitrary thrown Errors (e.g. client-side validation BEFORE the API
  // call), surface the message only if it passes the allowlist — never just
  // because it contains Arabic.
  if (err instanceof Error && err.message && isAllowlistedUserMessage(err.message)) {
    return { title: "بيانات غير صالحة", message: err.message, kind: "validation" };
  }
  return { title: "خطأ غير معروف", message: "حدث خطأ غير معروف. حاول مرة أخرى لاحقًا.", kind: "unknown" };
}

function mapHttpError(err: ApiHttpError, ctx: UserFacingErrorContext): UserFacingError {
  const status = err.status;
  const backendMsg = err.backendMessage;

  // Allowlist of safe backend message prefixes. Only messages that match one
  // of these prefixes are surfaced to the user; everything else falls through
  // to the generic, fixed copy. This closes the "Arabic message = safe to
  // display" assumption that previously let backend exceptions leak.
  const safeBackendMsg =
      backendMsg && isAllowlistedUserMessage(backendMsg) ? backendMsg : null;

  if (status === 400) {
    return {
      title: "بيانات غير صالحة",
      message: safeBackendMsg ?? "البيانات المرسلة غير صالحة. راجع الحقول وأعد المحاولة.",
      kind: "validation",
    };
  }
  if (status === 401) {
    // Critical: produce ONE identical message for any credential rejection,
    // regardless of whether the user existed or the password was wrong. This
    // eliminates the account-enumeration side channel.
    if (ctx.isLoginAttempt) {
      return {
        title: "تعذر تسجيل الدخول",
        message: "البريد الإلكتروني أو كلمة المرور غير صحيحة.",
        kind: "invalid-credentials",
      };
    }
    return {
      title: "انتهت الجلسة",
      message: "انتهت جلستك. سجّل الدخول مرة أخرى للمتابعة.",
      kind: "session-expired",
    };
  }
  if (status === 403) {
    return {
      title: "ممنوع الوصول",
      message: safeBackendMsg ?? "لا تملك صلاحية الوصول إلى هذا المورد.",
      kind: "validation",
    };
  }
  if (status === 404) {
    return {
      title: "غير موجود",
      message: "المورد المطلوب غير موجود. ربما تم حذفه أو أن المعرف غير صحيح.",
      kind: "not-found",
    };
  }
  if (status === 409) {
    return {
      title: "تعارض في البيانات",
      message: safeBackendMsg ?? "البريد الإلكتروني أو العضوية موجودة مسبقًا.",
      kind: "conflict",
    };
  }
  if (status === 429) {
    return {
      title: "طلبات كثيرة",
      message: safeBackendMsg ?? "تم تجاوز عدد المحاولات المسموح بها. حاول لاحقًا.",
      kind: "validation",
    };
  }
  if (status === 422) {
    return {
      title: "بيانات غير قابلة للمعالجة",
      message: safeBackendMsg ?? "تعذر معالجة البيانات المرسلة. راجع الحقول وأعد المحاولة.",
      kind: "validation",
    };
  }
  if (status >= 500 && status < 600) {
    return {
      title: "خطأ في الخادم",
      message: "حدث خطأ داخلي. حاول مرة أخرى لاحقًا، وإن استمر المشكل تواصل مع الدعم الفني.",
      kind: "server",
    };
  }
  if (status >= 400 && status < 500) {
    return {
      title: "خطأ في الطلب",
      message: safeBackendMsg ?? "تعذر إكمال الطلب. راجع البيانات وأعد المحاولة.",
      kind: "validation",
    };
  }
  return { title: "خطأ غير متوقع", message: "حدث خطأ غير متوقع أثناء معالجة الطلب.", kind: "unknown" };
}

/**
 * Returns true when a backend-supplied message is considered safe to display
 * directly to end users. The check has two layers:
 *   1. Reject any string that contains operator-only infrastructure words.
 *   2. Accept only strings that both contain Arabic AND match one of the
 *      known-safe human-message prefixes. This is much stricter than the
 *      previous "any Arabic = safe" heuristic.
 */
function isAllowlistedUserMessage(message: string): boolean {
  if (!message) {
    return false;
  }
  if (!containsArabic(message)) {
    return false;
  }
  const lower = message.toLowerCase();
  for (const forbidden of FORBIDDEN_OPERATOR_TERMS) {
    if (lower.includes(forbidden)) {
      return false;
    }
  }
  for (const prefix of ALLOWLISTED_USER_MESSAGE_PREFIXES) {
    if (message.startsWith(prefix)) {
      return true;
    }
  }
  return false;
}

function containsArabic(text: string): boolean {
  return /[\u0600-\u06FF]/.test(text);
}

/** Operator-only terms that must NEVER appear in user-facing copy. */
const FORBIDDEN_OPERATOR_TERMS = [
  "backend",
  "next_public_api_base_url",
  "ngrok",
  "vercel",
  "next.js",
  "render",
  "supabase",
  "cloudflare",
  "jdbc",
  "stack trace",
  "sqlstate",
  "psql",
];

/**
 * Known-safe Arabic prefixes that the backend deliberately emits as
 * user-facing validation messages. Anything else is mapped to a generic
 * fixed copy at the call site.
 */
const ALLOWLISTED_USER_MESSAGE_PREFIXES = [
  "البريد الإلكتروني أو كلمة المرور غير صحيحة",
  "تم تجاوز عدد محاولات الدخول",
  "حساب المستخدم غير نشط",
  "تم إرسال رابط",
  "تمت إعادة تعيين كلمة المرور",
  "لا يمكن استخدام كلمة المرور القديمة",
  "كلمة المرور ضعيفة",
  "البريد الإلكتروني موجود في عدة مستأجرين",
];

export function toUserFacingMessage(err: unknown, ctx: UserFacingErrorContext = {}): string {
  return toUserFacingError(err, ctx).message;
}

export function toUserFacingTitle(err: unknown, ctx: UserFacingErrorContext = {}): string {
  return toUserFacingError(err, ctx).title;
}
