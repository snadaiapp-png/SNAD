import { describe, expect, it } from "vitest";
import {
  ApiConfigurationError,
  ApiHttpError,
  ApiNetworkError,
  ApiRequestSerializationError,
  ApiResponseParseError,
  ApiTimeoutError,
  isApiClientError,
  isApiHttpError,
  isApiRequestSerializationError,
  isApiTimeoutError,
} from "./errors";

describe("API error model", () => {
  it("keeps stable codes and causes", () => {
    const cause = new Error("root");
    const error = new ApiNetworkError("network", cause);
    expect(error.code).toBe("API_NETWORK_ERROR");
    expect(error.cause).toBe(cause);
    expect(isApiClientError(error)).toBe(true);
  });

  it("exposes HTTP diagnostic fields safely", () => {
    const error = new ApiHttpError("failed", {
      status: 409,
      error: "Conflict",
      message: "duplicate",
      path: "/api/v1/items",
      requestId: "req-1",
      body: null,
    });
    expect(error.status).toBe(409);
    expect(error.requestId).toBe("req-1");
    expect(error.toSafeSummary()).toContain("HTTP 409");
    expect(isApiHttpError(error)).toBe(true);
  });

  it("distinguishes timeout and serialization errors", () => {
    const timeout = new ApiTimeoutError("late", 100);
    const serialization = new ApiRequestSerializationError("invalid");
    expect(isApiTimeoutError(timeout)).toBe(true);
    expect(isApiRequestSerializationError(serialization)).toBe(true);
  });

  it("supports configuration and parsing errors", () => {
    expect(new ApiConfigurationError("missing").code).toBe("API_CONFIGURATION_ERROR");
    expect(new ApiResponseParseError("bad json", 200).statusCode).toBe(200);
  });
});
