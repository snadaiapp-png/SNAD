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
    | "server"
    | "parse"
    | "serialization"
    | "unknown";
}

export function toUserFacingError(err: unknown): UserFacingError {
  if (err instanceof ApiClientCancellation) {
    return { title: "تم إلغاء الطلب", message: "تم إلغاء العملية ولم تكتمل.", kind: "cancellation" };
  }
  if (err instanceof ApiConfigurationError) {
    return {
      title: "إعداد غير مكتمل",
      message: "لم يتم ضبط عنوان الـBackend. تأكد من قيمة NEXT_PUBLIC_API_BASE_URL في بيئة التشغيل.",
      kind: "configuration",
    };
  }
  if (err instanceof ApiTimeoutError) {
    return { title: "انتهت مهلة الطلب", message: "استغرق الطلب وقتًا طويلًا. حاول مرة أخرى.", kind: "timeout" };
  }
  if (err instanceof ApiNetworkError) {
    return {
      title: "تعذر الاتصال بالخادم",
      message: "لا يمكن الوصول إلى الـBackend. تحقق من اتصال الشبكة وحالة الخادم.",
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
    return { title: "استجابة غير صالحة", message: "أرجع الخادم بيانات غير صالحة. تواصل مع الدعم الفني.", kind: "parse" };
  }
  if (err instanceof ApiHttpError) {
    return mapHttpError(err);
  }
  if (isApiClientError(err)) {
    return { title: "خطأ غير متوقع", message: "حدث خطأ غير متوقع أثناء معالجة الطلب.", kind: "unknown" };
  }
  if (err instanceof Error && err.message && containsArabic(err.message)) {
    return { title: "بيانات غير صالحة", message: err.message, kind: "validation" };
  }
  return { title: "خطأ غير معروف", message: "حدث خطأ غير معروف. حاول مرة أخرى لاحقًا.", kind: "unknown" };
}

function mapHttpError(err: ApiHttpError): UserFacingError {
  const status = err.status;
  const backendMsg = err.backendMessage;

  // If the backend sent an Arabic message, use it directly — it's already
  // a user-facing validation message in the correct language.
  if (backendMsg && containsArabic(backendMsg)) {
    if (status === 400) {
      return { title: "بيانات غير صالحة", message: backendMsg, kind: "validation" };
    }
    if (status === 401) {
      return { title: "غير مصرح", message: backendMsg, kind: "validation" };
    }
    if (status === 403) {
      return { title: "ممنوع الوصول", message: backendMsg, kind: "validation" };
    }
    if (status === 409) {
      return { title: "تعارض في البيانات", message: backendMsg, kind: "conflict" };
    }
    if (status === 429) {
      return { title: "طلبات كثيرة", message: backendMsg, kind: "validation" };
    }
  }

  if (status === 400) {
    return { title: "بيانات غير صالحة", message: "البيانات المرسلة غير صالحة. راجع الحقول وأعد المحاولة.", kind: "validation" };
  }
  if (status === 401) {
    return { title: "غير مصرح", message: "يجب تسجيل الدخول للوصول إلى هذا المورد.", kind: "validation" };
  }
  if (status === 403) {
    return { title: "ممنوع الوصول", message: "لا تملك صلاحية الوصول إلى هذا المورد.", kind: "validation" };
  }
  if (status === 404) {
    return { title: "غير موجود", message: "المورد المطلوب غير موجود. ربما تم حذفه أو أن المعرف غير صحيح.", kind: "not-found" };
  }
  if (status === 409) {
    return { title: "تعارض في البيانات", message: "البريد الإلكتروني أو العضوية موجودة مسبقًا.", kind: "conflict" };
  }
  if (status === 429) {
    return { title: "طلبات كثيرة", message: "تم تجاوز عدد الطلبات المسموح بها. حاول لاحقًا.", kind: "validation" };
  }
  if (status === 422) {
    return { title: "بيانات غير قابلة للمعالجة", message: "تعذر معالجة البيانات المرسلة. راجع الحقول وأعد المحاولة.", kind: "validation" };
  }
  if (status >= 500 && status < 600) {
    return { title: "خطأ في الخادم", message: "حدث خطأ داخلي في الخادم. حاول مرة أخرى لاحقًا.", kind: "server" };
  }
  if (status >= 400 && status < 500) {
    return { title: "خطأ في الطلب", message: "تعذر إكمال الطلب. راجع البيانات وأعد المحاولة.", kind: "validation" };
  }
  return { title: "خطأ غير متوقع", message: "حدث خطأ غير متوقع أثناء معالجة الطلب.", kind: "unknown" };
}

function containsArabic(text: string): boolean {
  return /[\u0600-\u06FF]/.test(text);
}

export function toUserFacingMessage(err: unknown): string {
  return toUserFacingError(err).message;
}

export function toUserFacingTitle(err: unknown): string {
  return toUserFacingError(err).title;
}
