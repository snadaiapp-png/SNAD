import { describe, expect, it } from "vitest";

import { buildUrl } from "./config";

describe("same-origin BFF URL composition regression", () => {
  it("does not duplicate /api/platform when a typed client already supplies the BFF-prefixed path", () => {
    expect(buildUrl("/api/platform", "/api/platform/api/v2/hr/people")).toBe(
      "/api/platform/api/v2/hr/people",
    );
  });

  it("still prefixes backend-relative API paths exactly once", () => {
    expect(buildUrl("/api/platform", "/api/v2/hr/people")).toBe(
      "/api/platform/api/v2/hr/people",
    );
  });
});
