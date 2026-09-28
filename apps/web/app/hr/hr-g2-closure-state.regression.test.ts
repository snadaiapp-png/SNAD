import { describe, expect, it } from "vitest";
import { HR_G2_CLOSURE } from "./hr-g2-closure";
import { HrExecutionProvider } from "./hr-execution-provider";

describe("HR-G2 final engineering closure", () => {
  it("binds G2 to the exact governed main closure evidence", () => {
    expect(HR_G2_CLOSURE.implementation).toBe("DONE");
    expect(HR_G2_CLOSURE.closureEvidenceMainSha).toBe(
      "24d7a52b3696b66bca2380433b51045a35c74d66",
    );
    expect(HR_G2_CLOSURE.authenticatedAcceptanceRunId).toBe(36344192747);
    expect(HR_G2_CLOSURE.humanPreviewRunId).toBe(36344192703);
    expect(HR_G2_CLOSURE.postMergeVerificationRunId).toBe(36344192701);
    expect(HR_G2_CLOSURE.visualEvidenceCases).toBe(60);
    expect(HR_G2_CLOSURE.visualEvidencePasses).toBe(60);
  });

  it("marks the execution dashboard G2 group complete", async () => {
    const provider = new HrExecutionProvider();
    const g2 = await provider.getGroup("HR-PROGRAM", "G2");
    expect(g2?.status).toBe("DONE");
  });

  it("marks all G2 roadmap tasks complete including the monthly report", async () => {
    const provider = new HrExecutionProvider();
    const g2Tasks = await provider.getTasks("HR-PROGRAM", "G2");
    expect(g2Tasks).toHaveLength(5);
    expect(g2Tasks.every((task) => task.status === "DONE")).toBe(true);
    expect(g2Tasks.find((task) => task.id === "G2-T05")?.status).toBe("DONE");
  });

  it("raises governed HR execution progress to 60 percent", async () => {
    const provider = new HrExecutionProvider();
    const progress = await provider.getProgramProgress("HR-PROGRAM");
    expect(progress.total).toBe(25);
    expect(progress.done + progress.approved).toBe(15);
    expect(progress.percentage).toBe(60);
  });
});
