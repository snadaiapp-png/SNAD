import { toUserFacingError } from "@/lib/api/user-facing-errors";

/**
 * Normalizes any thrown value into a safe, user-facing Arabic message
 * (R0C-12 Blocker G discipline). SCP pages must never render raw
 * `Error.message` / HTTP internals — everything flows through the shared
 * user-facing error mapper.
 */
export function scpErrorMessage(reason: unknown): string {
  return toUserFacingError(reason).message;
}
