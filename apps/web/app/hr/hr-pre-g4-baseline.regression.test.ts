import { existsSync, readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import { HR_G0_CLOSURE, HR_G3_CLOSURE, HR_GROUP_DATA } from "./hr-execution-data";

const REPO_ROOT = resolve(__dirname, "../../../../");
const BASELINE_PATH = resolve(REPO_ROOT, "docs/hrm/pre-g4.md");

describe("HR pre-G4 governance baseline", () => {
  it("keeps G0 through G3 closed and G4/G5 not started", () => {
    expect(Object.fromEntries(HR_GROUP_DATA.map((group) => [group.code, group.status]))).toEqual({
      G0: "DONE",
      G1: "DONE",
      G2: "DONE",
      G3: "DONE",
      G4: "NOT_STARTED",
      G5: "NOT_STARTED",
    });
  });

  it("requires the reconciled G0 and G3 governance states", () => {
    expect(HR_G0_CLOSURE.engineeringCertification).toBe("APPROVED");
    expect(HR_G3_CLOSURE.productionClosure).toBe("PASS");
    expect(HR_G3_CLOSURE.productionClosureMainSha).toBe("2ba84d4c146ca0b9b4f105fed53d81cfa7d2f581");
    expect(HR_G3_CLOSURE.legalCertification).toBe("BLOCKED");
    expect(HR_G3_CLOSURE.saCountryPack).toBe("DRAFT");
  });

  it("keeps the committed pre-G4 baseline evidence available", () => {
    expect(existsSync(BASELINE_PATH)).toBe(true);
    const evidence = readFileSync(BASELINE_PATH, "utf8");
    expect(evidence).toContain("STATUS_AUTHORITY: CURRENT");
    expect(evidence).toContain("G4_ENGINEERING_ENTRY_GATE = PASS");
    expect(evidence).toContain("37329097719");
    expect(evidence).toContain("37329097684");
    expect(evidence).toContain("37329356654");
  });
});
