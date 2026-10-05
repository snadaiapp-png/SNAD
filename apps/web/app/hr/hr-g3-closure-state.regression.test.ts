import { existsSync, readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import * as executionData from "./hr-execution-data";
import { HR_GROUP_DATA, HR_TASKS } from "./hr-execution-data";
import { HrExecutionProvider } from "./hr-execution-provider";

type G3Closure = {
  implementation: string;
  certificatePath: string;
  manifestPath: string;
  referencePrNumber: number;
  closureEvidenceMainSha: string;
  productionClosurePath: string;
  productionClosureMainSha: string;
  postMergeRuns: {
    authenticatedAcceptance: number;
    schemaIsolation: number;
    ci: number;
    postMergeVerification: number;
    playwrightVisualRegression: number;
  };
  implementationPerCanonicalTask: Record<"T1" | "T2" | "T3" | "T4" | "T5" | "T6" | "T7" | "T8", string>;
  engineeringFinalGate: string;
  engineeringCertification: string;
  productionClosure: string;
  productionRuns: {
    postMergeVerification: number;
    vercelMainProductionReconcile: number;
    operationalSmoke: number;
  };
  legalCertification: string;
  saCountryPack: string;
  productionAuthorization: string;
};

const HR_G3_CLOSURE = (executionData as unknown as { HR_G3_CLOSURE?: G3Closure }).HR_G3_CLOSURE;
const REPO_ROOT = resolve(__dirname, "../../../../");
const CERTIFICATE_PATH = resolve(REPO_ROOT, "docs/hrm/g3/evidence/HRM-G3-ENGINEERING-CLOSURE.md");
const MANIFEST_PATH = resolve(REPO_ROOT, "docs/hrm/g3/evidence/G3-FINAL-EVIDENCE-MANIFEST.md");

function readEvidence(path: string): string {
  expect(existsSync(path)).toBe(true);
  return readFileSync(path, "utf8");
}

describe("HR-G3 final engineering closure reconciliation", () => {
  it("exports an authoritative G3 closure block bound to final evidence", () => {
    expect(HR_G3_CLOSURE).toBeDefined();
    expect(HR_G3_CLOSURE?.implementation).toBe("DONE");
    expect(HR_G3_CLOSURE?.certificatePath).toBe("docs/hrm/g3/evidence/HRM-G3-ENGINEERING-CLOSURE.md");
    expect(HR_G3_CLOSURE?.manifestPath).toBe("docs/hrm/g3/evidence/G3-FINAL-EVIDENCE-MANIFEST.md");
  });

  it("binds G3 closure to the exact certified main SHA and real runs", () => {
    expect(HR_G3_CLOSURE?.referencePrNumber).toBe(1234);
    expect(HR_G3_CLOSURE?.closureEvidenceMainSha).toBe("0acdc42507d817766a0113ae418c7c36dde678d1");
    expect(HR_G3_CLOSURE?.postMergeRuns).toEqual({
      authenticatedAcceptance: 37121204831,
      schemaIsolation: 37121204843,
      ci: 37121204852,
      postMergeVerification: 37121204833,
      playwrightVisualRegression: 37121204782,
    });
  });

  it("declares all eight canonical G3 implementation tasks DONE", () => {
    expect(HR_G3_CLOSURE?.implementationPerCanonicalTask).toEqual({
      T1: "DONE", T2: "DONE", T3: "DONE", T4: "DONE",
      T5: "DONE", T6: "DONE", T7: "DONE", T8: "DONE",
    });
  });

  it("reconciles the roadmap G3 group and four legacy G3 rows to DONE", () => {
    expect(Object.fromEntries(HR_GROUP_DATA.map((group) => [group.code, group.status]))).toEqual({
      G0: "DONE", G1: "DONE", G2: "DONE", G3: "DONE", G4: "NOT_STARTED", G5: "NOT_STARTED",
    });
    const g3Tasks = HR_TASKS.filter((task) => task.groupCode === "G3");
    expect(g3Tasks.map((task) => [task.id, task.status])).toEqual([
      ["G3-T01", "DONE"], ["G3-T02", "DONE"], ["G3-T03", "DONE"], ["G3-T04", "DONE"],
    ]);
  });

  it("recomputes G3 to 100% and HR program progress to 76% (19/25)", async () => {
    const provider = new HrExecutionProvider();
    expect(await provider.getProgress("HR-PROGRAM", "G3")).toMatchObject({ total: 4, done: 4, percentage: 100 });
    expect(await provider.getProgramProgress("HR-PROGRAM")).toMatchObject({ total: 25, done: 19, percentage: 76 });
  });

  it("keeps legal, Saudi compliance, and production authorization independent", () => {
    expect(HR_G3_CLOSURE?.engineeringFinalGate).toBe("PASS");
    expect(HR_G3_CLOSURE?.engineeringCertification).toBe("APPROVED");
    expect(HR_G3_CLOSURE?.legalCertification).toBe("BLOCKED");
    expect(HR_G3_CLOSURE?.saCountryPack).toBe("DRAFT");
    expect(HR_G3_CLOSURE?.productionAuthorization).toBe("NO");
  });

  it("reconciles the committed certificate and manifest to the same exact closure", () => {
    for (const evidence of [readEvidence(CERTIFICATE_PATH), readEvidence(MANIFEST_PATH)]) {
      expect(evidence).toContain("STATUS_AUTHORITY: CURRENT");
      expect(evidence).toMatch(/G3_FINAL_GATE\s*=\s*PASS/);
      expect(evidence).toMatch(/G3_FULLY_CLOSED\s*=\s*PASS/);
      expect(evidence).toContain("0acdc42507d817766a0113ae418c7c36dde678d1");
      expect(evidence).toContain("#" + "1234");
      expect(evidence).toContain("37121204831");
      expect(evidence).toContain("37121204843");
      expect(evidence).toContain("37121204852");
      expect(evidence).toContain("37121204833");
      expect(evidence).toContain("37121204782");
      expect(evidence).toContain("PRODUCTION_AUTHORIZATION = NO");
      expect(evidence).toContain("PRODUCTION_READY = NOT_CLAIMED");
      expect(evidence).toContain("PRODUCTION_CERTIFIED = NOT_CLAIMED");
      expect(evidence).toContain("LEGAL_REVIEW = PENDING_HUMAN");
      expect(evidence).toContain("SA_COUNTRY_PACK = DRAFT");
    }
  });
});
