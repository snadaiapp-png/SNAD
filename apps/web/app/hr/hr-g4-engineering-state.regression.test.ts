import { readFileSync } from "fs";
import { resolve } from "path";
import { describe, expect, it } from "vitest";
import { HR_GROUP_DATA, HR_TASKS } from "./hr-execution-data";
import { HrExecutionProvider } from "./hr-execution-provider";
import { HR_WORKSPACE_LINKS } from "./components/hr-workspace";

const EVIDENCE = resolve(__dirname, "../../../../docs/hrm/g4/G4-engineering-roadmap-reconciliation-2026-10-10.md");

describe("G4 engineering closure: roadmap and RBAC remain distinct", () => {
  it("shows G4 engineering DONE without pretending G5 or the whole HR program is complete", async () => {
    expect(HR_GROUP_DATA.find(g => g.code === "G4")?.status).toBe("DONE");
    expect(HR_GROUP_DATA.find(g => g.code === "G5")?.status).toBe("NOT_STARTED");
    expect(HR_TASKS.filter(t => t.groupCode === "G4").every(t => t.status === "DONE")).toBe(true);
    expect(await new HrExecutionProvider().getProgramProgress("HR-PROGRAM")).toMatchObject({ total: 25, done: 22, percentage: 88 });
  });
  it("keeps the payroll workspace link capability-gated", () => {
    expect(HR_WORKSPACE_LINKS.find(l => l.href === "/hr/payroll")?.capability).toBe("HRM.PAYROLL.VIEW");
  });
  it("requires immutable engineering evidence and preserves pending operational gates", () => {
    const e = readFileSync(EVIDENCE, "utf8");
    expect(e).toContain("ce2df5797181eedd0fa4c2f74c602703726ccb09");
    expect(e).toContain("91ca493b3bff2ff3cd4e8b194fd33d0fdf51cde7");
    expect(e).toContain("HRM_G4_ENGINEERING = DONE");
    expect(e).toContain("HRM_G4_OPERATIONAL_CERTIFICATION = PENDING");
    expect(e).toContain("SAUDI_STATUTORY_PAYROLL = DISABLED");
  });
});
