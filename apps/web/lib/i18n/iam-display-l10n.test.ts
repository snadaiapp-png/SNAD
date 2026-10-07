import { describe, expect, it } from "vitest";
import {
  capabilityDisplayName,
  effectDisplayName,
  roleDisplayName,
  scopeDisplayName,
  sourceDisplayName,
  statusDisplayName,
} from "./iam-display-l10n";

describe("IAM display localization", () => {
  it("localizes canonical roles in Arabic and English", () => {
    expect(roleDisplayName("TENANT_ADMIN", "Tenant Admin", "ar")).toBe("مسؤول المستأجر");
    expect(roleDisplayName("TENANT_ADMIN", "Tenant Admin", "en")).toBe("Tenant Administrator");
    expect(roleDisplayName("HR_MANAGER", "HR Manager", "ar")).toBe("مدير الموارد البشرية");
  });

  it("localizes canonical IAM capabilities without changing their codes", () => {
    expect(capabilityDisplayName("USER.READ", "Read users", "ar")).toBe("عرض المستخدمين");
    expect(capabilityDisplayName("USER.READ", "Read users", "en")).toBe("Read users");
    expect(capabilityDisplayName("USER.GRANT_ROLE", null, "ar")).toBe("إسناد الأدوار للمستخدمين");
  });

  it("provides a deterministic bilingual fallback for future module capabilities", () => {
    expect(capabilityDisplayName("WORKFLOW.APPROVE", null, "ar")).toBe("سير العمل اعتماد");
    expect(capabilityDisplayName("WORKFLOW.APPROVE", null, "en")).toBe("Workflow Approve");
    expect(capabilityDisplayName("FUTURE_MODULE.READ", null, "en")).toBe("Future Module Read");
  });

  it("localizes status, scope, effect, and source vocabulary", () => {
    expect(statusDisplayName("ACTIVE", "ar")).toBe("نشط");
    expect(statusDisplayName("ARCHIVED", "en")).toBe("Archived");
    expect(scopeDisplayName("ORGANIZATION", "ar")).toBe("المؤسسة");
    expect(effectDisplayName("DENY", "ar")).toBe("منع");
    expect(sourceDisplayName("BREAK_GLASS", "ar")).toBe("وصول طارئ");
  });
});
