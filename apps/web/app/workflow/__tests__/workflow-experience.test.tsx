import { describe, expect, it } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const workflowDir = path.resolve(here, "..");

function readRequired(relative: string) {
  const target = path.resolve(workflowDir, relative);
  expect(existsSync(target), `${relative} must exist`).toBe(true);
  return existsSync(target) ? readFileSync(target, "utf8") : "";
}

describe("Workflow operations experience V2", () => {
  it("uses one semantic main landmark and a responsive workspace surface", () => {
    const page = readRequired("page.tsx");
    const styles = readRequired("workflow.module.css");

    expect(page).toContain("ExecutiveShell");
    expect(page).not.toContain("<main");
    expect(page).toContain("className={styles.workspace}");
    expect(page).toContain("className={styles.sectionSurface}");
    expect(styles).toContain("inline-size: min(100%, 1440px)");
    expect(styles).toContain("@media (max-width: 640px)");
  });

  it("makes the tab navigation keyboard operable and deep-linkable", () => {
    const page = readRequired("page.tsx");
    const nav = readRequired("components/workflow-nav.tsx");

    expect(nav).toContain('event.key === "ArrowLeft"');
    expect(nav).toContain('event.key === "ArrowRight"');
    expect(nav).toContain('event.key === "Home"');
    expect(nav).toContain('event.key === "End"');
    expect(nav).toContain("tabIndex={active ? 0 : -1}");
    expect(nav).toContain("workflow-tab-");
    expect(page).toContain("window.history.replaceState");
    expect(page).toContain('window.addEventListener("hashchange"');
  });

  it("deduplicates full task-access denial while preserving independent retries", () => {
    const tasks = readRequired("components/workflow-my-tasks.tsx");

    expect(tasks).toContain("bothDenied");
    expect(tasks).toContain("WORKFLOW.TASK_EXECUTE");
    expect(tasks).toContain("!bothDenied &&");
    expect(tasks).toContain('testId="error-mine"');
    expect(tasks).toContain('testId="error-pool"');
    expect(tasks).toContain("formatWorkflowDate(workItem.dueAt)");
    expect(tasks).toContain("formatPriority(workItem.priority)");
  });

  it("presents operational states as localized badges and purposeful empty states", () => {
    const ui = readRequired("components/workflow-ui.tsx");
    const overview = readRequired("components/workflow-overview.tsx");
    const approvals = readRequired("components/workflow-approvals.tsx");
    const incidents = readRequired("components/workflow-incidents.tsx");

    expect(ui).toContain("STATUS_LABELS");
    expect(ui).toContain('OK: "سليم"');
    expect(overview).toContain("StatusBadge");
    expect(approvals).toContain("WorkflowEmptyState");
    expect(incidents).toContain("SEVERITY_ORDER");
  });

  it("redirects unauthenticated designer access instead of hanging in a loading state", () => {
    const designerPage = readRequired("definitions/[id]/page.tsx");

    expect(designerPage).toContain("useRouter");
    expect(designerPage).toContain("router.push");
    expect(designerPage).toContain("/identity/login?from=");
    expect(designerPage).toContain("encodeURIComponent");
    expect(designerPage).toContain("/workflow#definitions");
  });
});
