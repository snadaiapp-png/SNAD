const REFRESH_LOCK_NAME = "snad-auth-refresh";

interface LockManagerLike {
  request<T>(
    name: string,
    options: { mode: "exclusive" },
    callback: () => Promise<T>,
  ): Promise<T>;
}

/**
 * Serialize refresh-token rotation across same-origin browser tabs.
 *
 * The backend intentionally treats reuse of an already-rotated refresh token
 * as a replay attack. A per-React-tree SingleFlight only protects one tab;
 * multiple tabs can still refresh the same HttpOnly cookie concurrently.
 * Web Locks are origin-wide, so the second tab waits until the first refresh
 * has committed the rotated cookie before issuing its own refresh request.
 *
 * Replay protection remains fully authoritative on the backend. Browsers that
 * do not implement Web Locks retain the previous behaviour rather than
 * weakening server-side replay detection.
 */
export async function withCrossTabRefreshLock<T>(operation: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== "undefined"
    ? (navigator as Navigator & { locks?: LockManagerLike }).locks
    : undefined;

  if (!locks || typeof locks.request !== "function") {
    return operation();
  }

  return locks.request(
    REFRESH_LOCK_NAME,
    { mode: "exclusive" },
    operation,
  );
}
