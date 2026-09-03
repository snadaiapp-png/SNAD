import { describe, expect, it } from "vitest";
import { resolvePostLoginDestination, safeReturnUrl } from "./destination";

describe("post-login destination security", () => {
  const available = ["/workspace", "/crm", "/control-plane"];

  it("accepts an authorized internal nested route", () => {
    expect(safeReturnUrl("/crm/leads?view=open", available)).toBe("/crm/leads?view=open");
  });

  it.each([
    "https://evil.example/phish",
    "//evil.example/phish",
    "javascript:alert(1)",
    "data:text/html,boom",
    "/\\evil.example",
  ])("rejects unsafe returnUrl %s", (candidate) => {
    expect(safeReturnUrl(candidate, available)).toBeNull();
  });

  it("rejects a valid internal route not granted by the bootstrap", () => {
    expect(safeReturnUrl("/control-plane", ["/workspace", "/crm"])).toBeNull();
  });

  it("prefers safe returnUrl, then default destination, then workspace", () => {
    expect(resolvePostLoginDestination({
      returnUrl: "/crm/leads",
      defaultDestination: "/control-plane",
      availableDestinations: available,
    })).toBe("/crm/leads");

    expect(resolvePostLoginDestination({
      returnUrl: "https://evil.example",
      defaultDestination: "/control-plane",
      availableDestinations: available,
    })).toBe("/control-plane");

    expect(resolvePostLoginDestination({ availableDestinations: ["/workspace"] })).toBe("/workspace");
  });
});
