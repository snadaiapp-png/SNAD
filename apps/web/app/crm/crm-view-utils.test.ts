import { describe, it, expect } from "vitest";
import { toIsoDateTime } from "./crm-view-utils";

describe("toIsoDateTime", () => {
  it("converts YYYY-MM-DD to full ISO-8601 date-time at UTC midnight", () => {
    expect(toIsoDateTime("2026-08-10")).toBe("2026-08-10T00:00:00.000Z");
  });

  it("converts January 1st correctly", () => {
    expect(toIsoDateTime("2026-01-01")).toBe("2026-01-01T00:00:00.000Z");
  });

  it("converts December 31st correctly", () => {
    expect(toIsoDateTime("2026-12-31")).toBe("2026-12-31T00:00:00.000Z");
  });

  it("passes through a date-time string unchanged", () => {
    expect(toIsoDateTime("2026-08-10T14:30:00Z")).toBe("2026-08-10T14:30:00Z");
  });

  it("returns undefined when input is undefined", () => {
    expect(toIsoDateTime(undefined)).toBeUndefined();
  });

  it("returns undefined when input is empty string", () => {
    expect(toIsoDateTime("")).toBeUndefined();
  });

  it("returns undefined when input is null-like empty string", () => {
    expect(toIsoDateTime("   ")).toBeUndefined();
  });

  it("preserves an ISO date-time with offset", () => {
    expect(toIsoDateTime("2026-08-10T10:00:00+03:00")).toBe("2026-08-10T10:00:00+03:00");
  });
});
