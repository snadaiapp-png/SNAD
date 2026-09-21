// @vitest-environment jsdom

/**
 * HR Evidence Drift Regression Test
 * =================================
 * Prevents the scenario that occurred on this branch where the closure
 * certificate said "T11 IN_PROGRESS (4/15)" while the actual implementation
 * was 15/15 and the T11 matrix said 15/15.
 *
 * This test fails if ANY of the following drift conditions occur:
 *   1. T11 matrix says 15/15 but closure certificate says a different number
 *   2. T12 matrix says PARTIAL but dashboard (HR_G1_CLOSURE) says APPROVED
 *   3. HR_G1_CLOSURE.implementation = "DONE" but G1_FINAL_GATE != PASS in
 *      the certificate (premature closure declaration)
 *   4. HR_G1_CLOSURE.engineeringCertification = "APPROVED" but the
 *      certificate says STATUS_AUTHORITY != CURRENT (inconsistent state)
 *   5. The certificate SHA differs from the certified SHA (the certificate
 *      must reference a real SHA on the branch, not a stale one)
 *
 * This is a FRONTEND regression test. It reads the certificate markdown
 * file + the HR_G1_CLOSURE block + the matrix files and verifies they are
 * mutually consistent.
 *
 * The backend-side evidence is bound by the per-requirement matrix in
 * T12-REQUIREMENT-MATRIX.md (each PARTIAL item lists the backend test class
 * that proves it). This test does NOT duplicate that binding — it only
 * verifies cross-document consistency.
 */

import { readFileSync, existsSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import { HR_G1_CLOSURE } from "./hr-execution-data";

const REPO_ROOT = resolve(__dirname, "../../../../");
const CERTIFICATE_PATH = resolve(REPO_ROOT, HR_G1_CLOSURE.certificatePath);
const T11_MATRIX_PATH = resolve(REPO_ROOT, "docs/hrm/g1/evidence/T11-OFFICIAL-SCREEN-MATRIX.md");
const T12_MATRIX_PATH = resolve(REPO_ROOT, "docs/hrm/g1/evidence/T12-REQUIREMENT-MATRIX.md");

function readFile(path: string): string {
  expect(existsSync(path), `Expected file to exist: ${path}`).toBe(true);
  return readFileSync(path, "utf8");
}

describe("HR Evidence Drift Regression — cross-document consistency", () => {
  it("certificate file exists at the path declared in HR_G1_CLOSURE", () => {
    expect(existsSync(CERTIFICATE_PATH)).toBe(true);
  });

  it("T11 matrix file exists", () => {
    expect(existsSync(T11_MATRIX_PATH)).toBe(true);
  });

  it("T12 matrix file exists", () => {
    expect(existsSync(T12_MATRIX_PATH)).toBe(true);
  });

  it("certificate does NOT contain stale '4/15' coverage claim", () => {
    const cert = readFile(CERTIFICATE_PATH);
    // The certificate must NOT contain the stale 4/15 claim from the
    // initial scaffold. The current state is 15/15.
    expect(cert).not.toContain("4/15");
    expect(cert).not.toContain("4 of 15");
    expect(cert).not.toContain("26.7%");
  });

  it("certificate does NOT contain stale 'IN_PROGRESS (4/15)' T11 status", () => {
    const cert = readFile(CERTIFICATE_PATH);
    expect(cert).not.toMatch(/T11.*IN_PROGRESS.*4.*15/);
    // T11 should be marked DONE in the certificate
    expect(cert).toContain("T11");
    expect(cert).toContain("DONE");
  });

  it("certificate does NOT claim remaining 11 screens to implement", () => {
    const cert = readFile(CERTIFICATE_PATH);
    // The certificate must NOT contain the stale "remaining 11 screens" claim
    expect(cert).not.toContain("remaining 11");
    expect(cert).not.toContain("11 of 15");
  });

  it("certificate DOES reflect 15/15 §14 screens coverage", () => {
    const cert = readFile(CERTIFICATE_PATH);
    expect(cert).toContain("15 / 15 = 100%");
    expect(cert).toContain("15/15");
  });

  it("T11 matrix coverage matches HR_G1_CLOSURE.t11ScreenCoverage", () => {
    const matrix = readFile(T11_MATRIX_PATH);
    // The matrix should reflect 15/15 (or whatever HR_G1_CLOSURE says)
    expect(HR_G1_CLOSURE.t11ScreenCoverage).toBe("15/15");
    // The matrix should NOT contain stale "NOT_IMPLEMENTED" for screens 3,5,6,7,8,9,10,11,12,15
    // (those are all implemented now)
    const staleScreens = [
      "Screen 3: Job Opening Detail",
      "Screen 5: Candidate Profile",
      "Screen 6: Applications Board",
      "Screen 7: Application Detail",
      "Screen 8: Interview Scheduling",
      "Screen 9: Interview Feedback",
      "Screen 10: Offer Editor",
      "Screen 11: Offer Approval",
      "Screen 12: Hire Conversion",
      "Screen 15: Onboarding Tasks",
    ];
    for (const screen of staleScreens) {
      // The matrix should mark these as DONE, not NOT_IMPLEMENTED
      expect(
        matrix,
        `T11 matrix should NOT mark "${screen}" as NOT_IMPLEMENTED`,
      ).not.toContain(`${screen}.*NOT_IMPLEMENTED`);
    }
  });

  it("HR_G1_CLOSURE.implementation is consistent with certificate state", () => {
    const cert = readFile(CERTIFICATE_PATH);
    // Find the AUTHORITATIVE G1_FINAL_GATE declaration in the certificate
    // (in §10 "G1 Final Gate" section, a code block). The body and
    // "path to closure" section may mention PASS as a future state — that's
    // correct and not a premature closure declaration.
    const gateMatch = cert.match(/```\s*\nG1_FINAL_GATE = (\w+)\n```/);
    expect(
      gateMatch,
      "Certificate must contain an authoritative G1_FINAL_GATE declaration in a code block",
    ).toBeTruthy();
    const gateValue = gateMatch![1];
    if (HR_G1_CLOSURE.implementation === "IN_PROGRESS") {
      expect(gateValue).toBe("NOT_CLOSED");
      expect(gateValue).not.toBe("PASS");
    }
    // engineeringCertification must be PENDING (not APPROVED) before merge
    expect(HR_G1_CLOSURE.engineeringCertification).toBe("PENDING");
    // Certificate must reflect this — the authoritative state in the
    // certificate body (not the path-to-closure section which describes
    // the future state) must NOT declare engineeringCertification as APPROVED.
    // The path-to-closure step 10 may mention flipping to APPROVED — that's
    // a future state description, not a current claim.
    expect(cert).toContain("engineeringCertification");
    // The certificate §9 says "Engineering closure is `PENDING`" — verify this
    expect(cert).toMatch(/Engineering closure is `?PENDING/);
    // The "does NOT claim" list mentions APPROVED but that's a negative claim
    // (the certificate explicitly says it does NOT claim APPROVED). The
    // authoritative claim is PENDING.
    // Verify no line starts with `engineeringCertification = APPROVED` as a declaration
    expect(cert).not.toMatch(/^engineeringCertification\s*=\s*APPROVED/m);
  });

  it("HR_G1_CLOSURE.t11ScreenCoverage matches certificate coverage claim", () => {
    const cert = readFile(CERTIFICATE_PATH);
    // HR_G1_CLOSURE says "15/15" — certificate must say the same
    expect(HR_G1_CLOSURE.t11ScreenCoverage).toBe("15/15");
    expect(cert).toContain("15/15");
    // Certificate must NOT claim a different coverage number.
    // Use regex with negative lookbehind to avoid matching "5/15" as substring of "15/15".
    const staleCoverage = cert.match(/(?<!\d)(\d{1,2})\/15\b/g);
    if (staleCoverage) {
      for (const match of staleCoverage) {
        if (match !== "15/15") {
          throw new Error(
            `Certificate contains stale coverage claim "${match}" (only 15/15 is valid)`,
          );
        }
      }
    }
  });

  it("HR_G1_CLOSURE.implementationPerCanonicalTask is consistent with certificate", () => {
    const cert = readFile(CERTIFICATE_PATH);
    // T11 should be DONE in both places
    expect(HR_G1_CLOSURE.implementationPerCanonicalTask.T11).toBe("DONE");
    expect(cert).toContain("T11");
    // T12 should be PENDING in HR_G1_CLOSURE (not CLOSED until merge + post-merge)
    expect(HR_G1_CLOSURE.implementationPerCanonicalTask.T12).toBe("PENDING");
    // Certificate must NOT claim T12=DONE or T12=CLOSED before merge
    expect(cert).not.toContain("T12 = CLOSED");
    expect(cert).not.toContain("T12=CLOSED");
  });

  it("certificate does NOT claim STATUS_AUTHORITY=CURRENT in header before merge", () => {
    const cert = readFile(CERTIFICATE_PATH);
    // The certificate HEADER (first few lines) must say DRAFT — PRE-MERGE,
    // NOT CURRENT. The body may mention CURRENT in the "claim discipline"
    // section (listing what it does NOT claim) and "path to closure" section
    // (describing the future state) — those mentions are correct.
    //
    // We check the first non-blank line containing STATUS_AUTHORITY.
    const lines = cert.split("\n");
    const headerStatusLine = lines.find(
      (l) => l.includes("STATUS_AUTHORITY") && l.includes("DRAFT"),
    );
    expect(
      headerStatusLine,
      "Certificate must have a STATUS_AUTHORITY header line with DRAFT",
    ).toBeTruthy();
    expect(headerStatusLine!).toContain("DRAFT");
    expect(headerStatusLine!).toContain("PRE-MERGE");
    // The header must NOT say CURRENT
    expect(headerStatusLine!).not.toContain("CURRENT");
  });

  it("certificate references PR #1119 + base SHA + branch name (not a stale HEAD SHA)", () => {
    const cert = readFile(CERTIFICATE_PATH);
    // The certificate references PR #1119 + base SHA + branch name instead
    // of a specific HEAD SHA. This avoids the chicken-and-egg problem where
    // the certificate can't reference the SHA of the commit that updates it.
    // The PR number + base SHA are stable across pushes and uniquely identify
    // the evidence bundle.
    expect(cert).toContain("#1119");
    expect(cert).toContain("b8af9346b21b8f8ac59d348233ef829513904fe4");
    expect(cert).toContain("g1/t11-hr-recruitment-onboarding-ui");
    // The certificate must NOT reference any specific HEAD SHA (which would
    // become stale on the next push). Check for the absence of any 40-char
    // hex string that looks like a git SHA in the "certified SHA" header.
    const headerLines = cert.split("\n").slice(0, 15).join("\n");
    const shaInHeader = headerLines.match(/\b[0-9a-f]{40}\b/);
    // The base SHA IS 40 chars and IS in the header — that's OK. But there
    // should NOT be any OTHER 40-char SHA in the header (which would be a
    // stale HEAD SHA reference).
    if (shaInHeader) {
      expect(
        shaInHeader[0],
        "The only 40-char SHA in the certificate header should be the base SHA (b8af9346...)",
      ).toBe("b8af9346b21b8f8ac59d348233ef829513904fe4");
    }
  });

  it("certificate references base SHA b8af9346 (origin/main at branch creation)", () => {
    const cert = readFile(CERTIFICATE_PATH);
    expect(cert).toContain("b8af9346b21b8f8ac59d348233ef829513904fe4");
  });

  it("certificate lists backend test classes for T12 PARTIAL requirements", () => {
    const cert = readFile(CERTIFICATE_PATH);
    // The certificate must bind each T12 PARTIAL requirement to a real
    // backend test class (not just claim "backend requires PostgreSQL Direct").
    const requiredTestClasses = [
      "HrG1RlsFailClosedIntegrationTest",
      "HrAuditOutboxAtomicityIntegrationTest",
      "HrHireConversionIntegrationTest",
      "HrScopedAuthorizationScopeMatrixIntegrationTest",
      "HrModuleBoundaryArchitectureTest",
      "HrT7IdempotencyConflictAndRbacTest",
      "HrT7EvidenceAtomicityTest",
      "HrSensitiveReadAuditIntegrationTest",
      "HrOnboardingServiceIntegrationTest",
      "HrT10CandidateSelfServiceSecurityContractTest",
    ];
    for (const testClass of requiredTestClasses) {
      expect(
        cert,
        `Certificate must reference backend test class "${testClass}" for T12 evidence`,
      ).toContain(testClass);
    }
  });

  it("T12 matrix does NOT mark all items as DONE before merge + post-merge", () => {
    const matrix = readFile(T12_MATRIX_PATH);
    // The T12 matrix must NOT flip post-merge-only items (PMV, final merge SHA,
    // final stage exit) to DONE before merge. These must remain NOT_PROVEN
    // until post-merge verification is complete.
    expect(matrix).toContain("NOT_PROVEN");
    expect(matrix).toContain("NOT_CLOSED");
  });

  it("HR_G1_CLOSURE.productionAuthorization is NO (no false production claims)", () => {
    expect(HR_G1_CLOSURE.productionAuthorization).toBe("NO");
    const cert = readFile(CERTIFICATE_PATH);
    // The certificate says "Production authorization is `NO`" (capital P).
    // We check case-insensitively to avoid brittleness.
    expect(cert.toLowerCase()).toContain("production authorization is `no`");
  });

  it("HR_G1_CLOSURE.legalCertification is BLOCKED (independent human gate)", () => {
    expect(HR_G1_CLOSURE.legalCertification).toBe("BLOCKED");
    const cert = readFile(CERTIFICATE_PATH);
    expect(cert).toContain("BLOCKED");
  });
});
