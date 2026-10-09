import { describe, expect, it } from "vitest";
import { assertVercelProductionRef } from "./vercel-release-guard";

describe("SNAD_RELEASE_GUARD production source attestation", () => {
  it("allows only main for a production build", () => {
    expect(() => assertVercelProductionRef("production", "main")).not.toThrow();
    expect(() => assertVercelProductionRef("prod", "main")).not.toThrow();
  });
  it("rejects production builds from certification or feature branches", () => {
    expect(() => assertVercelProductionRef("production", "cert/hrm-g4-t11-full-certification"))
      .toThrow("SNAD_RELEASE_GUARD");
    expect(() => assertVercelProductionRef("production", "feature/test"))
      .toThrow("non-main Git ref");
  });
  it("fails closed when production Git ref is missing or whitespace", () => {
    expect(() => assertVercelProductionRef("production", undefined)).toThrow("<missing>");
    expect(() => assertVercelProductionRef("production", " ")).toThrow("<missing>");
  });
  it("permits a non-main Preview build without turning it into Production", () => {
    expect(() => assertVercelProductionRef("preview", "cert/hrm-g4-t11-full-certification"))
      .not.toThrow();
    expect(() => assertVercelProductionRef("development", "feature/test")).not.toThrow();
  });
});
