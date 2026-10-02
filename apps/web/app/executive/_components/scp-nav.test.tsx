// @vitest-environment jsdom

/**
 * ScpNav — capability state machine tests.
 *
 * Navigation is fail-closed after the backend access-check resolves. The
 * Executive Authorization entry intentionally accepts any of its governed
 * read/manage capabilities so operators are not forced through ROLE.READ when
 * their assignment is limited to overrides or data relationships.
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, act, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const accessCheckMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    accessCheckV2: (...args: unknown[]) => accessCheckMock(...args),
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/executive",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}));

import { ScpAccessProvider } from "./ScpAccess";
import { ScpNav } from "./ScpNav";

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

const FULL_ACCESS = {
  authenticated: true,
  capabilities: {
    "subscription.read": true,
    "catalog.read": true,
    "plan.read": true,
    "entitlement.read": true,
    "usage.read": true,
    "billing.read": true,
    "provisioning.read": true,
    "audit.read": true,
    "PLATFORM.USER.READ": true,
    "PLATFORM.ROLE.READ": true,
    "ROLE.READ": true,
    "AUTHORIZATION.OVERRIDE.MANAGE": true,
    "AUTHORIZATION.RELATIONSHIP.MANAGE": true,
  },
};

beforeEach(() => {
  accessCheckMock.mockReset();
  vi.spyOn(console, "error").mockImplementation(() => undefined);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const ALL_LINK_LABELS = [
  "scp.nav.overview",
  "scp.nav.applications",
  "scp.nav.tenants",
  "scp.nav.subscriptions",
  "scp.nav.plans",
  "controlPlane.users",
  "controlPlane.roles",
  "scp.nav.authorization",
  "scp.nav.entitlements",
  "scp.nav.usage",
  "scp.nav.billing",
  "scp.nav.provisioning",
  "scp.nav.audit",
];

async function settle() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe("ScpNav — capability state machine", () => {
  it("renders optimistically while checking, then filters by explicit capabilities", async () => {
    const pending = deferred<typeof FULL_ACCESS>();
    accessCheckMock.mockReturnValueOnce(pending.promise);

    render(
      <ScpAccessProvider>
        <ScpNav />
      </ScpAccessProvider>,
    );

    expect(accessCheckMock).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("navigation")).toHaveAttribute("aria-busy", "true");
    for (const label of ALL_LINK_LABELS) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }

    await act(async () => {
      pending.resolve(FULL_ACCESS);
    });
    await settle();

    expect(screen.getByRole("navigation")).not.toHaveAttribute("aria-busy");
    for (const label of ALL_LINK_LABELS) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
  });

  it("authorized — hides links whose capability is explicitly false", async () => {
    accessCheckMock.mockResolvedValueOnce({
      authenticated: true,
      capabilities: {
        ...FULL_ACCESS.capabilities,
        "catalog.read": false,
        "billing.read": false,
      },
    });

    render(
      <ScpAccessProvider>
        <ScpNav />
      </ScpAccessProvider>,
    );
    await settle();

    expect(screen.getByText("scp.nav.overview")).toBeInTheDocument();
    expect(screen.queryByText("scp.nav.applications")).not.toBeInTheDocument();
    expect(screen.queryByText("scp.nav.billing")).not.toBeInTheDocument();
    expect(screen.getByText("scp.nav.audit")).toBeInTheDocument();
    expect(screen.queryByText("scp.nav.noAccess")).not.toBeInTheDocument();
  });

  it("authorization navigation accepts any governed authorization capability", async () => {
    accessCheckMock.mockResolvedValueOnce({
      authenticated: true,
      capabilities: { "AUTHORIZATION.RELATIONSHIP.MANAGE": true },
    });

    render(
      <ScpAccessProvider>
        <ScpNav />
      </ScpAccessProvider>,
    );
    await settle();

    expect(screen.getByText("scp.nav.authorization")).toBeInTheDocument();
    expect(screen.queryByText("controlPlane.users")).not.toBeInTheDocument();
    expect(screen.queryByText("controlPlane.roles")).not.toBeInTheDocument();
  });

  it("authorized — fail-closed: a capability missing from the map stays hidden", async () => {
    accessCheckMock.mockResolvedValueOnce({
      authenticated: true,
      capabilities: { "subscription.read": true },
    });

    render(
      <ScpAccessProvider>
        <ScpNav />
      </ScpAccessProvider>,
    );
    await settle();

    expect(screen.getByText("scp.nav.overview")).toBeInTheDocument();
    expect(screen.queryByText("scp.nav.applications")).not.toBeInTheDocument();
    expect(screen.queryByText("scp.nav.plans")).not.toBeInTheDocument();
    expect(screen.queryByText("scp.nav.audit")).not.toBeInTheDocument();
    expect(screen.queryByText("scp.nav.authorization")).not.toBeInTheDocument();
  });

  it("degraded — access-check failure hides all links and shows an explicit error with retry", async () => {
    accessCheckMock.mockRejectedValueOnce(new Error("network down"));

    render(
      <ScpAccessProvider>
        <ScpNav />
      </ScpAccessProvider>,
    );
    await settle();

    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("scp.nav.degraded")).toBeInTheDocument();
    for (const label of ALL_LINK_LABELS) {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    }
  });

  it("degraded — retry re-runs the access check and recovers into authorized", async () => {
    const user = userEvent.setup();
    accessCheckMock
      .mockRejectedValueOnce(new Error("network down"))
      .mockResolvedValueOnce(FULL_ACCESS);

    render(
      <ScpAccessProvider>
        <ScpNav />
      </ScpAccessProvider>,
    );
    await settle();
    expect(screen.getByRole("alert")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "scp.state.retry" }));
    await settle();

    expect(accessCheckMock).toHaveBeenCalledTimes(2);
    expect(screen.getByText("scp.nav.overview")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("unauthorized — authenticated=false renders a notice and no links", async () => {
    accessCheckMock.mockResolvedValueOnce({
      authenticated: false,
      capabilities: {},
    });

    render(
      <ScpAccessProvider>
        <ScpNav />
      </ScpAccessProvider>,
    );
    await settle();

    expect(screen.getByText("scp.nav.unauthorized")).toBeInTheDocument();
    for (const label of ALL_LINK_LABELS) {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    }
  });

  it("authenticated-no-access — authenticated=true with all capabilities false renders the explicit no-access state", async () => {
    accessCheckMock.mockResolvedValueOnce({
      authenticated: true,
      capabilities: {},
    });

    render(
      <ScpAccessProvider>
        <ScpNav />
      </ScpAccessProvider>,
    );
    await settle();

    expect(screen.getByText("scp.nav.noAccess")).toBeInTheDocument();
    expect(screen.queryByText("scp.nav.unauthorized")).not.toBeInTheDocument();
    for (const label of ALL_LINK_LABELS) {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    }
  });

  it("authenticated-no-access — all capabilities explicitly false never map to authentication failure", async () => {
    const allFalse: Record<string, boolean> = {
      "subscription.read": false,
      "catalog.read": false,
      "plan.read": false,
      "entitlement.read": false,
      "usage.read": false,
      "billing.read": false,
      "provisioning.read": false,
      "audit.read": false,
      "PLATFORM.USER.READ": false,
      "PLATFORM.ROLE.READ": false,
      "ROLE.READ": false,
      "AUTHORIZATION.OVERRIDE.MANAGE": false,
      "AUTHORIZATION.RELATIONSHIP.MANAGE": false,
    };
    accessCheckMock.mockResolvedValueOnce({ authenticated: true, capabilities: allFalse });

    render(
      <ScpAccessProvider>
        <ScpNav />
      </ScpAccessProvider>,
    );
    await settle();

    expect(screen.getByText("scp.nav.noAccess")).toBeInTheDocument();
    expect(screen.queryByText("scp.nav.unauthorized")).not.toBeInTheDocument();
    for (const label of ALL_LINK_LABELS) {
      expect(screen.queryByText(label)).not.toBeInTheDocument();
    }
  });
});
