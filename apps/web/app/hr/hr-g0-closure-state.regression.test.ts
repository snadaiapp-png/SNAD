/**
 * HR-G0 Closure State Regression Test
 * ------------------------------------
 * Guards the reconciled G0 execution-dashboard state against regression.
 *
 * Background: `hr-execution-data.ts` reported G0 as NOT_STARTED even after
 * PR 914 (HRM-G0 foundation) was merged into main (748e2c60). The
 * reconciliation directive requires the dashboard to reflect the real
 * engineering state, with a documented certificate as the evidence source,
 * and requires a regression test that prevents `G0 NOT_STARTED` from
 * returning after a documented closure certificate exists.
 *
 * Claim discipline enforced here:
 *   - Engineering completion must never imply legal approval.
 *   - Engineering certification stays PENDING until human review approves it.
 *   - No production authorization may be fabricated.
 */
import { readFileSync, existsSync } from "fs";
import { resolve } from "path";
import { describe, it, expect } from "vitest";
import { HR_GROUP_DATA, HR_TASKS, HR_G0_CLOSURE } from "./hr-execution-data";
import { HrExecutionProvider } from "./hr-execution-provider";

const REPO_ROOT = resolve(__dirname, "../../../../");
const CERTIFICATE_PATH = resolve(REPO_ROOT, HR_G0_CLOSURE.certificatePath);

function readCertificate(): string {
  expect(
    existsSync(CERTIFICATE_PATH),
    `G0 closure certificate must exist at ${HR_G0_CLOSURE.certificatePath} — ` +
      "the dashboard may not claim a reconciled state without its documented evidence."
  ).toBe(true);
  return readFileSync(CERTIFICATE_PATH, "utf-8");
}

describe("HR-G0 closure state reconciliation", () => {
  it("never reports G0 as NOT_STARTED after the documented closure", () => {
    const g0 = HR_GROUP_DATA.find((g) => g.code === "G0");
    expect(g0).toBeDefined();
    expect(g0!.status).not.toBe("NOT_STARTED");
    expect(g0!.status).toBe("DONE");
  });

  it("reports every G0 task as delivered (none NOT_STARTED)", () => {
    const g0Tasks = HR_TASKS.filter((t) => t.groupCode === "G0");
    expect(g0Tasks.length).toBeGreaterThan(0);
    for (const task of g0Tasks) {
      expect(task.status, `${task.id} must not regress to NOT_STARTED`).not.toBe(
        "NOT_STARTED"
      );
    }
  });

  it("does not touch future-phase groups (G1..G5 remain not-started)", () => {
    const future = HR_GROUP_DATA.filter((g) => g.code !== "G0");
    for (const group of future) {
      expect(group.status, `${group.code} is not part of G0 closure`).toBe(
        "NOT_STARTED"
      );
    }
  });

  it("binds the dashboard state to the committed closure certificate", () => {
    const certificate = readCertificate();

    // The certificate must record implementation completion...
    expect(certificate).toMatch(/HRM_G0_IMPLEMENTATION\s*=\s*COMPLETE/);

    // ...and the exact merge SHA the dashboard block claims.
    const shaLine = certificate.match(/G0_MERGE_SHA\s*=\s*([0-9a-f]{40})/);
    expect(shaLine, "certificate must record G0_MERGE_SHA").toBeTruthy();
    expect(shaLine![1]).toBe(HR_G0_CLOSURE.mergeSha);
  });

  it("keeps engineering completion strictly separated from legal and production gates", () => {
    const certificate = readCertificate();

    // Engineering certification is NOT self-approved by code.
    expect(HR_G0_CLOSURE.engineeringCertification).toBe("PENDING");
    // Legal gate is independent and blocked pending human review.
    expect(HR_G0_CLOSURE.legalCertification).toBe("BLOCKED");
    expect(certificate).toMatch(/LEGAL_REVIEW\s*=\s*BLOCKED_OR_PENDING_HUMAN/);
    // Saudi pack stays DRAFT absent independent human evidence.
    expect(HR_G0_CLOSURE.saCountryPack).toBe("DRAFT");
    // No production authorization exists.
    expect(HR_G0_CLOSURE.productionAuthorization).toBe("NO");
    expect(certificate).toMatch(/PRODUCTION_AUTHORIZATION\s*=\s*NO/);
  });

  it("serves the documented G0 certification from a fresh provider instance (restart-safe)", async () => {
    const provider = new HrExecutionProvider();
    const certification = await provider.getCertification("HR-PROGRAM", "G0");
    expect(certification).not.toBeNull();
    // The documented certification is stable and Map-independent.
    expect(certification!.id).toBe("CERT-G0-DOCUMENTED");
    expect(certification!.status).toBe("PENDING_REVIEW");
    // Notes must carry the claim-discipline semantics.
    expect(certification!.notes).toContain("legal certification: BLOCKED");
  });

  it("prevents runtime certification submissions from overwriting the documented G0 state", async () => {
    const provider = new HrExecutionProvider();
    await provider.submitForCertification("HR-PROGRAM", "G0");
    const certification = await provider.getCertification("HR-PROGRAM", "G0");
    expect(certification!.id).toBe("CERT-G0-DOCUMENTED");
  });
});
