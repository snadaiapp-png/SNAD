import { getLocales } from "expo-localization";
import { environment } from "../config/environment";
import { getAccessToken } from "../auth/secure-session";

export class MobileApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly requestId?: string,
  ) {
    super(message);
    this.name = "MobileApiError";
  }
}

const API_BASE_URL = environment.apiBaseUrl.replace(/\/$/, "");
const REQUEST_TIMEOUT_MS = 20_000;

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  const token = getAccessToken();
  const locale = getLocales()[0]?.languageTag ?? "ar-SA";

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      signal: controller.signal,
      headers: {
        Accept: "application/json",
        "Accept-Language": locale,
        "Content-Type": "application/json",
        "X-SNAD-Client": "mobile",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init.headers,
      },
    });

    const requestId = response.headers.get("x-request-id") ?? undefined;
    const contentType = response.headers.get("content-type") ?? "";
    const body = contentType.includes("application/json")
      ? await response.json()
      : await response.text();

    if (!response.ok) {
      const message =
        typeof body === "object" && body && "message" in body
          ? String(body.message)
          : `Request failed with status ${response.status}`;
      throw new MobileApiError(message, response.status, requestId);
    }

    return body as T;
  } catch (error) {
    if (error instanceof MobileApiError) throw error;
    if (error instanceof Error && error.name === "AbortError") {
      throw new MobileApiError("Request timed out", 408);
    }
    throw new MobileApiError("Network request failed", 0);
  } finally {
    clearTimeout(timeout);
  }
}
