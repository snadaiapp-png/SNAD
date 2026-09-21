/**
 * HR-G1 Final Engineering Closure State Regression Test
 * -----------------------------------------------------
 * Binds the execution dashboard to the governed HRM-G1 engineering closure.
 *
 * Engineering closure evidence:
 *   - implementation PR 1119 exact head: 87cdfba3522f8b576236c901fbb98cfe77d25c13
 *   - protected squash merge on main: eba5aa7d1537fcaff5326963735af5414ce08de7
 *   - Post-Merge Main Verification run 1092 / 35617855980: SUCCESS
 *   - CI run 3951 / 35617855855: SUCCESS
 *
 * Claim discipline:
 *   - engineering completion does not imply legal certification;
 *   - engineering completion does not imply production authorization;
 *   - G2 remains NOT_AUTHORIZED for implementation.
 */
import { existsSync, readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import { HR_G1_CLOSURE } from "./hr-execution-data";

const REPO_ROOT = resolve(__dirname, "../../../../");
const CERTIFICATE_PATH = resolve(REPO_ROOT, HR_G1_CLOSURE.certificatePath);

function readCertificate(): string {
  expect(existsSync(CERTIFICATE_PATH)).toBe(true);
  return readFileSync(CERTIFICATE_PATH, "utf8");
}

describe("HR-G1 final engineering closure", () => {
  it("is bound to the final engineering closure certificate", () => {
    expect(HR_G1_CLOSURE.certificatePath).toBe(
      "docs/hrm/g1/evidence/HRM-G1-ENGINEERING-CLOSURE.md",
    );
  });

  it("declares G1 implementation DONE", () => {
    expect(HR_G1_CLOSURE.implementation).toBe("DONE");
  });

  it("declares all canonical tasks T1..T12 DONE", () => {
    const m = HR_G1_CLOSURE.implementationPerCanonicalTask;
    for (const key of ["T1","T2","T3","T4","T5","T6","T7","T8","T9","T10","T11","T12"] as const) {
      expect(m[key], key).toBe("DONE");
    }
  });

  it("binds closure to the exact implementation merge and PMV run", () => {
    expect(HR_G1_CLOSURE.closureEvidenceMainSha).toBe(
      "eba5aa7d1537fcaff5326963735af5414ce08de7",
    );
    expect(HR_G1_CLOSURE.preMergeClosureHeadSha).toBe(
      "87cdfba3522f8b576236c901fbb98cfe77d25c13",
    );
    expect(HR_G1_CLOSURE.postMergeVerificationRunId).toBe(35617855980);
  });

  it("declares the engineering final gate PASS", () => {
    expect(HR_G1_CLOSURE.engineeringFinalGate).toBe("PASS");
    expect(HR_G1_CLOSURE.engineeringCertification).toBe("APPROVED");
    const cert = readCertificate();
    expect(cert).toMatch(/G1_FINAL_GATE\s*=\s*PASS/);
    expect(cert).toMatch(/T12_FINAL_GATE\s*=\s*PASS/);
    expect(cert).toContain("STATUS_AUTHORITY: CURRENT");
  });

  it("keeps T11 coverage at 15/15", () => {
    expect(HR_G1_CLOSURE.t11ScreenCoverage).toBe("15/15");
  });

  it("does not inflate engineering closure into legal certification", () => {
    expect(HR_G1_CLOSURE.legalCertification).toBe("BLOCKED");
    expect(HR_G1_CLOSURE.saCountryPack).toBe("DRAFT");
    const cert = readCertificate();
    expect(cert).toContain("LEGAL_REVIEW = PENDING_HUMAN");
    expect(cert).toContain("SA_COUNTRY_PACK = DRAFT");
  });

  it("does not fabricate production authorization", () => {
    expect(HR_G1_CLOSURE.productionAuthorization).toBe("NO");
    const cert = readCertificate();
    expect(cert).toContain("PRODUCTION_AUTHORIZATION = NO");
    expect(cert).toContain("PRODUCTION_READY = NOT_CLAIMED");
    expect(cert).toContain("PRODUCTION_CERTIFIED = NOT_CLAIMED");
  });

  it("preserves canonical historical provenance", () => {
    expect(HR_G1_CLOSURE.baselineT10Sha).toBe(
      "b8af9346b21b8f8ac59d348233ef829513904fe4",
    );
    expect(HR_G1_CLOSURE.historicalRecord).toContain("PR-998");
    expect(HR_G1_CLOSURE.historicalRecord).toContain("PR-1119");
    expect(HR_G1_CLOSURE.historicalRecord).toContain("PMV run 1092");
  });
});
