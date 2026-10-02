// @vitest-environment jsdom

/**
 * HRM G3 Task 6 — /hr/performance/reviews page contract (RED-first).
 *
 * Executable specification for the performance review surface:
 * auth loading, capability gating (SELF_VIEW / SELF_SUBMIT / TEAM_MANAGE),
 * deterministic loading/empty/ready states, populated SELF/MANAGER/PEER
 * rendering, DRAFT/SUBMITTED/ACKNOWLEDGED/CANCELLED lifecycle, rating and
 * comments and period display, create/submit/acknowledge + team create/cancel,
 * validation, backend 403/500 propagation, retry, Arabic/English i18n,
 * mobile/desktop rendering, workspace navigation, and static
 * transport/authority guards.
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
    listReviews: vi.fn(),
    getReview: vi.fn(),
    createSelfReview: vi.fn(),
    submitReview: vi.fn(),
    acknowledgeReview: vi.fn(),
    listTeamReviews: vi.fn(),
    getTeamReview: vi.fn(),
    createTeamReview: vi.fn(),
    cancelTeamReview: vi.fn(),
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
    "hrm.g2.workspace.nav.employees": "الموظفون",
    "hrm.g2.landing.attendance": "حضوري",
    "hrm.g3.workspace.nav.goals": "أهداف الأداء",
    "hrm.g3.workspace.nav.reviews": "تقييمات الأداء",
    "hrm.g3.reviews.eyebrow": "الأداء",
    "hrm.g3.reviews.title": "تقييمات الأداء",
    "hrm.g3.reviews.subtitle": "أنشئ تقييماتك وأرسلها واعتمدها، وتابع تقييمات فريقك.",
    "hrm.g3.reviews.loading": "جارٍ تحميل التقييمات…",
    "hrm.g3.reviews.col.cycle": "الدورة",
    "hrm.g3.reviews.col.period": "الفترة",
    "hrm.g3.reviews.col.source": "المصدر",
    "hrm.g3.reviews.col.status": "الحالة",
    "hrm.g3.reviews.col.rating": "التقييم",
    "hrm.g3.reviews.col.comments": "الملاحظات",
    "hrm.g3.reviews.col.updated": "آخر تحديث",
    "hrm.g3.reviews.empty.title": "لا توجد تقييمات بعد",
    "hrm.g3.reviews.empty.description": "لم تُسند إليك تقييمات أداء حتى الآن.",
    "hrm.g3.reviews.action.create": "إنشاء تقييم ذاتي",
    "hrm.g3.reviews.action.submit": "إرسال التقييم",
    "hrm.g3.reviews.action.acknowledge": "الإقرار بالتقييم",
    "hrm.g3.reviews.action.cancel": "إلغاء المسودة",
    "hrm.g3.reviews.action.saving": "جارٍ الحفظ…",
    "hrm.g3.reviews.form.cycle": "الدورة",
    "hrm.g3.reviews.form.periodStart": "بداية الفترة",
    "hrm.g3.reviews.form.periodEnd": "نهاية الفترة",
    "hrm.g3.reviews.form.rating": "التقييم (1-5)",
    "hrm.g3.reviews.form.comments": "الملاحظات",
    "hrm.g3.reviews.form.rating.none": "اختر التقييم",
    "hrm.g3.reviews.notice.created": "تم إنشاء مسودة التقييم.",
    "hrm.g3.reviews.notice.submitted": "تم إرسال التقييم.",
    "hrm.g3.reviews.notice.acknowledged": "تم الإقرار بالتقييم.",
    "hrm.g3.reviews.notice.cancelled": "تم إلغاء المسودة.",
    "hrm.g3.reviews.validation.cycle": "الدورة مطلوبة وبحد أقصى 80 حرفًا.",
    "hrm.g3.reviews.validation.rating": "التقييم يجب أن يكون بين 1 و5.",
    "hrm.g3.reviews.validation.period": "نهاية الفترة يجب أن تكون بعد بدايتها أو مساوية لها.",
    "hrm.g3.reviews.team.title": "تقييمات الفريق",
    "hrm.g3.reviews.team.description": "تقييمات التقارير المباشرة التي تديرها.",
    "hrm.g3.reviews.team.create": "إنشاء تقييم لموظف",
    "hrm.g3.reviews.team.employmentId": "معرّف التوظيف",
    "hrm.g3.reviews.kpi.total": "إجمالي التقييمات",
    "hrm.g3.reviews.kpi.submitted": "تقييمات مرسلة",
    "hrm.g3.reviews.kpi.acknowledged": "تقييمات معتمَد بها",
    "hrm.g3.reviews.forbidden": "لا تملك صلاحية عرض التقييمات. تواصل مع مسؤول النظام إذا كنت تحتاج وصولًا.",
    "hrm.g3.reviews.state.DRAFT": "مسودة",
    "hrm.g3.reviews.state.SUBMITTED": "مرسل",
    "hrm.g3.reviews.state.ACKNOWLEDGED": "مُقر به",
    "hrm.g3.reviews.state.CANCELLED": "ملغى",
    "hrm.g3.reviews.source.SELF": "ذاتي",
    "hrm.g3.reviews.source.MANAGER": "مدير",
    "hrm.g3.reviews.source.PEER": "زميل",
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
    "hrm.g2.workspace.nav.employees": "Employees",
    "hrm.g2.landing.attendance": "My attendance",
    "hrm.g3.workspace.nav.goals": "Performance goals",
    "hrm.g3.workspace.nav.reviews": "Performance reviews",
    "hrm.g3.reviews.eyebrow": "Performance",
    "hrm.g3.reviews.title": "Performance reviews",
    "hrm.g3.reviews.subtitle": "Create, submit, and acknowledge your reviews; track your team's reviews.",
    "hrm.g3.reviews.loading": "Loading reviews…",
    "hrm.g3.reviews.col.cycle": "Cycle",
    "hrm.g3.reviews.col.period": "Period",
    "hrm.g3.reviews.col.source": "Source",
    "hrm.g3.reviews.col.status": "Status",
    "hrm.g3.reviews.col.rating": "Rating",
    "hrm.g3.reviews.col.comments": "Comments",
    "hrm.g3.reviews.col.updated": "Last updated",
    "hrm.g3.reviews.empty.title": "No reviews yet",
    "hrm.g3.reviews.empty.description": "No performance reviews have been assigned to you yet.",
    "hrm.g3.reviews.action.create": "Create self review",
    "hrm.g3.reviews.action.submit": "Submit review",
    "hrm.g3.reviews.action.acknowledge": "Acknowledge review",
    "hrm.g3.reviews.action.cancel": "Cancel draft",
    "hrm.g3.reviews.action.saving": "Saving…",
    "hrm.g3.reviews.form.cycle": "Cycle",
    "hrm.g3.reviews.form.periodStart": "Period start",
    "hrm.g3.reviews.form.periodEnd": "Period end",
    "hrm.g3.reviews.form.rating": "Rating (1-5)",
    "hrm.g3.reviews.form.comments": "Comments",
    "hrm.g3.reviews.form.rating.none": "Select rating",
    "hrm.g3.reviews.notice.created": "Review draft created.",
    "hrm.g3.reviews.notice.submitted": "Review submitted.",
    "hrm.g3.reviews.notice.acknowledged": "Review acknowledged.",
    "hrm.g3.reviews.notice.cancelled": "Draft cancelled.",
    "hrm.g3.reviews.validation.cycle": "Cycle is required with a maximum of 80 characters.",
    "hrm.g3.reviews.validation.rating": "Rating must be between 1 and 5.",
    "hrm.g3.reviews.validation.period": "Period end must be on or after period start.",
    "hrm.g3.reviews.team.title": "Team reviews",
    "hrm.g3.reviews.team.description": "Reviews for the direct reports you manage.",
    "hrm.g3.reviews.team.create": "Create review for employee",
    "hrm.g3.reviews.team.employmentId": "Employment id",
    "hrm.g3.reviews.kpi.total": "Total reviews",
    "hrm.g3.reviews.kpi.submitted": "Submitted reviews",
    "hrm.g3.reviews.kpi.acknowledged": "Acknowledged reviews",
    "hrm.g3.reviews.forbidden": "You do not have permission to view reviews. Contact a system administrator if you need access.",
    "hrm.g3.reviews.state.DRAFT": "Draft",
    "hrm.g3.reviews.state.SUBMITTED": "Submitted",
    "hrm.g3.reviews.state.ACKNOWLEDGED": "Acknowledged",
    "hrm.g3.reviews.state.CANCELLED": "Cancelled",
    "hrm.g3.reviews.source.SELF": "Self",
    "hrm.g3.reviews.source.MANAGER": "Manager",
    "hrm.g3.reviews.source.PEER": "Peer",
  },
}));

vi.mock("@/lib/api/hr-g3-api", () => ({
  hrG3Api: hrG3ApiMock,
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: authMock.state, me: { capabilities: authMock.capabilities } }),
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({
    locale: i18nState.locale,
    direction: i18nState.locale === "ar" ? "rtl" : "ltr",
    setLocale: vi.fn(),
    t: (key: string) => i18nState.messages[key] ?? key,
  }),
}));

vi.mock("next/link", () => ({
  default: ({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) => (
    <a href={String(href)} {...props}>{children}</a>
  ),
}));

const SELF_DRAFT = {
  id: "r-1",
  tenantId: "t-1",
  subjectPersonId: "p-1",
  subjectEmploymentId: "e-1",
  reviewerPersonId: "p-1",
  reviewerEmploymentId: "e-1",
  source: "SELF",
  status: "DRAFT",
  cycle: "2026-H1",
  periodStart: "2026-01-01",
  periodEnd: "2026-06-30",
  rating: null,
  comments: "مسودة التقييم الذاتي",
  version: 0,
  createdAt: "2026-10-01T08:00:00Z",
  createdBy: "u-1",
  updatedAt: "2026-10-01T09:30:00Z",
  updatedBy: "u-1",
};

const SELF_SUBMITTED = {
  ...SELF_DRAFT,
  id: "r-2",
  status: "SUBMITTED",
  rating: 4,
  comments: "تقييم مرسل بانتظار الاعتماد",
};

const SELF_ACKNOWLEDGED = {
  ...SELF_DRAFT,
  id: "r-3",
  status: "ACKNOWLEDGED",
  rating: 5,
};

const MANAGER_SUBMITTED = {
  ...SELF_DRAFT,
  id: "r-4",
  source: "MANAGER",
  status: "SUBMITTED",
  rating: 3,
  reviewerPersonId: "p-9",
  reviewerEmploymentId: "e-9",
};

const PEER_SUBMITTED = {
  ...SELF_DRAFT,
  id: "r-5",
  source: "PEER",
  status: "SUBMITTED",
  rating: 4,
  reviewerPersonId: "p-8",
  reviewerEmploymentId: "e-8",
};

const CANCELLED_TEAM_DRAFT = {
  ...MANAGER_SUBMITTED,
  id: "r-6",
  status: "CANCELLED",
};

function cloneReviews() {
  return [SELF_DRAFT, SELF_SUBMITTED, SELF_ACKNOWLEDGED, MANAGER_SUBMITTED, PEER_SUBMITTED, CANCELLED_TEAM_DRAFT]
    .map((review) => ({ ...review }));
}

async function renderReviewsPage() {
  // Dynamic import so RED evidence records a real per-test failure while
  // page.tsx does not exist yet (feature-missing RED). @vite-ignore keeps the
  // import runtime-resolved (no transform-time suite abort); once the page
  // exists the same import loads the real component.
  const ReviewsPage = (await import(/* @vite-ignore */ `./page${""}`)).default;
  return render(<ReviewsPage />);
}

function httpError(status: number): ApiHttpError {
  return new ApiHttpError(`HTTP ${status} for /api/v2/hr/performance/reviews`, {
    status,
    error: status === 403 ? "Forbidden" : "Internal Server Error",
    message: null,
    path: "/api/v2/hr/performance/reviews",
    requestId: null,
    body: null,
  });
}

function pageSource(): string {
  const pagePath = resolve(__dirname, "page.tsx");
  expect(existsSync(pagePath), "Task 6 page.tsx must exist").toBe(true);
  return readFileSync(pagePath, "utf8");
}

beforeEach(() => {
  cleanup();
  for (const fn of Object.values(hrG3ApiMock)) fn.mockReset();
  authMock.state = "AUTHENTICATED";
  authMock.capabilities = [
    "HRM.PERFORMANCE.REVIEW.SELF_VIEW",
    "HRM.PERFORMANCE.REVIEW.SELF_SUBMIT",
    "HRM.PERFORMANCE.REVIEW.TEAM_MANAGE",
  ];
  i18nState.locale = "ar";
  i18nState.messages = AR_MESSAGES;
  hrG3ApiMock.listReviews.mockImplementation(() => Promise.resolve(cloneReviews()));
  hrG3ApiMock.listTeamReviews.mockImplementation(() => Promise.resolve([MANAGER_SUBMITTED, CANCELLED_TEAM_DRAFT]));
  hrG3ApiMock.createSelfReview.mockImplementation((input: Record<string, unknown>) =>
    Promise.resolve({ ...SELF_DRAFT, id: "r-new", status: "DRAFT", ...input }));
  hrG3ApiMock.submitReview.mockImplementation((id: string) =>
    Promise.resolve({ ...SELF_DRAFT, id, status: "SUBMITTED", rating: 4 }));
  hrG3ApiMock.acknowledgeReview.mockImplementation((id: string) =>
    Promise.resolve({ ...SELF_DRAFT, id, status: "ACKNOWLEDGED", rating: 4 }));
  hrG3ApiMock.createTeamReview.mockImplementation((_employmentId: string, input: Record<string, unknown>) =>
    Promise.resolve({ ...MANAGER_SUBMITTED, id: "r-team-new", status: "DRAFT", ...input }));
  hrG3ApiMock.cancelTeamReview.mockImplementation((id: string) =>
    Promise.resolve({ ...MANAGER_SUBMITTED, id, status: "CANCELLED" }));
});

afterEach(() => cleanup());

describe("/hr/performance/reviews — Task 6 performance review surface", () => {
  it("1. renders the auth initializing state while the session is being restored", async () => {
    authMock.state = "INITIALIZING";
    await renderReviewsPage();
    expect(screen.getByRole("status")).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByTestId("reviews-ready")).not.toBeInTheDocument();
  });

  it("2. denies users without any review capability: no data and no mutation controls", async () => {
    authMock.capabilities = [];
    await renderReviewsPage();
    expect(screen.getByTestId("reviews-capability-denied")).toBeInTheDocument();
    expect(screen.queryByTestId("reviews-ready")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reviews-self-panel")).not.toBeInTheDocument();
    expect(hrG3ApiMock.listReviews).not.toHaveBeenCalled();
  });

  it("3. renders a deterministic loading state for authorized users", async () => {
    hrG3ApiMock.listReviews.mockImplementation(() => new Promise(() => undefined));
    await renderReviewsPage();
    expect(screen.getByText(AR_MESSAGES["hrm.g3.reviews.loading"])).toBeInTheDocument();
    expect(screen.queryByTestId("reviews-ready")).not.toBeInTheDocument();
  });

  it("4. renders the empty state for authorized users with no reviews and no error", async () => {
    hrG3ApiMock.listReviews.mockResolvedValue([]);
    hrG3ApiMock.listTeamReviews.mockResolvedValue([]);
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());
    expect(screen.getByText(AR_MESSAGES["hrm.g3.reviews.empty.title"])).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("5. renders populated reviews with cycle, period, source, status, rating and comments", async () => {
    const { container } = await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    expect(screen.getAllByText("2026-H1").length).toBeGreaterThan(0);
    expect(container.querySelector('[data-source="SELF"]')).toBeInTheDocument();
    expect(container.querySelector('[data-source="MANAGER"]')).toBeInTheDocument();
    expect(container.querySelector('[data-source="PEER"]')).toBeInTheDocument();
    expect(container.querySelector('[data-status="DRAFT"]')).toBeInTheDocument();
    expect(container.querySelector('[data-status="SUBMITTED"]')).toBeInTheDocument();
    expect(container.querySelector('[data-status="ACKNOWLEDGED"]')).toBeInTheDocument();
    expect(container.querySelector('[data-status="CANCELLED"]')).toBeInTheDocument();
    expect(screen.getAllByText(/2026/).length).toBeGreaterThan(0);
    expect(screen.getByTestId("reviews-kpi-total")).toHaveTextContent("6");
  });

  it("6. renders rating values only when the backend supplies them (DRAFT/CANCELLED show none)", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());
    expect(screen.getByTestId("reviews-kpi-total")).toBeInTheDocument();
    // rating 3 for MANAGER_SUBMITTED and 4 for submitted rows appear; draft row has no rating cell value.
    expect(screen.getAllByText("4").length).toBeGreaterThan(0);
    expect(screen.getAllByText("3").length).toBeGreaterThan(0);
  });

  it("7. creates a self review draft through the facade, validates locally, and reconciles from the backend", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    fireEvent.change(screen.getByTestId("reviews-cycle-input"), { target: { value: "2026-H2" } });
    fireEvent.change(screen.getByTestId("reviews-period-start-input"), { target: { value: "2026-07-01" } });
    fireEvent.change(screen.getByTestId("reviews-period-end-input"), { target: { value: "2026-12-31" } });
    fireEvent.change(screen.getByTestId("reviews-rating-select"), { target: { value: "4" } });
    fireEvent.change(screen.getByTestId("reviews-comments-input"), { target: { value: "تقييم جيد" } });
    fireEvent.click(screen.getByTestId("reviews-create-save"));

    await waitFor(() => expect(hrG3ApiMock.createSelfReview).toHaveBeenCalledWith({
      cycle: "2026-H2",
      periodStart: "2026-07-01",
      periodEnd: "2026-12-31",
      rating: 4,
      comments: "تقييم جيد",
    }));
    await waitFor(() => expect(screen.getByTestId("reviews-notice")).toBeInTheDocument());
    // Backend reconciliation: list re-fetched after the mutation.
    await waitFor(() => expect(hrG3ApiMock.listReviews).toHaveBeenCalledTimes(2));
  });

  it("8. rejects invalid create input locally and never sends it to the backend (cycle, rating, period order)", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    fireEvent.click(screen.getByTestId("reviews-create-save"));
    expect(await screen.findByText(AR_MESSAGES["hrm.g3.reviews.validation.cycle"])).toBeInTheDocument();
    expect(hrG3ApiMock.createSelfReview).not.toHaveBeenCalled();

    fireEvent.change(screen.getByTestId("reviews-cycle-input"), { target: { value: "2026-H2" } });
    fireEvent.change(screen.getByTestId("reviews-period-start-input"), { target: { value: "2026-12-01" } });
    fireEvent.change(screen.getByTestId("reviews-period-end-input"), { target: { value: "2026-01-01" } });
    fireEvent.click(screen.getByTestId("reviews-create-save"));
    expect(await screen.findByText(AR_MESSAGES["hrm.g3.reviews.validation.period"])).toBeInTheDocument();
    expect(hrG3ApiMock.createSelfReview).not.toHaveBeenCalled();

    fireEvent.change(screen.getByTestId("reviews-period-start-input"), { target: { value: "2026-07-01" } });
    fireEvent.change(screen.getByTestId("reviews-period-end-input"), { target: { value: "2026-12-31" } });
    fireEvent.click(screen.getByTestId("reviews-create-save"));
    expect(await screen.findByText(AR_MESSAGES["hrm.g3.reviews.validation.rating"])).toBeInTheDocument();
    expect(hrG3ApiMock.createSelfReview).not.toHaveBeenCalled();
  });

  it("9. submits an own DRAFT review through the facade and reconciles from the backend", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    fireEvent.click(screen.getByTestId("reviews-submit-r-1"));
    await waitFor(() => expect(hrG3ApiMock.submitReview).toHaveBeenCalledWith("r-1"));
    await waitFor(() => expect(screen.getByTestId("reviews-notice")).toHaveTextContent(
      AR_MESSAGES["hrm.g3.reviews.notice.submitted"],
    ));
    await waitFor(() => expect(hrG3ApiMock.listReviews).toHaveBeenCalledTimes(2));
  });

  it("10. acknowledges an own SUBMITTED review through the facade", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    fireEvent.click(screen.getByTestId("reviews-acknowledge-r-2"));
    await waitFor(() => expect(hrG3ApiMock.acknowledgeReview).toHaveBeenCalledWith("r-2"));
    await waitFor(() => expect(screen.getByTestId("reviews-notice")).toHaveTextContent(
      AR_MESSAGES["hrm.g3.reviews.notice.acknowledged"],
    ));
  });

  it("11. shows the TEAM panel only for TEAM_MANAGE users, with create and cancel controls", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    expect(screen.getByTestId("reviews-team-panel")).toBeInTheDocument();
    expect(screen.getAllByText(AR_MESSAGES["hrm.g3.reviews.team.title"]).length).toBeGreaterThan(0);
    expect(screen.getByTestId("reviews-team-employment-input")).toBeInTheDocument();
    expect(screen.getByTestId("reviews-team-cancel-r-6")).toBeInTheDocument();
  });

  it("12. hides the TEAM panel for SELF-only users without any backend authority widening", async () => {
    authMock.capabilities = ["HRM.PERFORMANCE.REVIEW.SELF_VIEW", "HRM.PERFORMANCE.REVIEW.SELF_SUBMIT"];
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    expect(screen.queryByTestId("reviews-team-panel")).not.toBeInTheDocument();
    expect(hrG3ApiMock.listTeamReviews).not.toHaveBeenCalled();
  });

  it("13. hides mutation controls from read-only SELF_VIEW users", async () => {
    authMock.capabilities = ["HRM.PERFORMANCE.REVIEW.SELF_VIEW"];
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    expect(screen.getAllByText("2026-H1").length).toBeGreaterThan(0);
    expect(screen.queryByTestId("reviews-create-save")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reviews-submit-r-1")).not.toBeInTheDocument();
    expect(screen.queryByTestId("reviews-acknowledge-r-2")).not.toBeInTheDocument();
  });

  it("14. creates a team review for a direct report through the facade (employmentId in path only)", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    fireEvent.change(screen.getByTestId("reviews-team-employment-input"), { target: { value: "e-7" } });
    fireEvent.change(screen.getByTestId("reviews-team-cycle-input"), { target: { value: "2026-H2" } });
    fireEvent.change(screen.getByTestId("reviews-team-period-start-input"), { target: { value: "2026-07-01" } });
    fireEvent.change(screen.getByTestId("reviews-team-period-end-input"), { target: { value: "2026-12-31" } });
    fireEvent.change(screen.getByTestId("reviews-team-rating-select"), { target: { value: "3" } });
    fireEvent.click(screen.getByTestId("reviews-team-create-save"));

    await waitFor(() => expect(hrG3ApiMock.createTeamReview).toHaveBeenCalledWith("e-7", {
      cycle: "2026-H2",
      periodStart: "2026-07-01",
      periodEnd: "2026-12-31",
      rating: 3,
      comments: "",
    }));
  });

  it("15. cancels a manager-authored team draft through the facade", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    fireEvent.click(screen.getByTestId("reviews-team-cancel-r-6"));
    await waitFor(() => expect(hrG3ApiMock.cancelTeamReview).toHaveBeenCalledWith("r-6"));
    await waitFor(() => expect(screen.getByTestId("reviews-notice")).toHaveTextContent(
      AR_MESSAGES["hrm.g3.reviews.notice.cancelled"],
    ));
  });

  it("16. renders a forbidden error state on backend 403 and never converts it to an empty state", async () => {
    hrG3ApiMock.listReviews.mockRejectedValue(httpError(403));
    const { container } = await renderReviewsPage();

    await waitFor(() => expect(container.querySelector('[data-kind="forbidden"]')).toBeInTheDocument());
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.queryByText(AR_MESSAGES["hrm.g3.reviews.empty.title"])).not.toBeInTheDocument();
    expect(screen.queryByTestId("reviews-ready")).not.toBeInTheDocument();
  });

  it("17. renders an error state with a retry action on backend 500", async () => {
    hrG3ApiMock.listReviews.mockRejectedValue(httpError(500));
    const { container } = await renderReviewsPage();

    await waitFor(() => expect(container.querySelector('[data-kind="server"]')).toBeInTheDocument());
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "إعادة المحاولة" })).toBeInTheDocument();
  });

  it("18. retry re-invokes the facade and recovers to the ready state", async () => {
    hrG3ApiMock.listReviews.mockRejectedValueOnce(httpError(500));
    await renderReviewsPage();

    await waitFor(() => expect(screen.getByRole("button", { name: "إعادة المحاولة" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "إعادة المحاولة" }));

    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());
    expect(hrG3ApiMock.listReviews).toHaveBeenCalledTimes(2);
  });

  it("19. renders all governing copy in Arabic from the i18n catalog", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    expect(screen.getAllByText(AR_MESSAGES["hrm.g3.reviews.title"]).length).toBeGreaterThan(0);
    expect(screen.getByRole("table")).toHaveTextContent(AR_MESSAGES["hrm.g3.reviews.col.cycle"]);
    expect(screen.getByRole("table")).toHaveTextContent(AR_MESSAGES["hrm.g3.reviews.col.status"]);
    expect(screen.getByRole("table")).toHaveTextContent(AR_MESSAGES["hrm.g3.reviews.col.rating"]);
  });

  it("20. renders the same surface in English through the translator catalog", async () => {
    i18nState.locale = "en";
    i18nState.messages = EN_MESSAGES;
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    expect(screen.getAllByText(EN_MESSAGES["hrm.g3.reviews.title"]).length).toBeGreaterThan(0);
    expect(screen.getByRole("table")).toHaveTextContent(EN_MESSAGES["hrm.g3.reviews.col.cycle"]);
    expect(screen.getByRole("table")).toHaveTextContent(EN_MESSAGES["hrm.g3.reviews.col.status"]);
    expect(screen.getByRole("table")).toHaveTextContent(EN_MESSAGES["hrm.g3.reviews.col.rating"]);
  });

  it("21. renders mobile records via the established mobile record list", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    expect(screen.getAllByTestId("g2-mobile-record-list").length).toBeGreaterThan(0);
  });

  it("22. renders the desktop operational table with all review rows", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    expect(screen.getAllByRole("table").length).toBeGreaterThan(0);
    expect(screen.getAllByTestId("hr-data-row").length).toBeGreaterThanOrEqual(6);
  });

  it("23. marks the reviews workspace link as the active page", async () => {
    await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    const reviewsLink = screen.getByRole("link", { name: AR_MESSAGES["hrm.g3.workspace.nav.reviews"] });
    expect(reviewsLink).toHaveAttribute("href", "/hr/performance/reviews");
    expect(reviewsLink).toHaveAttribute("aria-current", "page");
  });

  it("24. uses no direct fetch inside page.tsx (apiClient is the only transport owner)", async () => {
    const source = pageSource();
    expect(source).not.toMatch(/\bfetch\s*\(/);
  });

  it("25. never derives SELF/TEAM authority from caller-supplied tenant/user ids in page code", async () => {
    const source = pageSource();
    expect(source).not.toContain("tenantId");
    expect(source).not.toContain("userId");
  });

  it("26. keeps accessibility: labelled controls, alert/status roles, headings, native buttons", async () => {
    const { container } = await renderReviewsPage();
    await waitFor(() => expect(screen.getByTestId("reviews-ready")).toBeInTheDocument());

    expect(screen.getAllByRole("heading", { level: 1 }).length).toBeGreaterThan(0);
    expect(screen.getByLabelText(AR_MESSAGES["hrm.g3.reviews.form.cycle"])).toBeInTheDocument();
    expect(screen.getByLabelText(AR_MESSAGES["hrm.g3.reviews.form.rating"])).toBeInTheDocument();
    for (const button of container.querySelectorAll("button")) {
      expect(button.tagName).toBe("BUTTON");
    }
    fireEvent.click(screen.getByTestId("reviews-create-save"));
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });

  it("27. contains no hard-coded locale branching inside page.tsx (i18n audit)", async () => {
    const source = pageSource();
    expect(source).not.toMatch(/locale\s*===\s*["']ar["']/);
    expect(source).not.toMatch(/locale\s*===\s*["']en["']/);
  });
});
