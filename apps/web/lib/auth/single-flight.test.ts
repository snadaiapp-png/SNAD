import { describe, expect, it, vi } from "vitest";
import { SingleFlight } from "./single-flight";

describe("SingleFlight", () => {
  it("shares one operation across concurrent callers", async () => {
    let resolveOperation: ((value: string) => void) | undefined;
    const operation = vi.fn(() => new Promise<string>((resolve) => {
      resolveOperation = resolve;
    }));
    const flight = new SingleFlight<string>();

    const first = flight.run(operation);
    const second = flight.run(operation);
    const third = flight.run(operation);

    expect(operation).toHaveBeenCalledTimes(1);
    expect(flight.active).toBe(true);

    resolveOperation?.("rotated-token");
    await expect(Promise.all([first, second, third])).resolves.toEqual([
      "rotated-token",
      "rotated-token",
      "rotated-token",
    ]);
    expect(flight.active).toBe(false);
  });

  it("releases a failed operation so a later refresh can retry", async () => {
    const operation = vi.fn()
      .mockRejectedValueOnce(new Error("temporary failure"))
      .mockResolvedValueOnce("recovered");
    const flight = new SingleFlight<string>();

    const first = flight.run(operation);
    const second = flight.run(operation);

    await expect(first).rejects.toThrow("temporary failure");
    await expect(second).rejects.toThrow("temporary failure");
    expect(operation).toHaveBeenCalledTimes(1);
    expect(flight.active).toBe(false);

    await expect(flight.run(operation)).resolves.toBe("recovered");
    expect(operation).toHaveBeenCalledTimes(2);
  });

  it("releases a successful operation so the next rotation is independent", async () => {
    const operation = vi.fn()
      .mockResolvedValueOnce("first")
      .mockResolvedValueOnce("second");
    const flight = new SingleFlight<string>();

    await expect(flight.run(operation)).resolves.toBe("first");
    await expect(flight.run(operation)).resolves.toBe("second");
    expect(operation).toHaveBeenCalledTimes(2);
  });
});
