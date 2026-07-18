import { describe, expect, it } from "vitest";
import { ApiHttpError, ApiNetworkError, ApiTimeoutError } from "./errors";
import { toUserFacingError } from "./user-facing-errors";
import type { ApiErrorDetails } from "./types";

function httpError(
  status: number,
  path: string,
  message: string | null = null,
  requestId: string | null = null,
): ApiHttpError {
  const details: ApiErrorDetails = {
    status,
    error: null,
    message,
    path,
    requestId,
    body: message ? { message, path } : { path },
  };
  return new ApiHttpError(`HTTP ${status}: POST ${path}`, details);
}

describe("toUserFacingError", () => {
  it("reports invalid credentials only after a definitive login 401", () => {
    const result = toUserFacingError(httpError(
      401,
      "/api/v1/auth/login",
      "بيانات اعتماد داخلية لا ينبغي عرضها حرفيًا",
    ));

    expect(result.title).toBe("تعذر تسجيل الدخول");
    expect(result.message).toBe("البريد الإلكتروني أو كلمة المرور غير صحيحة.");
  });

  it("treats a non-login 401 as a missing or expired session", () => {
    const result = toUserFacingError(httpError(401, "/api/v1/auth/me"));

    expect(result.title).toBe("يلزم تسجيل الدخول");
    expect(result.message).toContain("الجلسة");
    expect(result.message).not.toContain("كلمة المرور غير صحيحة");
  });

  it("identifies a BFF 502 during login as backend connectivity failure", () => {
    const result = toUserFacingError(httpError(
      502,
      "https://snad-app.vercel.app/api/platform/api/v1/auth/login",
      "Backend unavailable",
      "req-502",
    ));

    expect(result.title).toBe("تعذر الوصول إلى الخادم الخلفي");
    expect(result.message).toContain("لم تتمكن من الاتصال");
    expect(result.message).toContain("req-502");
    expect(result.message).toContain("لم يتم التحقق من بيانات الدخول");
  });

  it("identifies a BFF 504 during login as an upstream authentication timeout", () => {
    const result = toUserFacingError(httpError(
      504,
      "https://snad-app.vercel.app/api/platform/api/v1/auth/login",
      "Backend timeout",
      "req-504",
    ));

    expect(result.title).toBe("الخادم الخلفي لم يستجب");
    expect(result.kind).toBe("timeout");
    expect(result.message).toContain("خدمة المصادقة الخلفية");
    expect(result.message).toContain("req-504");
  });

  it("does not misreport a client-side login timeout as invalid credentials", () => {
    const result = toUserFacingError(new ApiTimeoutError(
      "Request to POST /api/v1/auth/login timed out after 30000ms",
      30_000,
    ));

    expect(result.title).toBe("لم تكتمل محاولة الدخول");
    expect(result.message).toContain("لم يتم التحقق من بيانات الدخول");
    expect(result.message).not.toContain("غير صحيحة");
  });

  it("does not misreport a login network failure as invalid credentials", () => {
    const result = toUserFacingError(new ApiNetworkError(
      "Network error while requesting POST /api/v1/auth/login",
    ));

    expect(result.title).toBe("تعذر الوصول إلى بوابة الدخول");
    expect(result.kind).toBe("network");
    expect(result.message).toContain("لم يتم التحقق");
  });

  it("passes through safe Arabic validation messages but not server details", () => {
    const validation = toUserFacingError(httpError(422, "/api/v1/customers", "رقم الجوال غير صالح"));
    const server = toUserFacingError(httpError(500, "/api/v1/customers", "فشل SQL في قاعدة البيانات"));

    expect(validation.message).toBe("رقم الجوال غير صالح");
    expect(server.message).not.toContain("SQL");
  });
});
