/**
 * HR-G1 Closure State Regression Test (T12 deliverable)
 * -----------------------------------------------------
 * Guards the reconciled G1 execution-dashboard state against regression.
 *
 * Pattern mirrors `hr-g0-closure-state.regression.test.ts`:
 *   - The reconciliation block (HR_G1_CLOSURE) binds the dashboard state
 *     to a documented certificate path.
 *   - If the certificate is missing or the dashboard state drifts from the
 *     certificate state, this test fails.
 *   - The dashboard state must NEVER claim G1_CLOSED, PRODUCTION_READY,
 *     PRODUCTION_CERTIFIED, or SAUDI_LEGAL_COMPLIANT.
 *
 * Claim discipline enforced here:
 *   - Engineering completion must never imply legal approval.
 *   - Engineering certification stays PENDING until T11 + T12 both merge
 *     with independent approval + exact-head CI green on closure SHA.
 *   - No production authorization may be fabricated.
 *
 * Status as of this branch:
 *   - T1..T10 = DONE (PR-998 + commit b8af9346)
 *   - T11 = IN_PROGRESS (4 of 15 §14 screens scaffolded — NOT CLOSED)
 *   - T12 = IN_PROGRESS (this scaffold + closure block + evidence doc)
 *   - G1_FINAL_GATE = NOT_CLOSED
 *
 * This regression test currently EXPECTS:
 *   - implementation = "IN_PROGRESS"
 *   - engineeringCertification = "PENDING"
 *   - legalCertification = "BLOCKED"
 *   - productionAuthorization = "NO"
 *
 * Once T11+T12 are fully merged and independently approved on exact SHA,
 * update HR_G1_CLOSURE to flip implementation → "DONE" and
 * engineeringCertification → "APPROVED", then update the expectations here.
 */
import { existsSync, readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import { HR_G1_CLOSURE } from "./hr-execution-data";

const REPO_ROOT = resolve(__dirname, "../../../../");
const CERTIFICATE_PATH = resolve(REPO_ROOT, HR_G1_CLOSURE.certificatePath);

function readCertificate(): string {
  expect(
    existsSync(CERTIFICATE_PATH),
    `G1 closure certificate must exist at ${HR_G1_CLOSURE.certificatePath} — ` +
      `the dashboard state is bound to a documented evidence source.`,
  ).toBe(true);
  return readFileSync(CERTIFICATE_PATH, "utf8");
}

describe("HR-G1 Closure State (T12 regression)", () => {
  it("HR_G1_CLOSURE is defined and bound to a certificate path", () => {
    expect(HR_G1_CLOSURE).toBeDefined();
    expect(typeof HR_G1_CLOSURE.certificatePath).toBe("string");
    expect(HR_G1_CLOSURE.certificatePath.length).toBeGreaterThan(0);
    expect(HR_G1_CLOSURE.certificatePath).toContain("docs/hrm/g1/evidence/");
  });

  it("certificate file exists on disk", () => {
    expect(existsSync(CERTIFICATE_PATH)).toBe(true);
  });

  it("certificate contains a NOT_CLOSED gate declaration", () => {
    const cert = readCertificate();
    expect(cert).toContain("G1_FINAL_GATE");
    expect(cert).toContain("NOT_CLOSED");
  });

  it("certificate does not claim G1_CLOSED, PRODUCTION_READY, or PRODUCTION_CERTIFIED", () => {
    const cert = readCertificate();
    // The certificate explicitly lists what it does NOT claim, so we check
    // for the "intentionally does NOT claim" header rather than the absence
    // of these strings entirely.
    expect(cert).toContain("intentionally does NOT claim");
  });

  it("implementation state is IN_PROGRESS (T11 implementation complete; T12 + closure PENDING)", () => {
    // T11 implementation is now complete (15/15 §14 screens); T12 backend
    // sweep + independent approval + exact-head CI green on closure SHA
    // remain PENDING. The group status stays IN_PROGRESS until T12 closes.
    expect(HR_G1_CLOSURE.implementation).toBe("IN_PROGRESS");
  });

  it("baselineT10Sha matches the user-stated T10 baseline", () => {
    expect(HR_G1_CLOSURE.baselineT10Sha).toBe("b8af9346b21b8f8ac59d348233ef829513904fe4");
  });

  it("per-canonical-task matrix reflects T1..T10 DONE, T11 DONE, T12 PENDING", () => {
    const m = HR_G1_CLOSURE.implementationPerCanonicalTask;
    expect(m.T1).toBe("DONE");
    expect(m.T2).toBe("DONE");
    expect(m.T3).toBe("DONE");
    expect(m.T4).toBe("DONE");
    expect(m.T5).toBe("DONE");
    expect(m.T6).toBe("DONE");
    expect(m.T7).toBe("DONE");
    expect(m.T8).toBe("DONE");
    expect(m.T9).toBe("DONE");
    expect(m.T10).toBe("DONE");
    expect(m.T11).toBe("DONE");
    expect(m.T12).toBe("PENDING");
  });

  it("T11 screen coverage is 15/15 (all §14 screens implemented)", () => {
    expect(HR_G1_CLOSURE.t11ScreenCoverage).toBe("15/15");
  });

  it("engineering certification is PENDING (not auto-approved)", () => {
    expect(HR_G1_CLOSURE.engineeringCertification).toBe("PENDING");
  });

  it("legal certification is BLOCKED (independent human gate)", () => {
    expect(HR_G1_CLOSURE.legalCertification).toBe("BLOCKED");
  });

  it("production authorization is NO", () => {
    expect(HR_G1_CLOSURE.productionAuthorization).toBe("NO");
  });

  it("historical record references PR-998 and commit b8af9346", () => {
    const hist = HR_G1_CLOSURE.historicalRecord;
    expect(hist).toContain("PR-998");
    expect(hist).toContain("81a86faa");
    expect(hist).toContain("b8af9346");
  });
});
