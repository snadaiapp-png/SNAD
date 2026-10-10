import { ApiInputValidationError, type ApiInputField } from "@/lib/api/errors";
import { requireValidDisplayName, requireValidEmail } from "@/lib/api/validation";

export interface UserCreationDraft {
  email: string;
  username?: string | null;
  displayName?: string | null;
  mobileNumber?: string | null;
  mobileRegion?: string | null;
  initialCredential?: string | null;
}

export interface NormalizedUserCreationInput {
  email: string;
  username: string | null;
  displayName: string | null;
  mobileNumber: string | null;
  mobileRegion: string | null;
  initialCredential?: string;
}

function reclassify<T>(field: ApiInputField, operation: () => T): T {
  try {
    return operation();
  } catch (error) {
    if (error instanceof ApiInputValidationError) throw error;
    if (error instanceof Error) {
      throw new ApiInputValidationError(field, error.message, error);
    }
    throw new ApiInputValidationError(field, "القيمة المدخلة غير صالحة", error);
  }
}

export function normalizeUsernameForUser(value?: string | null): string | null {
  if (value == null) return null;
  const normalized = value.trim().toLowerCase();
  if (!normalized) return null;
  if (normalized.length < 3 || normalized.length > 100) {
    throw new ApiInputValidationError("username", "اسم المستخدم يجب أن يكون بين 3 و100 حرف");
  }
  if (!/^[\p{L}\p{N}][\p{L}\p{N}._-]{2,99}$/u.test(normalized)) {
    throw new ApiInputValidationError(
      "username",
      "اسم المستخدم يجب أن يبدأ بحرف أو رقم ويحتوي فقط على الحروف والأرقام والنقطة والشرطة والشرطة السفلية",
    );
  }
  return normalized;
}

export function normalizeMobileRegionForUser(value?: string | null): string | null {
  const normalized = value?.trim().toUpperCase() ?? "";
  if (!normalized) return null;
  if (!/^[A-Z]{2}$/.test(normalized)) {
    throw new ApiInputValidationError(
      "mobileRegion",
      "رمز الدولة يجب أن يتكون من حرفين وفق ISO، مثل SA",
    );
  }
  return normalized;
}

export function normalizeMobileNumberForUser(
  value?: string | null,
  mobileRegion?: string | null,
): string | null {
  const normalized = value?.trim().replace(/[\s()-]/g, "") ?? "";
  if (!normalized) return null;

  if (/^\+[1-9][0-9]{7,14}$/.test(normalized)) {
    return normalized;
  }

  if (mobileRegion === "SA" && /^05[0-9]{8}$/.test(normalized)) {
    return `+966${normalized.slice(1)}`;
  }

  if (/^05[0-9]{8}$/.test(normalized) && !mobileRegion) {
    throw new ApiInputValidationError(
      "mobileRegion",
      "اختر رمز الدولة SA عند استخدام رقم جوال سعودي محلي يبدأ بـ 05",
    );
  }

  throw new ApiInputValidationError(
    "mobileNumber",
    mobileRegion === "SA"
      ? "أدخل رقم الجوال بصيغة 05XXXXXXXX أو +9665XXXXXXXX"
      : "أدخل رقم الجوال بالصيغة الدولية E.164، مثل +9665XXXXXXXX",
  );
}

export function normalizeInitialCredentialForUser(value?: string | null): string | undefined {
  if (value == null || !value.trim()) return undefined;
  if (value.length < 8 || value.length > 256) {
    throw new ApiInputValidationError(
      "initialCredential",
      "كلمة المرور المؤقتة يجب أن تكون بين 8 و256 حرفًا",
    );
  }
  return value;
}

export function normalizeUserCreationInput(input: UserCreationDraft): NormalizedUserCreationInput {
  const email = reclassify("email", () => requireValidEmail(input.email));
  const displayName = reclassify("displayName", () =>
    requireValidDisplayName(input.displayName ?? null),
  );
  const username = normalizeUsernameForUser(input.username);
  const mobileRegion = normalizeMobileRegionForUser(input.mobileRegion);
  const mobileNumber = normalizeMobileNumberForUser(input.mobileNumber, mobileRegion);
  const initialCredential = normalizeInitialCredentialForUser(input.initialCredential);

  return {
    email,
    username,
    displayName,
    mobileNumber,
    mobileRegion,
    ...(initialCredential !== undefined ? { initialCredential } : {}),
  };
}
