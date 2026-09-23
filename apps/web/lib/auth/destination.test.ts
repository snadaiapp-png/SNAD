import { describe, expect, it } from "vitest";
import { resolvePostLoginDestination, safeReturnUrl } from "./destination";

describe("post-login destination security", () => {
  const available = ["/workspace", "/crm", "/control-plane"];

  it("accepts an authorized internal nested route", () => {
    expect(safeReturnUrl("/crm/leads?view=open", available)).toBe("/crm/leads?view=open");
  });

  it("preserves an authorized nested executive route", () => {
    expect(safeReturnUrl("/executive/tenants?tab=active", ["/workspace", "/executive"]))
      .toBe("/executive/tenants?tab=active");
  });

  it("rejects a nested executive route when executive access is absent", () => {
    expect(safeReturnUrl("/executive/tenants", ["/workspace", "/crm"]))
      .toBeNull();
  });

  it.each([
    "https://evil.example/phish",
    "//evil.example/phish",
    "javascript:alert(1)",
    "data:text/html,boom",
    "/\\evil.example",
    "%2F%2Fevil.example/phish",
    "/%5Cevil.example/phish",
  ])("rejects unsafe returnUrl %s", (candidate) => {
    expect(safeReturnUrl(candidate, available)).toBeNull();
  });

  it("rejects a valid internal route not granted by the bootstrap", () => {
    expect(safeReturnUrl("/control-plane", ["/workspace", "/crm"])).toBeNull();
  });

  it("preserves an explicitly requested authorized returnUrl", () => {
    expect(resolvePostLoginDestination({
      returnUrl: "/crm/leads",
      defaultDestination: "/control-plane",
      availableDestinations: available,
    })).toBe("/crm/leads");
  });

  it("preserves nested workflow routes after authentication", () => {
    expect(resolvePostLoginDestination({
      returnUrl: "/workflow/definitions/abc",
      availableDestinations: ["/workflow"],
    })).toBe("/workflow/definitions/abc");
  });

  it("uses workspace as the default landing page after login", () => {
    expect(resolvePostLoginDestination({
      returnUrl: "https://evil.example",
      defaultDestination: "/control-plane",
      availableDestinations: available,
    })).toBe("/workspace");

    expect(resolvePostLoginDestination({
      defaultDestination: "/crm",
      availableDestinations: ["/crm", "/control-plane"],
    })).toBe("/workspace");
  });
});
