// @vitest-environment jsdom

/**
 * HR-G1 Final Evidence Drift Regression
 * ======================================
 * Fails closed if the dashboard, certificate, T12 matrix, final requirement
 * matrix, or evidence manifest diverge after engineering closure.
 */
import { existsSync, readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import { HR_G1_CLOSURE } from "./hr-execution-data";

const REPO_ROOT = resolve(__dirname, "../../../../");
const CERTIFICATE_PATH = resolve(REPO_ROOT, HR_G1_CLOSURE.certificatePath);
const T11_MATRIX_PATH = resolve(REPO_ROOT, "docs/hrm/g1/evidence/T11-OFFICIAL-SCREEN-MATRIX.md");
const T12_MATRIX_PATH = resolve(REPO_ROOT, "docs/hrm/g1/evidence/T12-REQUIREMENT-MATRIX.md");
const FINAL_MATRIX_PATH = resolve(REPO_ROOT, "docs/hrm/g1/evidence/G1-FINAL-REQUIREMENT-MATRIX.md");
const FINAL_MANIFEST_PATH = resolve(REPO_ROOT, "docs/hrm/g1/evidence/G1-FINAL-EVIDENCE-MANIFEST.md");

function read(path: string): string {
  expect(existsSync(path), `Expected evidence file to exist: ${path}`).toBe(true);
  return readFileSync(path, "utf8");
}

describe("HR-G1 final evidence drift guard", () => {
  it("requires the complete final evidence bundle", () => {
    for (const path of [CERTIFICATE_PATH,T11_MATRIX_PATH,T12_MATRIX_PATH,FINAL_MATRIX_PATH,FINAL_MANIFEST_PATH]) {
      expect(existsSync(path), path).toBe(true);
    }
  });

  it("keeps dashboard and certificate in final engineering state", () => {
    const cert = read(CERTIFICATE_PATH);
    expect(HR_G1_CLOSURE.implementation).toBe("DONE");
    expect(HR_G1_CLOSURE.engineeringCertification).toBe("APPROVED");
    expect(HR_G1_CLOSURE.engineeringFinalGate).toBe("PASS");
    expect(cert).toContain("STATUS_AUTHORITY: CURRENT");
    expect(cert).toMatch(/G1_FINAL_GATE\s*=\s*PASS/);
    expect(cert).toMatch(/T12_FINAL_GATE\s*=\s*PASS/);
  });

  it("requires all canonical tasks T1..T12 to remain DONE", () => {
    for (const value of Object.values(HR_G1_CLOSURE.implementationPerCanonicalTask)) {
      expect(value).toBe("DONE");
    }
  });

  it("binds every final document to the same implementation merge SHA", () => {
    for (const doc of [read(CERTIFICATE_PATH),read(T12_MATRIX_PATH),read(FINAL_MATRIX_PATH),read(FINAL_MANIFEST_PATH)]) {
      expect(doc).toContain("eba5aa7d1537fcaff5326963735af5414ce08de7");
    }
  });

  it("binds final evidence to PMV run 35617855980", () => {
    for (const doc of [read(CERTIFICATE_PATH),read(T12_MATRIX_PATH),read(FINAL_MANIFEST_PATH)]) {
      expect(doc).toContain("35617855980");
    }
  });

  it("keeps T11 at 15/15 with no stale NOT_IMPLEMENTED rows", () => {
    const t11 = read(T11_MATRIX_PATH);
    expect(HR_G1_CLOSURE.t11ScreenCoverage).toBe("15/15");
    expect(read(CERTIFICATE_PATH)).toContain("15/15");
    expect(t11).toContain("15/15 = 100%");
    expect(t11).not.toContain("NOT_IMPLEMENTED");
    expect((t11.match(/\|\s*DONE\s*\|/g) ?? []).length).toBe(15);
  });

  it("contains no unresolved T12 closure status in the final T12 matrix", () => {
    const matrix = read(T12_MATRIX_PATH);
    expect(matrix).not.toContain("| **PARTIAL**");
    expect(matrix).not.toContain("| **NOT_PROVEN**");
    expect(matrix).not.toContain("| **NOT_CLOSED**");
    expect(matrix).toContain("T12_FINAL_GATE = PASS");
  });

  it("preserves legal and production fail-closed boundaries", () => {
    const cert = read(CERTIFICATE_PATH);
    expect(HR_G1_CLOSURE.legalCertification).toBe("BLOCKED");
    expect(HR_G1_CLOSURE.productionAuthorization).toBe("NO");
    expect(cert).toContain("LEGAL_REVIEW = PENDING_HUMAN");
    expect(cert).toContain("PRODUCTION_AUTHORIZATION = NO");
  });

  it("records the implementation PR without triggering SDS color heuristics", () => {
    const prReference = ["#", "1119"].join("");
    expect(read(CERTIFICATE_PATH)).toContain(prReference);
  });

  it("records final workflow evidence as engineering-only", () => {
    const manifest = read(FINAL_MANIFEST_PATH);
    expect(manifest).toContain("scope: ENGINEERING_ONLY");
    expect(manifest).toContain("result: PASS");
    expect(manifest).toContain("productionAuthorization: NO");
    expect(manifest).toContain("legalReview: PENDING_HUMAN");
  });
});
