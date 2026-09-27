import { describe, expect, it } from "vitest";
import { HR_GROUP_DATA, HR_TASKS } from "./hr-execution-data";
import { HrExecutionProvider } from "./hr-execution-provider";

describe("HR-G2 final closure state", () => {
  it("publishes G2 as DONE only after every G2 task is delivered", () => {
    const g2 = HR_GROUP_DATA.find((group) => group.code === "G2");
    const g2Tasks = HR_TASKS.filter((task) => task.groupCode === "G2");

    expect(g2).toBeDefined();
    expect(g2Tasks).toHaveLength(5);
    expect(g2Tasks.every((task) => task.status === "DONE")).toBe(true);
    expect(g2?.status).toBe("DONE");
  });

  it("raises the execution dashboard from 56% to 60% after G2 closure", async () => {
    const provider = new HrExecutionProvider();
    const progress = await provider.getProgramProgress("HR-PROGRAM");

    expect(progress.total).toBe(25);
    expect(progress.done).toBe(15);
    expect(progress.notStarted).toBe(10);
    expect(progress.percentage).toBe(60);
  });
});
