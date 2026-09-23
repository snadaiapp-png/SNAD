import { afterEach, describe, expect, it, vi } from "vitest";
import { withCrossTabRefreshLock } from "./cross-tab-refresh-lock";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("withCrossTabRefreshLock", () => {
  it("serializes concurrent refresh operations through the Web Locks API", async () => {
    let tail = Promise.resolve();
    const request = vi.fn(<T>(_name: string, _options: LockOptions, callback: () => Promise<T>): Promise<T> => {
      const run = tail.then(callback, callback);
      tail = run.then(() => undefined, () => undefined);
      return run;
    });

    Object.defineProperty(globalThis, "navigator", {
      configurable: true,
      value: { locks: { request } },
    });

    let active = 0;
    let maxActive = 0;
    const operation = async () => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      await new Promise((resolve) => setTimeout(resolve, 5));
      active -= 1;
      return maxActive;
    };

    await Promise.all([
      withCrossTabRefreshLock(operation),
      withCrossTabRefreshLock(operation),
      withCrossTabRefreshLock(operation),
    ]);

    expect(request).toHaveBeenCalledTimes(3);
    expect(maxActive).toBe(1);
  });

  it("falls back to the operation when Web Locks is unavailable", async () => {
    Object.defineProperty(globalThis, "navigator", {
      configurable: true,
      value: {},
    });

    const operation = vi.fn(async () => "ok");
    await expect(withCrossTabRefreshLock(operation)).resolves.toBe("ok");
    expect(operation).toHaveBeenCalledTimes(1);
  });
});
