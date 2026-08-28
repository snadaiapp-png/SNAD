const FALLBACK_DESTINATION = "/workspace";
const KNOWN_DESTINATIONS = ["/workspace", "/crm", "/crm/command-center", "/control-plane", "/executive", "/system-health"] as const;

function normalizeInternalPath(candidate: string): string | null {
  const value = candidate.trim();
  if (!value.startsWith("/") || value.startsWith("//") || value.includes("\\")) return null;
  if (/^[\u0000-\u001F\u007F]/.test(value)) return null;

  try {
    const parsed = new URL(value, "https://snad.invalid");
    if (parsed.origin !== "https://snad.invalid") return null;
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return null;
  }
}

function destinationRoot(path: string): string {
  const parsed = new URL(path, "https://snad.invalid");
  const pathname = parsed.pathname;
  const exact = KNOWN_DESTINATIONS.find((candidate) => pathname === candidate);
  if (exact) return exact;
  if (pathname.startsWith("/crm/")) return "/crm";
  if (pathname.startsWith("/control-plane/")) return "/control-plane";
  if (pathname.startsWith("/workspace/")) return "/workspace";
  return pathname;
}

export function safeReturnUrl(
  candidate: string | null | undefined,
  availableDestinations: readonly string[],
): string | null {
  if (!candidate) return null;
  const normalized = normalizeInternalPath(candidate);
  if (!normalized) return null;

  const root = destinationRoot(normalized);
  const allowed = availableDestinations.length > 0
    ? availableDestinations
    : [FALLBACK_DESTINATION];
  return allowed.includes(root) ? normalized : null;
}

export function resolvePostLoginDestination(input: {
  returnUrl?: string | null;
  defaultDestination?: string | null;
  availableDestinations?: readonly string[];
}): string {
  const available = input.availableDestinations?.length
    ? [...input.availableDestinations]
    : [FALLBACK_DESTINATION];

  const requested = safeReturnUrl(input.returnUrl, available);
  if (requested) return requested;

  // The authenticated workspace is the application hub and therefore the
  // canonical landing page after a normal login. Backend module defaults must
  // not bypass this hub; only an explicit authorized returnUrl may do so.
  return FALLBACK_DESTINATION;
}

export function readReturnUrl(search = typeof window === "undefined" ? "" : window.location.search): string | null {
  return new URLSearchParams(search).get("returnUrl");
}

export const AUTH_PREFETCH_DESTINATIONS = KNOWN_DESTINATIONS;
