import { existsSync, readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import * as executionData from "./hr-execution-data";
import { HR_GROUP_DATA, HR_TASKS } from "./hr-execution-data";
import { HrExecutionProvider } from "./hr-execution-provider";

type G2Closure = {
  implementation: string;
  certificatePath: string;
  manifestPath: string;
  referencePrNumber: number;
  preMergeClosureHeadSha: string;
  closureEvidenceMainSha: string;
  postMergeRuns: {
    authenticatedAcceptance: number;
    humanPreview: number;
    postMergeVerification: number;
    playwrightVisualRegression: number;
  };
  implementationPerCanonicalTask: Record<"T1" | "T2" | "T3" | "T4" | "T5", string>;
  engineeringFinalGate: string;
  engineeringCertification: string;
  visualEvidence: {
    total: number;
    arDesktop: number;
    arMobile: number;
    enDesktop: number;
    enMobile: number;
  };
  legalCertification: string;
  saCountryPack: string;
  productionAuthorization: string;
};

const HR_G2_CLOSURE = (executionData as unknown as { HR_G2_CLOSURE?: G2Closure }).HR_G2_CLOSURE;
const REPO_ROOT = resolve(__dirname, "../../../../");
const CERTIFICATE_PATH = resolve(REPO_ROOT, "docs/hrm/g2/evidence/HRM-G2-ENGINEERING-CLOSURE.md");
const MANIFEST_PATH = resolve(REPO_ROOT, "docs/hrm/g2/evidence/G2-FINAL-EVIDENCE-MANIFEST.md");

function readEvidence(path: string): string {
  expect(existsSync(path)).toBe(true);
  return readFileSync(path, "utf8");
}

describe("HR-G2 final engineering closure reconciliation", () => {
  it("exports an authoritative G2 closure block bound to the final evidence files", () => {
    expect(HR_G2_CLOSURE).toBeDefined();
    expect(HR_G2_CLOSURE?.implementation).toBe("DONE");
    expect(HR_G2_CLOSURE?.certificatePath).toBe("docs/hrm/g2/evidence/HRM-G2-ENGINEERING-CLOSURE.md");
    expect(HR_G2_CLOSURE?.manifestPath).toBe("docs/hrm/g2/evidence/G2-FINAL-EVIDENCE-MANIFEST.md");
  });

  it("binds G2 closure to PR 1173 and the exact certified SHA", () => {
    expect(HR_G2_CLOSURE?.referencePrNumber).toBe(1173);
    expect(HR_G2_CLOSURE?.preMergeClosureHeadSha).toBe("8553e4d170b6ef856e5df4771cee3c6bccbee6e7");
    expect(HR_G2_CLOSURE?.closureEvidenceMainSha).toBe("24d7a52b3696b66bca2380433b51045a35c74d66");
  });

  it("binds G2 closure to the successful post-merge runs", () => {
    expect(HR_G2_CLOSURE?.postMergeRuns).toEqual({
      authenticatedAcceptance: 36344192747,
      humanPreview: 36344192703,
      postMergeVerification: 36344192701,
      playwrightVisualRegression: 36344192757,
    });
  });

  it("declares all five canonical G2 tasks DONE", () => {
    expect(HR_G2_CLOSURE?.implementationPerCanonicalTask).toEqual({ T1: "DONE", T2: "DONE", T3: "DONE", T4: "DONE", T5: "DONE" });
    const g2Tasks = HR_TASKS.filter((task) => task.groupCode === "G2");
    expect(g2Tasks).toHaveLength(5);
    expect(g2Tasks.map((task) => [task.id, task.status])).toEqual([
      ["G2-T01", "DONE"], ["G2-T02", "DONE"], ["G2-T03", "DONE"], ["G2-T04", "DONE"], ["G2-T05", "DONE"],
    ]);
  });

  it("preserves independently closed G3 without changing later HR phases", () => {
    expect(Object.fromEntries(HR_GROUP_DATA.map((group) => [group.code, group.status]))).toEqual({
      G0: "DONE", G1: "DONE", G2: "DONE", G3: "DONE", G4: "NOT_STARTED", G5: "NOT_STARTED",
    });
  });

  it("keeps G2 at 100% and reflects G3 closure in HR program progress (19/25 = 76%)", async () => {
    const provider = new HrExecutionProvider();
    expect(await provider.getProgress("HR-PROGRAM", "G2")).toMatchObject({ total: 5, done: 5, percentage: 100 });
    expect(await provider.getProgramProgress("HR-PROGRAM")).toMatchObject({ total: 25, done: 19, percentage: 76 });
  });

  it("records the 60/60 bilingual desktop/mobile visual evidence", () => {
    expect(HR_G2_CLOSURE?.visualEvidence).toEqual({ total: 60, arDesktop: 15, arMobile: 15, enDesktop: 15, enMobile: 15 });
  });

  it("keeps legal, Saudi compliance, and production authorization as independent gates", () => {
    expect(HR_G2_CLOSURE?.engineeringFinalGate).toBe("PASS");
    expect(HR_G2_CLOSURE?.engineeringCertification).toBe("APPROVED");
    expect(HR_G2_CLOSURE?.legalCertification).toBe("BLOCKED");
    expect(HR_G2_CLOSURE?.saCountryPack).toBe("DRAFT");
    expect(HR_G2_CLOSURE?.productionAuthorization).toBe("NO");
  });

  it("reconciles the committed G2 certificate and manifest to the same exact closure", () => {
    for (const evidence of [readEvidence(CERTIFICATE_PATH), readEvidence(MANIFEST_PATH)]) {
      expect(evidence).toContain("STATUS_AUTHORITY: CURRENT");
      expect(evidence).toMatch(/G2_FINAL_GATE\s*=\s*PASS/);
      expect(evidence).toMatch(/G2_FULLY_CLOSED\s*=\s*PASS/);
      expect(evidence).toContain("24d7a52b3696b66bca2380433b51045a35c74d66");
      expect(evidence).toContain("#" + "1173");
      expect(evidence).toContain("36344192747");
      expect(evidence).toContain("36344192703");
      expect(evidence).toContain("36344192701");
      expect(evidence).toContain("36344192757");
      expect(evidence).toContain("PRODUCTION_AUTHORIZATION = NO");
      expect(evidence).toContain("PRODUCTION_READY = NOT_CLAIMED");
      expect(evidence).toContain("PRODUCTION_CERTIFIED = NOT_CLAIMED");
      expect(evidence).toContain("LEGAL_REVIEW = PENDING_HUMAN");
      expect(evidence).toContain("SA_COUNTRY_PACK = DRAFT");
    }
  });
});
