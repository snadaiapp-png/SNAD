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
import { HR_GROUP_DATA, HR_TASKS, HR_G0_CLOSURE, HR_G1_CLOSURE } from "./hr-execution-data";
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

  it("keeps unrelated HR phases isolated while accepting independently closed G2", () => {
    const g1 = HR_GROUP_DATA.find((g) => g.code === "G1");
    expect(g1?.status).toBe(HR_G1_CLOSURE.implementation);

    const g2 = HR_GROUP_DATA.find((g) => g.code === "G2");
    expect(g2?.status).toBe("DONE");

    const untouched = HR_GROUP_DATA.filter((g) =>
      ["G3", "G4", "G5"].includes(g.code)
    );
    for (const group of untouched) {
      expect(group.status, `${group.code} has not started`).toBe("NOT_STARTED");
    }
  });

  it("binds the dashboard state to the committed closure certificate", () => {
    const certificate = readCertificate();
    expect(certificate).toMatch(/HRM_G0_IMPLEMENTATION\s*=\s*COMPLETE/);
    const shaLine = certificate.match(/G0_MERGE_SHA\s*=\s*([0-9a-f]{40})/);
    expect(shaLine, "certificate must record G0_MERGE_SHA").toBeTruthy();
    expect(shaLine![1]).toBe(HR_G0_CLOSURE.mergeSha);
  });

  it("keeps engineering completion strictly separated from legal and production gates", () => {
    const certificate = readCertificate();
    expect(HR_G0_CLOSURE.engineeringCertification).toBe("PENDING");
    expect(HR_G0_CLOSURE.legalCertification).toBe("BLOCKED");
    expect(certificate).toMatch(/LEGAL_REVIEW\s*=\s*BLOCKED_OR_PENDING_HUMAN/);
    expect(HR_G0_CLOSURE.saCountryPack).toBe("DRAFT");
    expect(HR_G0_CLOSURE.productionAuthorization).toBe("NO");
    expect(certificate).toMatch(/PRODUCTION_AUTHORIZATION\s*=\s*NO/);
  });

  it("serves the documented G0 certification from a fresh provider instance (restart-safe)", async () => {
    const provider = new HrExecutionProvider();
    const certification = await provider.getCertification("HR-PROGRAM", "G0");
    expect(certification).not.toBeNull();
    expect(certification!.id).toBe("CERT-G0-DOCUMENTED");
    expect(certification!.status).toBe("PENDING_REVIEW");
    expect(certification!.notes).toContain("legal certification: BLOCKED");
  });

  it("prevents runtime certification submissions from overwriting the documented G0 state", async () => {
    const provider = new HrExecutionProvider();
    await provider.submitForCertification("HR-PROGRAM", "G0");
    const certification = await provider.getCertification("HR-PROGRAM", "G0");
    expect(certification!.id).toBe("CERT-G0-DOCUMENTED");
  });
});
