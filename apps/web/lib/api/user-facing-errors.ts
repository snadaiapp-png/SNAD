/**
 * User-facing error mapper — converts API client errors into safe,
 * Arabic-language messages suitable for display to end users.
 *
 * Rules:
 * - Never expose stack traces, internal URLs, request headers, raw bodies,
 *   tokens, cookies, authorization data, or database details.
 * - Describe only the failure that was actually observed.
 * - Never report invalid credentials when the request did not reach a
 *   definitive HTTP 401 response from the login endpoint.
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

const LOGIN_PATH = "/api/v1/auth/login";

export function toUserFacingError(err: unknown): UserFacingError {
  const loginRequest = isLoginRequest(err);

  if (err instanceof ApiClientCancellation) {
    return { title: "تم إلغاء الطلب", message: "تم إلغاء العملية قبل اكتمالها.", kind: "cancellation" };
  }
  if (err instanceof ApiConfigurationError) {
    return {
      title: "خدمة الاتصال غير مهيأة",
      message: "تعذر بدء الاتصال بخدمات النظام بسبب إعداد تشغيل غير مكتمل. تواصل مع مسؤول النظام.",
      kind: "configuration",
    };
  }
  if (err instanceof ApiTimeoutError) {
    return loginRequest
      ? {
          title: "لم تكتمل محاولة الدخول",
          message: "لم تصل استجابة من بوابة النظام ضمن المهلة المحددة، لذلك لم يتم التحقق من بيانات الدخول. أعد المحاولة بعد التأكد من توفر الخدمة.",
          kind: "timeout",
        }
      : {
          title: "انتهت مهلة الاتصال",
          message: "لم تصل استجابة من النظام ضمن المهلة المحددة. أعد المحاولة بعد التأكد من توفر الخدمة.",
          kind: "timeout",
        };
  }
  if (err instanceof ApiNetworkError) {
    return loginRequest
      ? {
          title: "تعذر الوصول إلى بوابة الدخول",
          message: "لم يتم إنشاء اتصال بخدمة الدخول، لذلك لم يتم التحقق من البريد الإلكتروني أو كلمة المرور. تحقق من الشبكة وحالة الخدمة ثم أعد المحاولة.",
          kind: "network",
        }
      : {
          title: "تعذر الاتصال بالنظام",
          message: "لم يتم إنشاء اتصال بخدمات النظام. تحقق من الشبكة وحالة الخدمة ثم أعد المحاولة.",
          kind: "network",
        };
  }
  if (err instanceof ApiRequestSerializationError) {
    return {
      title: "تعذر تجهيز الطلب",
      message: "تعذر تجهيز البيانات للإرسال. راجع الحقول وأعد المحاولة.",
      kind: "serialization",
    };
  }
  if (err instanceof ApiResponseParseError) {
    return {
      title: "استجابة غير صالحة",
      message: "وصلت استجابة غير مفهومة من الخدمة. أعد المحاولة، وإن استمرت المشكلة فتواصل مع الدعم الفني.",
      kind: "parse",
    };
  }
  if (err instanceof ApiHttpError) {
    return mapHttpError(err, loginRequest);
  }
  if (isApiClientError(err)) {
    return { title: "تعذر إكمال الطلب", message: "حدث خطأ أثناء معالجة الطلب ولم تكتمل العملية.", kind: "unknown" };
  }
  if (err instanceof Error && isSafeUserMessage(err.message)) {
    return { title: "بيانات غير صالحة", message: err.message, kind: "validation" };
  }
  return { title: "خطأ غير معروف", message: "حدث خطأ غير معروف. حاول مرة أخرى لاحقًا.", kind: "unknown" };
}

function mapHttpError(err: ApiHttpError, loginRequest: boolean): UserFacingError {
  const status = err.status;
  const backendMsg = err.backendMessage;

  if (loginRequest) {
    if (status === 400 || status === 422) {
      return {
        title: "بيانات الدخول غير مكتملة",
        message: safeValidationMessage(backendMsg, "راجع البريد الإلكتروني وكلمة المرور ثم أعد المحاولة."),
        kind: "validation",
      };
    }
    if (status === 401) {
      return {
        title: "تعذر تسجيل الدخول",
        message: "البريد الإلكتروني أو كلمة المرور غير صحيحة.",
        kind: "validation",
      };
    }
    if (status === 403) {
      return {
        title: "تم رفض الدخول",
        message: "بيانات الاعتماد صحيحة أو وصلت إلى الخدمة، لكن الحساب غير مخول بالدخول أو غير نشط. تواصل مع مسؤول النظام.",
        kind: "validation",
      };
    }
    if (status === 404) {
      return {
        title: "خدمة الدخول غير متاحة",
        message: withReference("لم يتم العثور على مسار تسجيل الدخول في النسخة المنشورة. يلزم مراجعة إعداد الربط أو إصدار النظام.", err.requestId),
        kind: "not-found",
      };
    }
    if (status === 409) {
      return {
        title: "يلزم تحديد مساحة العمل",
        message: safeValidationMessage(backendMsg, "البريد الإلكتروني مرتبط بأكثر من مساحة عمل. اختر مساحة العمل للمتابعة."),
        kind: "conflict",
      };
    }
    if (status === 423) {
      return {
        title: "الحساب مقفل",
        message: "تم إيقاف محاولات الدخول إلى هذا الحساب مؤقتًا. تواصل مع مسؤول النظام أو حاول لاحقًا.",
        kind: "validation",
      };
    }
    if (status === 429) {
      return {
        title: "محاولات دخول كثيرة",
        message: "تم تجاوز عدد محاولات الدخول المسموح بها. انتظر قليلًا ثم أعد المحاولة.",
        kind: "validation",
      };
    }
    if (status === 502) {
      return {
        title: "تعذر الوصول إلى الخادم الخلفي",
        message: withReference("بوابة النظام تعمل، لكنها لم تتمكن من الاتصال بخدمة المصادقة الخلفية. لم يتم التحقق من بيانات الدخول.", err.requestId),
        kind: "network",
      };
    }
    if (status === 503) {
      return {
        title: "خدمة الدخول غير متاحة",
        message: withReference("خدمة الربط أو المصادقة غير متاحة حاليًا. لم يتم التحقق من بيانات الدخول.", err.requestId),
        kind: "server",
      };
    }
    if (status === 504) {
      return {
        title: "الخادم الخلفي لم يستجب",
        message: withReference("وصل طلب الدخول إلى بوابة النظام، لكن خدمة المصادقة الخلفية لم تستجب ضمن المهلة. لم يتم التحقق من بيانات الدخول.", err.requestId),
        kind: "timeout",
      };
    }
    if (status >= 500 && status < 600) {
      return {
        title: "خطأ في خدمة الدخول",
        message: withReference("وصل الطلب إلى النظام، لكن خدمة الدخول فشلت داخليًا. لم يتم التحقق من بيانات الدخول.", err.requestId),
        kind: "server",
      };
    }
  }

  // Only pass through backend text for validation-style responses. Authentication,
  // authorization, and server failures use controlled messages to avoid leaking
  // implementation details or misclassifying the failure.
  if ((status === 400 || status === 409 || status === 422) && isSafeUserMessage(backendMsg)) {
    if (status === 409) return { title: "تعارض في البيانات", message: backendMsg, kind: "conflict" };
    return { title: "بيانات غير صالحة", message: backendMsg, kind: "validation" };
  }

  if (status === 400) {
    return { title: "بيانات غير صالحة", message: "البيانات المرسلة غير صالحة. راجع الحقول وأعد المحاولة.", kind: "validation" };
  }
  if (status === 401) {
    return { title: "يلزم تسجيل الدخول", message: "انتهت الجلسة أو لا توجد جلسة صالحة. سجّل الدخول من جديد.", kind: "validation" };
  }
  if (status === 403) {
    return { title: "الوصول مرفوض", message: "لا تملك الصلاحية المطلوبة لتنفيذ هذه العملية.", kind: "validation" };
  }
  if (status === 404) {
    return { title: "غير موجود", message: "المورد المطلوب غير موجود أو لم يعد متاحًا.", kind: "not-found" };
  }
  if (status === 408 || status === 504) {
    return { title: "انتهت مهلة الخدمة", message: withReference("لم تستجب إحدى خدمات النظام ضمن المهلة المحددة.", err.requestId), kind: "timeout" };
  }
  if (status === 409) {
    return { title: "تعارض في البيانات", message: "تتعارض العملية مع بيانات موجودة حاليًا.", kind: "conflict" };
  }
  if (status === 422) {
    return { title: "تعذر معالجة البيانات", message: "تعذر معالجة البيانات المرسلة. راجع الحقول وأعد المحاولة.", kind: "validation" };
  }
  if (status === 423) {
    return { title: "المورد مقفل", message: "لا يمكن تنفيذ العملية لأن المورد مقفل حاليًا.", kind: "conflict" };
  }
  if (status === 429) {
    return { title: "طلبات كثيرة", message: "تم تجاوز عدد الطلبات المسموح بها. انتظر قليلًا ثم أعد المحاولة.", kind: "validation" };
  }
  if (status === 502) {
    return { title: "تعذر الوصول إلى خدمة داخلية", message: withReference("بوابة النظام لم تتمكن من الاتصال بإحدى الخدمات الخلفية.", err.requestId), kind: "network" };
  }
  if (status === 503) {
    return { title: "الخدمة غير متاحة", message: withReference("الخدمة المطلوبة غير متاحة حاليًا. حاول مرة أخرى لاحقًا.", err.requestId), kind: "server" };
  }
  if (status >= 500 && status < 600) {
    return { title: "خطأ في الخادم", message: withReference("حدث خطأ داخلي ولم تكتمل العملية. حاول مرة أخرى لاحقًا.", err.requestId), kind: "server" };
  }
  if (status >= 400 && status < 500) {
    return { title: "تعذر إكمال الطلب", message: "رفض النظام الطلب. راجع البيانات والصلاحيات ثم أعد المحاولة.", kind: "validation" };
  }
  return { title: "خطأ غير متوقع", message: "حدث خطأ غير متوقع أثناء معالجة الطلب.", kind: "unknown" };
}

function isLoginRequest(err: unknown): boolean {
  if (err instanceof ApiHttpError) {
    return Boolean(err.details.path?.includes(LOGIN_PATH));
  }
  if (err instanceof Error) {
    return err.message.includes(LOGIN_PATH);
  }
  return false;
}

function safeValidationMessage(message: string | null, fallback: string): string {
  return isSafeUserMessage(message) ? message : fallback;
}

function isSafeUserMessage(message: unknown): message is string {
  if (typeof message !== "string") return false;
  const value = message.trim();
  if (!value || value.length > 240 || !containsArabic(value)) return false;
  if (/https?:\/\/|jdbc:|sql|exception|stack|trace|authorization|bearer|cookie/i.test(value)) return false;
  return true;
}

function withReference(message: string, requestId: string | null): string {
  if (!requestId || !/^[A-Za-z0-9._:-]{1,100}$/.test(requestId)) return message;
  return `${message} رقم المرجع: ${requestId}`;
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
