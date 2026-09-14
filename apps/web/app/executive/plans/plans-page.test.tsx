// @vitest-environment jsdom

/**
 * PlansPage — loading deadlock regression tests.
 *
 * The original defect: the page early-returned a skeleton while `loading`
 * was true, and the hidden <LoadPlans /> component that scheduled the
 * initial request was rendered only *after* that early return — so it never
 * mounted, its effect never ran, and the skeleton stayed on screen forever.
 *
 * These tests pin the corrected architecture: PlansPage itself schedules the
 * initial fetch on mount (useCallback + useEffect) and every terminal state
 * (data / empty / error) is reachable without user interaction.
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, waitFor, act, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiHttpError } from "@/lib/api/errors";

const plansMock = vi.fn();
const createPlanVersionMock = vi.fn();
const hasMock = vi.fn();

vi.mock("@/lib/api/executive-api", () => ({
  executiveApi: {
    plans: (...args: unknown[]) => plansMock(...args),
  },
}));

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    planVersions: vi.fn().mockResolvedValue([]),
    activatePlanVersion: vi.fn(),
    createPlanVersion: (...args: unknown[]) => createPlanVersionMock(...args),
    planVersionPrices: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("@/app/executive/_components/format", () => ({
  useScpFormat: () => ({
    money: (minor: number, currency: string) => `${minor} ${currency}`,
    day: (value: string) => value,
  }),
}));

vi.mock("@/lib/api/client", () => ({
  apiClient: {
    setDefaultHeader: vi.fn(),
    removeDefaultHeader: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("@/lib/auth/auth-provider", () => ({
  useAuth: () => ({ state: "AUTHENTICATED" }),
}));

vi.mock("@/app/executive/_components/ScpAccess", () => ({
  useScpAccess: () => ({
    has: (capability: string) => hasMock(capability),
    hasAll: () => false,
    hasAny: () => false,
  }),
}));

import PlansPage from "./page";

type Deferred<T> = {
  promise: Promise<T>;
  resolve: (value: T) => void;
  reject: (reason: unknown) => void;
};

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const PLAN = {
  id: "plan-1",
  code: "STARTER",
  name: "Starter",
  status: "ACTIVE",
  currencyCode: "SAR",
  monthlyPriceMinor: 9900,
  annualPriceMinor: 99000,
  trialDays: 14,
  maxUsers: 25,
  maxOrganizations: 5,
  storageMb: 51200,
  entitlements: [],
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

beforeEach(() => {
  plansMock.mockReset();
  createPlanVersionMock.mockReset();
  hasMock.mockReset();
  hasMock.mockReturnValue(true);
  vi.spyOn(console, "error").mockImplementation(() => undefined);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("PlansPage — loading deadlock regression", () => {
  it("schedules the initial plans request on first render without any interaction", async () => {
    const pending = deferred<Array<typeof PLAN>>();
    plansMock.mockReturnValueOnce(pending.promise);

    render(<PlansPage />);

    // The request must already be in flight while the skeleton shows —
    // this is exactly the transition the hidden-loader deadlock broke.
    expect(plansMock).toHaveBeenCalledTimes(1);
    expect(plansMock).toHaveBeenCalledWith();

    await act(async () => {
      pending.resolve([PLAN]);
    });
    expect(await screen.findByText("Starter")).toBeInTheDocument();
  });

  it("resolves the loading skeleton on a successful request", async () => {
    plansMock.mockResolvedValueOnce([PLAN]);

    render(<PlansPage />);
    expect(screen.getByRole("status")).toBeInTheDocument(); // skeleton

    expect(await screen.findByText("Starter")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("renders the empty state for an empty catalog", async () => {
    plansMock.mockResolvedValueOnce([]);

    render(<PlansPage />);

    expect(await screen.findByText("scp.state.empty")).toBeInTheDocument();
    expect(screen.queryByText("Starter")).not.toBeInTheDocument();
  });

  it("renders a safe visible error state when the request fails", async () => {
    plansMock.mockRejectedValueOnce(
      new ApiHttpError("Request failed", {
        status: 500,
        error: "Internal Server Error",
        message: "relation saas_plans does not exist",
        path: "/api/v1/executive/plans",
        requestId: "req-plan-safe-error",
        body: null,
      }),
    );

    render(<PlansPage />);

    const alert = await screen.findByRole("alert");
    expect(alert).toBeInTheDocument();
    expect(alert.textContent).not.toMatch(/relation saas_plans|does not exist/i);
  });

  it("retry performs another plans request and recovers into the data state", async () => {
    const user = userEvent.setup();
    plansMock
      .mockRejectedValueOnce(new Error("boom"))
      .mockResolvedValueOnce([PLAN]);

    render(<PlansPage />);
    expect(await screen.findByRole("alert")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "scp.state.retry" }));

    expect(await screen.findByText("Starter")).toBeInTheDocument();
    expect(plansMock).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("does not update state after unmount (no state-update warnings)", async () => {
    const pending = deferred<Array<typeof PLAN>>();
    plansMock.mockReturnValueOnce(pending.promise);

    const { unmount } = render(<PlansPage />);
    expect(plansMock).toHaveBeenCalledTimes(1);

    unmount();
    await act(async () => {
      pending.resolve([PLAN]);
    });

    expect(console.error).not.toHaveBeenCalled();
  });

  it("has no infinite-loading path: a settled request always leaves the skeleton", async () => {
    plansMock.mockResolvedValueOnce([PLAN]);

    render(<PlansPage />);
    await act(async () => {
      await Promise.resolve();
    });
    await waitFor(() => {
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });
    expect(await screen.findByText("Starter")).toBeInTheDocument();
  });

  it("hides plan mutation controls from read-only viewers", async () => {
    hasMock.mockReturnValue(false);
    plansMock.mockResolvedValueOnce([PLAN]);

    render(<PlansPage />);
    expect(await screen.findByText("Starter")).toBeInTheDocument();

    expect(screen.queryByRole("button", { name: "scp.plans.newVersion" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "scp.plans.activate" })).not.toBeInTheDocument();
  });

  it("new plan versions preserve the current plan limits instead of resetting hidden defaults", async () => {
    const user = userEvent.setup();
    hasMock.mockImplementation((capability: string) => capability === "EXECUTIVE_MANAGE");
    plansMock.mockResolvedValueOnce([PLAN]);
    createPlanVersionMock.mockResolvedValueOnce({});

    render(<PlansPage />);
    expect(await screen.findByText("Starter")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "scp.plans.newVersion" }));

    expect(screen.getByLabelText("scp.plans.maxUsers")).toHaveValue(25);
    expect(screen.getByLabelText("scp.plans.maxOrganizations")).toHaveValue(5);
    expect(screen.getByLabelText("scp.plans.storageMb")).toHaveValue(51200);

    await user.click(screen.getByRole("button", { name: "scp.plans.submitVersion" }));

    await waitFor(() => expect(createPlanVersionMock).toHaveBeenCalledWith(
      PLAN.id,
      expect.objectContaining({
        maxUsers: 25,
        maxOrganizations: 5,
        storageMb: 51200,
      }),
    ));
  });

});
