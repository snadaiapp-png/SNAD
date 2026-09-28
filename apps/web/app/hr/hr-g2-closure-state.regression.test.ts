import { existsSync, readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import { HR_G2_CLOSURE, HR_GROUP_DATA, HR_TASKS } from "./hr-execution-data";

const REPO_ROOT = resolve(__dirname, "../../../../");
const CERTIFICATE_PATH = resolve(REPO_ROOT, HR_G2_CLOSURE.certificatePath);

function readCertificate(): string {
  expect(existsSync(CERTIFICATE_PATH)).toBe(true);
  return readFileSync(CERTIFICATE_PATH, "utf8");
}

describe("HR-G2 final engineering closure reconciliation", () => {
  it("reports G2 and all five canonical execution tasks DONE", () => {
    expect(HR_GROUP_DATA.find((g) => g.code === "G2")?.status).toBe("DONE");
    const tasks = HR_TASKS.filter((t) => t.groupCode === "G2");
    expect(tasks.map((t) => t.id)).toEqual([
      "G2-T01",
      "G2-T02",
      "G2-T03",
      "G2-T04",
      "G2-T05",
    ]);
    for (const task of tasks) expect(task.status, task.id).toBe("DONE");
  });

  it("binds G2 closure to PR 1173, exact main SHA, and the four post-merge runs", () => {
    expect(HR_G2_CLOSURE.prNumber).toBe(1173);
    expect(HR_G2_CLOSURE.closureEvidenceMainSha).toBe(
      "24d7a52b3696b66bca2380433b51045a35c74d66",
    );
    expect(HR_G2_CLOSURE.postMergeRunIds).toEqual([
      36344192747,
      36344192703,
      36344192701,
      36344192757,
    ]);
    expect(HR_G2_CLOSURE.visualEvidence).toEqual({
      total: 60,
      passed: 60,
      arDesktop: 15,
      arMobile: 15,
      enDesktop: 15,
      enMobile: 15,
    });
  });

  it("binds dashboard state to a committed closure certificate", () => {
    const cert = readCertificate();
    expect(cert).toContain("STATUS_AUTHORITY: CURRENT");
    expect(cert).toContain("G2_FULLY_CLOSED = PASS");
    expect(cert).toContain("PR_1173_MERGED = PASS");
    expect(cert).toContain("G2_EXACT_MAIN_POST_MERGE = PASS");
    expect(cert).toContain(HR_G2_CLOSURE.closureEvidenceMainSha);
    for (const runId of HR_G2_CLOSURE.postMergeRunIds) {
      expect(cert).toContain(String(runId));
    }
  });

  it("does not inflate G2 engineering closure into legal, Saudi, or production authorization", () => {
    expect(HR_G2_CLOSURE.engineeringFinalGate).toBe("PASS");
    expect(HR_G2_CLOSURE.legalCertification).toBe("BLOCKED");
    expect(HR_G2_CLOSURE.saCountryPack).toBe("DRAFT");
    expect(HR_G2_CLOSURE.productionAuthorization).toBe("NO");
  });

  it("does not alter G0, G1, or future G3-G5 states", () => {
    expect(HR_GROUP_DATA.find((g) => g.code === "G0")?.status).toBe("DONE");
    expect(HR_GROUP_DATA.find((g) => g.code === "G1")?.status).toBe("DONE");
    for (const code of ["G3", "G4", "G5"]) {
      expect(HR_GROUP_DATA.find((g) => g.code === code)?.status, code).toBe("NOT_STARTED");
    }
  });
});
