// @vitest-environment jsdom

/**
 * R0C-12 HUMAN-UAT CORRECTIVE — ScpAccessProvider / useScpAccess contract (RED first).
 *
 * Mission Blocker C: ONE unified authorization source for the console,
 * backed by GET /api/v1/executive/access-check/v2, exposing
 * authenticated / loading / error / has() / hasAll() — FAIL-CLOSED:
 *   checking, degraded, unauthenticated or missing capability ⇒ has() === false.
 * No component may re-implement access-check ad hoc (no duplicate fetches).
 */
import "@testing-library/jest-dom/vitest";
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";

const accessCheckMock = vi.fn();

vi.mock("@/lib/api/scp-api", () => ({
  scpApi: {
    accessCheckV2: (...args: unknown[]) => accessCheckMock(...args),
  },
}));

vi.mock("@/lib/i18n/I18nProvider", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

import { ScpAccessProvider, useScpAccess } from "./ScpAccess";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const ADMIN_MAP = {
  authenticated: true,
  capabilities: {
    "subscription.read": true,
    "subscription.create": true,
    "subscription.change_plan": true,
    "subscription.cancel": true,
    "subscription.suspend": true,
    "entitlement.read": true,
    "entitlement.manage": true,
    "usage.read": true,
    "billing.read": true,
    "provisioning.read": true,
    "provisioning.retry": true,
    "audit.read": true,
  },
};

const READ_ONLY_MAP = {
  authenticated: true,
  capabilities: {
    "subscription.read": true,
    "entitlement.read": true,
    "usage.read": true,
    "billing.read": true,
    "provisioning.read": true,
    "audit.read": true,
  },
};

function Probe({ onProbe }: { onProbe: (value: ReturnType<typeof useScpAccess>) => void }) {
  const value = useScpAccess();
  onProbe(value);
  return (
    <div>
      <span data-testid="phase">{value.phase}</span>
      <span data-testid="has-change-plan">{String(value.has("subscription.change_plan"))}</span>
      <span data-testid="has-retry">{String(value.has("provisioning.retry"))}</span>
      <span data-testid="has-all-manage">
        {String(
          value.hasAll([
            "subscription.create",
            "subscription.change_plan",
            "subscription.cancel",
            "subscription.suspend",
          ]),
        )}
      </span>
    </div>
  );
}

beforeEach(() => {
  accessCheckMock.mockReset();
  vi.spyOn(console, "error").mockImplementation(() => undefined);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

async function settle() {
  const { act } = await import("@testing-library/react");
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe("ScpAccessProvider / useScpAccess — unified fail-closed authorization", () => {
  it("R6: fetches access-check exactly once and exposes the capability map", async () => {
    accessCheckMock.mockResolvedValueOnce(ADMIN_MAP);
    let captured: ReturnType<typeof useScpAccess> | null = null;
    render(
      <ScpAccessProvider>
        <Probe onProbe={(v) => (captured = v)} />
      </ScpAccessProvider>,
    );
    await settle();
    expect(accessCheckMock).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("phase")).toHaveTextContent("authorized");
    expect(screen.getByTestId("has-change-plan")).toHaveTextContent("true");
    expect(screen.getByTestId("has-retry")).toHaveTextContent("true");
    expect(screen.getByTestId("has-all-manage")).toHaveTextContent("true");
    expect(captured).not.toBeNull();
  });

  it("FAIL-CLOSED: while checking, has() is false (no optimistic mutation enablement)", async () => {
    const pending = deferred<typeof ADMIN_MAP>();
    accessCheckMock.mockReturnValueOnce(pending.promise);
    render(
      <ScpAccessProvider>
        <Probe onProbe={() => undefined} />
      </ScpAccessProvider>,
    );
    expect(screen.getByTestId("phase")).toHaveTextContent("checking");
    expect(screen.getByTestId("has-change-plan")).toHaveTextContent("false");
    expect(screen.getByTestId("has-retry")).toHaveTextContent("false");
    expect(screen.getByTestId("has-all-manage")).toHaveTextContent("false");
  });

  it("FAIL-CLOSED: degraded (network failure) never grants any capability", async () => {
    accessCheckMock.mockRejectedValueOnce(new Error("network down"));
    render(
      <ScpAccessProvider>
        <Probe onProbe={() => undefined} />
      </ScpAccessProvider>,
    );
    await settle();
    expect(screen.getByTestId("phase")).toHaveTextContent("degraded");
    expect(screen.getByTestId("has-change-plan")).toHaveTextContent("false");
    expect(screen.getByTestId("has-retry")).toHaveTextContent("false");
  });

  it("FAIL-CLOSED: authenticated=false grants nothing", async () => {
    accessCheckMock.mockResolvedValueOnce({ authenticated: false, capabilities: {} });
    render(
      <ScpAccessProvider>
        <Probe onProbe={() => undefined} />
      </ScpAccessProvider>,
    );
    await settle();
    expect(screen.getByTestId("phase")).toHaveTextContent("unauthorized");
    expect(screen.getByTestId("has-change-plan")).toHaveTextContent("false");
  });

  it("read-only viewer: manage/action capabilities are false even when keys exist as false", async () => {
    accessCheckMock.mockResolvedValueOnce({
      authenticated: true,
      capabilities: { ...READ_ONLY_MAP.capabilities, "subscription.change_plan": false },
    });
    render(
      <ScpAccessProvider>
        <Probe onProbe={() => undefined} />
      </ScpAccessProvider>,
    );
    await settle();
    expect(screen.getByTestId("has-change-plan")).toHaveTextContent("false");
    expect(screen.getByTestId("has-retry")).toHaveTextContent("false");
    expect(screen.getByTestId("has-all-manage")).toHaveTextContent("false");
  });

  it("has() is false for keys missing from the map entirely (fail-closed)", async () => {
    accessCheckMock.mockResolvedValueOnce({
      authenticated: true,
      capabilities: { "subscription.read": true },
    });
    render(
      <ScpAccessProvider>
        <Probe onProbe={() => undefined} />
      </ScpAccessProvider>,
    );
    await settle();
    expect(screen.getByTestId("has-change-plan")).toHaveTextContent("false");
  });
});
