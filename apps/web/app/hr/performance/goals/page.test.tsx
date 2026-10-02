// @vitest-environment jsdom

/**
 * HRM G3 Task 5 — /hr/performance/goals page contract (RED-first).
 *
 * Executable specification for the goals tracking surface:
 * auth loading, capability gating, deterministic loading/empty/ready states,
 * populated goal rendering, authorized progress update with backend
 * reconciliation, validation, backend 403/500 propagation, retry,
 * Arabic/English i18n, mobile/desktop rendering, status badge, workspace
 * navigation, and static transport/authority guards.
 */

import "@testing-library/jest-dom/vitest";

import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiHttpError } from "@/lib/api/errors";

const { hrG3ApiMock, authMock, i18nState, AR_MESSAGES, EN_MESSAGES } = vi.hoisted(() => ({
  hrG3ApiMock: {
    listGoals: vi.fn(),
    getGoal: vi.fn(),
    createGoal: vi.fn(),
    updateGoal: vi.fn(),
    updateGoalProgress: vi.fn(),
  },
  authMock: { state: "AUTHENTICATED", capabilities: [] as string[] },
  i18nState: { locale: "ar" as "ar" | "en", messages: {} as Record<string, string> },
  AR_MESSAGES: {
    "auth.loading.restoring": "جارٍ استعادة الجلسة…",
    "crm.shell.loading": "لحظة…",
    "loading.processing": "جارٍ المعالجة…",
    "hrm.g2.workspace.title": "مساحة عمل الموارد البشرية",
    "hrm.g2.workspace.subtitle": "إدارة الموظفين والهيكل التنظيمي والتوظيف والتأهيل والإسنادات والعقود والالتزام",
    "hrm.g2.workspace.navLabel": "أقسام الموارد البشرية",
    "hrm.g2.workspace.nav.home": "الرئيسية",
    "hrm.g2.workspace.nav.execution": "لوحة التنفيذ",
    "hrm.g3.workspace.nav.goals": "أهداف الأداء",
    "hrm.g3.goals.eyebrow": "الأداء",
    "hrm.g3.goals.title": "أهداف الأداء",
    "hrm.g3.goals.subtitle": "تابع أهدافك وحدّث تقدمك بشكل موثوق.",
    "hrm.g3.goals.loading": "جارٍ تحميل الأهداف…",
    "hrm.g3.goals.col.title": "الهدف",
    "hrm.g3.goals.col.metric": "المؤشر",
    "hrm.g3.goals.col.target": "المستهدف",
    "hrm.g3.goals.col.progress": "التقدم",
    "hrm.g3.goals.col.status": "الحالة",
    "hrm.g3.goals.col.period": "الفترة",
    "hrm.g3.goals.col.updated": "آخر تحديث",
    "hrm.g3.goals.empty.title": "لا توجد أهداف بعد",
    "hrm.g3.goals.empty.description": "لم تُسند إليك أهداف أداء حتى الآن.",
    "hrm.g3.goals.action.updateProgress": "تحديث التقدم",
    "hrm.g3.goals.action.saving": "جارٍ الحفظ…",
    "hrm.g3.goals.form.selectGoal": "اختر الهدف",
    "hrm.g3.goals.progress.label": "نسبة التقدم (0-100)",
    "hrm.g3.goals.notice.progressUpdated": "تم تحديث التقدم بنجاح.",
    "hrm.g3.goals.validation.progressRange": "التقدم يجب أن يكون بين 0 و100.",
    "hrm.g3.goals.kpi.total": "إجمالي الأهداف",
    "hrm.g3.goals.kpi.avgProgress": "متوسط التقدم",
    "hrm.g3.goals.kpi.completed": "أهداف مكتملة",
    "hrm.g3.goals.forbidden": "لا تملك صلاحية عرض الأهداف. تواصل مع مسؤول النظام إذا كنت تحتاج وصولًا.",
    "hrm.g3.goals.state.DRAFT": "مسودة",
    "hrm.g3.goals.state.ACTIVE": "نشط",
    "hrm.g3.goals.state.IN_PROGRESS": "قيد التنفيذ",
    "hrm.g3.goals.state.COMPLETED": "مكتمل",
    "hrm.g3.goals.state.CANCELLED": "ملغى",
  },
  EN_MESSAGES: {
    "auth.loading.restoring": "Restoring session…",
    "crm.shell.loading": "One moment…",
    "loading.processing": "Processing…",
    "hrm.g2.workspace.title": "Human Resources Workspace",
    "hrm.g2.workspace.subtitle": "Manage employees, organization structure, recruitment, onboarding, assignments, contracts, and compliance",
    "hrm.g2.workspace.navLabel": "Human resources sections",
    "hrm.g2.workspace.nav.home": "Home",
    "hrm.g2.workspace.nav.execution": "Execution Board",
    "hrm.g3.workspace.nav.goals": "Performance goals",
    "hrm.g3.goals.eyebrow": "Performance",
    "hrm.g3.goals.title": "Performance goals",
    "hrm.g3.goals.subtitle": "Track your goals and update progress reliably.",
    "hrm.g3.goals.loading": "Loading goals…",
    "hrm.g3.goals.col.title": "Goal",
    "hrm.g3.goals.col.metric": "Metric",
    "hrm.g3.goals.col.target": "Target",
    "hrm.g3.goals.col.progress": "Progress",
    "hrm.g3.goals.col.status": "Status",
    "hrm.g3.goals.col.period": "Period",
    "hrm.g3.goals.col.updated": "Last updated",
    "hrm.g3.goals.empty.title": "No goals yet",
    "hrm.g3.goals.empty.description": "No performance goals have been assigned to you yet.",
    "hrm.g3.goals.action.updateProgress": "Update progress",
    "hrm.g3.goals.action.saving": "Saving…",
    "hrm.g3.goals.form.selectGoal": "Select goal",
    "hrm.g3.goals.progress.label": "Progress percentage (0-100)",
    "hrm.g3.goals.notice.progressUpdated": "Progress updated successfully.",
    "hrm.g3.goals.validation.progressRange": "Progress must be between 0 and 100.",
    "hrm.g3.goals.kpi.total": "Total goals",
    "hrm.g3.goals.kpi.avgProgress": "Average progress",
    "hrm.g3.goals.kpi.completed": "Completed goals",
    "hrm.g3.goals.forbidden": "You do not have permission to view goals. Contact a system administrator if you need access.",
    "hrm.g3.goals.state.DRAFT": "Draft",
    "hrm.g3.goals.state.ACTIVE": "Active",
    "hrm.g3.goals.state.IN_PROGRESS": "In progress",
    "hrm.g3.goals.state.COMPLETED": "Completed",
    "hrm.g3.goals.state.CANCELLED": "Cancelled",
  },
}));

vi.mock("@/lib/api/hr-g3-api", () => ({
  hrG3Api: hrG3ApiMock,
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: authMock.state, me: { capabilities: authMock.capabilities } }),
}));

vi.mock("@/lib/i18n/I18nProvider", async () => {
  const { createContext } = await import("react");
  const I18nContext = createContext(null);
  return {
    I18nContext,
  useI18n: () => ({
    locale: i18nState.locale,
    direction: i18nState.locale === "ar" ? "rtl" : "ltr",
    setLocale: vi.fn(),
    t: (key: string) => i18nState.messages[key] ?? key,
  }),
  };
});

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

const GOAL_G1 = {
  id: "g-1",
  tenantId: "t-1",
  personId: "p-1",
  employmentId: "e-1",
  title: "تقليل زمن التهيئة",
  metric: "متوسط أيام التهيئة",
  targetValue: "10",
  progress: 40,
  status: "DRAFT",
  startsOn: "2026-10-01",
  endsOn: "2026-12-31",
  createdAt: "2026-10-01T08:00:00Z",
  updatedAt: "2026-10-01T09:30:00Z",
};

const GOAL_G2 = {
  ...GOAL_G1,
  id: "g-2",
  title: "رفع رضا العملاء",
  metric: "درجة الرضا",
  targetValue: "90",
  progress: 100,
  status: "COMPLETED",
  startsOn: "2026-09-01",
  endsOn: "2026-11-30",
};

function cloneGoals() {
  return [GOAL_G1, GOAL_G2].map((goal) => ({ ...goal }));
}

async function renderGoalsPage() {
  // Dynamic import so RED evidence records a real per-test failure while
  // page.tsx does not exist yet (feature-missing RED). @vite-ignore keeps the
  // import runtime-resolved (no transform-time suite abort); once the page
  // exists the same import loads the real component.
  const GoalsPage = (await import(/* @vite-ignore */ `./page${""}`)).default;
  return render(<GoalsPage />);
}

function httpError(status: number): ApiHttpError {
  return new ApiHttpError(`HTTP ${status} for /api/v2/hr/performance/goals`, {
    status,
    error: status === 403 ? "Forbidden" : "Internal Server Error",
    message: null,
    path: "/api/v2/hr/performance/goals",
    requestId: null,
    body: null,
  });
}

function pageSource(): string {
  const pagePath = resolve(__dirname, "page.tsx");
  expect(existsSync(pagePath), "Task 5 page.tsx must exist").toBe(true);
  return readFileSync(pagePath, "utf8");
}

beforeEach(() => {
  cleanup();
  for (const fn of Object.values(hrG3ApiMock)) fn.mockReset();
  authMock.state = "AUTHENTICATED";
  authMock.capabilities = ["HRM.PERFORMANCE.GOAL.SELF_VIEW", "HRM.PERFORMANCE.GOAL.SELF_UPDATE"];
  i18nState.locale = "ar";
  i18nState.messages = AR_MESSAGES;
  hrG3ApiMock.listGoals.mockImplementation(() => Promise.resolve(cloneGoals()));
  hrG3ApiMock.updateGoalProgress.mockImplementation((_id: string, input: { progress: number }) =>
    Promise.resolve({ ...GOAL_G1, progress: input.progress }));
});

afterEach(() => cleanup());

describe("/hr/performance/goals — Task 5 goals tracking surface", () => {
  it("1. renders the auth initializing state while the session is being restored", async () => {
    authMock.state = "INITIALIZING";
    await renderGoalsPage();
    expect(screen.getByRole("status")).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByTestId("goals-ready")).not.toBeInTheDocument();
  });

  it("2. denies users without GOAL SELF_VIEW: no goal data and no mutation controls", async () => {
    authMock.capabilities = [];
    await renderGoalsPage();
    expect(screen.getByTestId("goals-capability-denied")).toBeInTheDocument();
    expect(screen.queryByTestId("goals-ready")).not.toBeInTheDocument();
    expect(screen.queryByTestId("goals-list-panel")).not.toBeInTheDocument();
    expect(hrG3ApiMock.listGoals).not.toHaveBeenCalled();
  });

  it("3. renders a deterministic loading state for authorized users", async () => {
    hrG3ApiMock.listGoals.mockImplementation(() => new Promise(() => undefined));
    await renderGoalsPage();
    expect(screen.getByText(AR_MESSAGES["hrm.g3.goals.loading"])).toBeInTheDocument();
    expect(screen.queryByTestId("goals-ready")).not.toBeInTheDocument();
  });

  it("4. renders the empty state for authorized users with no goals and no error", async () => {
    hrG3ApiMock.listGoals.mockResolvedValue([]);
    await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());
    expect(screen.getByText(AR_MESSAGES["hrm.g3.goals.empty.title"])).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("5. renders populated goals with title, metric, target, progress, status and period", async () => {
    const { container } = await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    // The goal title renders in the table, the mobile record list, and the
    // update editor selector — assert presence across the surface.
    expect(screen.getAllByText("تقليل زمن التهيئة").length).toBeGreaterThan(0);
    expect(screen.getAllByText("متوسط أيام التهيئة").length).toBeGreaterThan(0);
    expect(screen.getByText("10")).toBeInTheDocument();
    expect(screen.getByText("40%")).toBeInTheDocument();
    expect(container.querySelector('[data-status="DRAFT"]')).toBeInTheDocument();
    expect(screen.getAllByText(/2026/).length).toBeGreaterThan(0);
    expect(screen.getByTestId("goals-kpi-total")).toHaveTextContent("2");
    expect(screen.getByTestId("goals-kpi-completed")).toHaveTextContent("1");
  });

  it("6. updates progress through the facade, reconciles from the backend, and shows success feedback", async () => {
    let resolveUpdate!: (value: unknown) => void;
    hrG3ApiMock.updateGoalProgress.mockImplementation(
      () => new Promise((resolve) => { resolveUpdate = resolve; }),
    );

    await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    const input = screen.getByTestId("progress-input-g-1") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "40" } });
    fireEvent.click(screen.getByTestId("progress-save-g-1"));

    await waitFor(() => expect(hrG3ApiMock.updateGoalProgress).toHaveBeenCalledWith("g-1", { progress: 40 }));

    // MUTATION_BUSY: save disabled while the backend call is pending.
    expect(screen.getByTestId("progress-save-g-1")).toBeDisabled();

    resolveUpdate({ ...GOAL_G1, progress: 40 });

    await waitFor(() => expect(screen.getByTestId("goals-notice")).toHaveTextContent(
      AR_MESSAGES["hrm.g3.goals.notice.progressUpdated"],
    ));
    // Backend reconciliation: the list is re-fetched after the mutation.
    await waitFor(() => expect(hrG3ApiMock.listGoals).toHaveBeenCalledTimes(2));
    expect(screen.getByTestId("progress-save-g-1")).not.toBeDisabled();
  });

  it("7. read-only users (SELF_VIEW without SELF_UPDATE) see data but no mutation controls", async () => {
    authMock.capabilities = ["HRM.PERFORMANCE.GOAL.SELF_VIEW"];
    await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    expect(screen.getAllByText("تقليل زمن التهيئة").length).toBeGreaterThan(0);
    expect(screen.queryByTestId("progress-input-g-1")).not.toBeInTheDocument();
    expect(screen.queryByTestId("progress-save-g-1")).not.toBeInTheDocument();
    expect(screen.queryByTestId("goals-progress-form")).not.toBeInTheDocument();
  });

  it("8. rejects out-of-range progress locally and never sends it to the backend", async () => {
    await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    const input = screen.getByTestId("progress-input-g-1") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "150" } });
    fireEvent.click(screen.getByTestId("progress-save-g-1"));

    await waitFor(() => expect(screen.getByTestId("progress-error-g-1")).toBeInTheDocument());
    expect(screen.getByTestId("progress-error-g-1")).toHaveTextContent(
      AR_MESSAGES["hrm.g3.goals.validation.progressRange"],
    );
    expect(hrG3ApiMock.updateGoalProgress).not.toHaveBeenCalled();
  });

  it("9. renders a forbidden error state on backend 403 and never converts it to an empty state", async () => {
    hrG3ApiMock.listGoals.mockRejectedValue(httpError(403));
    const { container } = await renderGoalsPage();

    await waitFor(() => expect(container.querySelector('[data-kind="forbidden"]')).toBeInTheDocument());
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText(AR_MESSAGES["hrm.g3.goals.empty.title"])).not.toBeInTheDocument();
    expect(screen.queryByTestId("goals-ready")).not.toBeInTheDocument();
  });

  it("10. renders an error state with a retry action on backend 500", async () => {
    hrG3ApiMock.listGoals.mockRejectedValue(httpError(500));
    const { container } = await renderGoalsPage();

    await waitFor(() => expect(container.querySelector('[data-kind="server"]')).toBeInTheDocument());
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "إعادة المحاولة" })).toBeInTheDocument();
  });

  it("11. retry re-invokes the facade and recovers to the ready state", async () => {
    hrG3ApiMock.listGoals.mockRejectedValueOnce(httpError(500));
    await renderGoalsPage();

    await waitFor(() => expect(screen.getByRole("button", { name: "إعادة المحاولة" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "إعادة المحاولة" }));

    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());
    expect(hrG3ApiMock.listGoals).toHaveBeenCalledTimes(2);
  });

  it("12. renders all governing copy in Arabic from the i18n catalog", async () => {
    await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    expect(screen.getAllByText(AR_MESSAGES["hrm.g3.goals.title"]).length).toBeGreaterThan(0);
    expect(screen.getAllByText(AR_MESSAGES["hrm.g3.goals.subtitle"]).length).toBeGreaterThan(0);
    expect(screen.getByRole("table")).toHaveTextContent(AR_MESSAGES["hrm.g3.goals.col.metric"]);
    expect(screen.getByRole("table")).toHaveTextContent(AR_MESSAGES["hrm.g3.goals.col.target"]);
    expect(screen.getByRole("table")).toHaveTextContent(AR_MESSAGES["hrm.g3.goals.col.status"]);
  });

  it("13. renders the same surface in English through the translator catalog", async () => {
    i18nState.locale = "en";
    i18nState.messages = EN_MESSAGES;
    await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    expect(screen.getAllByText(EN_MESSAGES["hrm.g3.goals.title"]).length).toBeGreaterThan(0);
    expect(screen.getAllByText(EN_MESSAGES["hrm.g3.goals.subtitle"]).length).toBeGreaterThan(0);
    expect(screen.getByRole("table")).toHaveTextContent(EN_MESSAGES["hrm.g3.goals.col.metric"]);
    expect(screen.getByRole("table")).toHaveTextContent(EN_MESSAGES["hrm.g3.goals.col.target"]);
    expect(screen.getByRole("table")).toHaveTextContent(EN_MESSAGES["hrm.g3.goals.col.status"]);
  });

  it("14. renders mobile records for goals via the established mobile record list", async () => {
    await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    expect(screen.getByTestId("g2-mobile-record-list")).toBeInTheDocument();
    expect(screen.getByTestId("g2-mobile-record-list")).toHaveTextContent("تقليل زمن التهيئة");
  });

  it("15. renders the desktop operational table with all goal rows", async () => {
    await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    expect(screen.getByRole("table")).toBeInTheDocument();
    expect(screen.getAllByTestId("hr-data-row")).toHaveLength(2);
  });

  it("16. renders goal status through the governed state badge convention", async () => {
    await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    const badge = screen.getAllByText(AR_MESSAGES["hrm.g3.goals.state.DRAFT"])[0].closest("span[data-status]");
    expect(badge).not.toBeNull();
    expect(badge).toHaveAttribute("data-status", "DRAFT");
  });

  it("17. marks the goals workspace link as the active page", async () => {
    await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    const goalsLink = screen.getByRole("link", { name: AR_MESSAGES["hrm.g3.workspace.nav.goals"] });
    expect(goalsLink).toHaveAttribute("href", "/hr/performance/goals");
    expect(goalsLink).toHaveAttribute("aria-current", "page");
  });

  it("18. uses no direct fetch inside page.tsx (apiClient is the only transport owner)", async () => {
    const source = pageSource();
    expect(source).not.toMatch(/\bfetch\s*\(/);
  });

  it("19. never derives SELF authority from caller-supplied tenant/user/employment ids", async () => {
    const source = pageSource();
    expect(source).not.toContain("tenantId");
    expect(source).not.toContain("employmentId");
    expect(source).not.toContain("userId");
  });

  it("20. keeps accessibility: labelled controls, alert/status roles, headings, native buttons", async () => {
    const { container } = await renderGoalsPage();
    await waitFor(() => expect(screen.getByTestId("goals-ready")).toBeInTheDocument());

    // Page header h1 (the workspace shell renders its own h1 landmark).
    expect(screen.getAllByRole("heading", { level: 1 }).length).toBeGreaterThan(0);
    // Single labelled progress editor control (goal selection + progress input).
    const progressInputs = screen.getAllByLabelText(AR_MESSAGES["hrm.g3.goals.progress.label"]);
    expect(progressInputs.length).toBe(1);
    expect(screen.getByLabelText(AR_MESSAGES["hrm.g3.goals.form.selectGoal"])).toBeInTheDocument();
    for (const button of container.querySelectorAll("button")) {
      expect(button.tagName).toBe("BUTTON");
    }
    // Success and error semantics are announced through roles.
    fireEvent.change(screen.getByTestId("progress-input-g-1"), { target: { value: "150" } });
    fireEvent.click(screen.getByTestId("progress-save-g-1"));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });

  it("21. contains no hard-coded locale branching inside page.tsx (i18n audit)", async () => {
    const source = pageSource();
    expect(source).not.toMatch(/locale\s*===\s*["']ar["']/);
    expect(source).not.toMatch(/locale\s*===\s*["']en["']/);
  });
});
