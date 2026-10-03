import { existsSync, readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import { HR_G3_CLOSURE, HR_GROUP_DATA, HR_TASKS } from "./hr-execution-data";
import { HrExecutionProvider } from "./hr-execution-provider";

const REPO_ROOT = resolve(__dirname, "../../../../");
const CERTIFICATE_PATH = resolve(REPO_ROOT, HR_G3_CLOSURE.certificatePath);
const MANIFEST_PATH = resolve(REPO_ROOT, HR_G3_CLOSURE.manifestPath);

function readEvidence(path: string): string {
  expect(existsSync(path)).toBe(true);
  return readFileSync(path, "utf8");
}

describe("HR-G3 final engineering closure reconciliation", () => {
  it("exports an authoritative G3 closure block bound to committed evidence", () => {
    expect(HR_G3_CLOSURE.implementation).toBe("DONE");
    expect(HR_G3_CLOSURE.certificatePath).toBe(
      "docs/hrm/g3/evidence/HRM-G3-ENGINEERING-CLOSURE.md"
    );
    expect(HR_G3_CLOSURE.manifestPath).toBe(
      "docs/hrm/g3/evidence/G3-FINAL-EVIDENCE-MANIFEST.md"
    );
  });

  it("binds G3 closure to the exact implementation and verification evidence", () => {
    expect(HR_G3_CLOSURE.implementationMergeSha).toBe(
      "99adf88046772fbe6492f5e74c4c5a184c6d0376"
    );
    expect(HR_G3_CLOSURE.authenticatedAcceptanceRunId).toBe(37078851136);
    expect(HR_G3_CLOSURE.postMergeVerificationRunId).toBe(37078851075);
    expect(HR_G3_CLOSURE.schemaIsolationDispatchRunId).toBe(37088523419);
    expect(HR_G3_CLOSURE.schemaIsolationCheckJobId).toBe(111103669620);
  });

  it("declares all four G3 roadmap tasks DONE", () => {
    expect(HR_G3_CLOSURE.implementationPerRoadmapTask).toEqual({
      T01: "DONE", T02: "DONE", T03: "DONE", T04: "DONE",
    });
    expect(
      HR_TASKS.filter((task) => task.groupCode === "G3").map((task) => [
        task.id,
        task.status,
      ])
    ).toEqual([
      ["G3-T01", "DONE"],
      ["G3-T02", "DONE"],
      ["G3-T03", "DONE"],
      ["G3-T04", "DONE"],
    ]);
  });

  it("marks G3 DONE while leaving G4 and G5 untouched", () => {
    expect(Object.fromEntries(HR_GROUP_DATA.map((group) => [group.code, group.status]))).toEqual({
      G0: "DONE", G1: "DONE", G2: "DONE", G3: "DONE", G4: "NOT_STARTED", G5: "NOT_STARTED",
    });
  });

  it("recomputes G3 to 100% and HR program progress to 76% (19/25)", async () => {
    const provider = new HrExecutionProvider();
    expect(await provider.getProgress("HR-PROGRAM", "G3")).toMatchObject({
      total: 4, done: 4, percentage: 100,
    });
    expect(await provider.getProgramProgress("HR-PROGRAM")).toMatchObject({
      total: 25, done: 19, percentage: 76,
    });
  });

  it("keeps engineering closure separate from production/legal authorization", () => {
    expect(HR_G3_CLOSURE.engineeringFinalGate).toBe("PASS");
    expect(HR_G3_CLOSURE.engineeringCertification).toBe("APPROVED");
    expect(HR_G3_CLOSURE.productionSmokeForG3Closure).toBe("NOT_APPLICABLE");
    expect(HR_G3_CLOSURE.productionAuthorization).toBe("NO");
    expect(HR_G3_CLOSURE.legalCertification).toBe("BLOCKED");
    expect(HR_G3_CLOSURE.saCountryPack).toBe("DRAFT");
  });

  it("reconciles the certificate and manifest to the same closed engineering state", () => {
    for (const evidence of [readEvidence(CERTIFICATE_PATH), readEvidence(MANIFEST_PATH)]) {
      expect(evidence).toContain("STATUS_AUTHORITY: CURRENT");
      expect(evidence).toMatch(/G3_FINAL_GATE\s*=\s*PASS/);
      expect(evidence).toMatch(/G3_FULLY_CLOSED\s*=\s*PASS/);
      expect(evidence).toContain("99adf88046772fbe6492f5e74c4c5a184c6d0376");
      expect(evidence).toContain("37078851136");
      expect(evidence).toContain("37078851075");
      expect(evidence).toContain("37088523419");
      expect(evidence).toContain("111103669620");
      expect(evidence).toContain("PRODUCTION_AUTHORIZATION = NO");
      expect(evidence).toContain("PRODUCTION_READY = NOT_CLAIMED");
      expect(evidence).toContain("PRODUCTION_CERTIFIED = NOT_CLAIMED");
      expect(evidence).toContain("LEGAL_REVIEW = PENDING_HUMAN");
      expect(evidence).toContain("SA_COUNTRY_PACK = DRAFT");
    }
  });
});
