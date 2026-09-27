"use client";

import { Button } from "@/components/sds";
import type { UserLifecycleAction, UserStatus } from "@/lib/api/users";
import type { UsersMessages } from "../users-i18n";

interface UserLifecycleActionsProps {
  status: UserStatus;
  canWrite: boolean;
  canArchive: boolean;
  busy?: boolean;
  messages: UsersMessages;
  onAction: (action: UserLifecycleAction) => void | Promise<void>;
}

export function UserLifecycleActions({
  status,
  canWrite,
  canArchive,
  busy = false,
  messages,
  onAction,
}: UserLifecycleActionsProps) {
  if (!canWrite && !canArchive) return null;

  return (
    <>
      {canWrite && status !== "ACTIVE" && status !== "ARCHIVED" ? (
        <Button size="sm" variant="secondary" disabled={busy} onClick={() => onAction("activate")}>
          {messages.activate}
        </Button>
      ) : null}
      {canWrite && status === "ACTIVE" ? (
        <Button size="sm" variant="secondary" disabled={busy} onClick={() => onAction("deactivate")}>
          {messages.deactivate}
        </Button>
      ) : null}
      {canWrite && status !== "SUSPENDED" && status !== "ARCHIVED" ? (
        <Button size="sm" variant="secondary" disabled={busy} onClick={() => onAction("suspend")}>
          {messages.suspend}
        </Button>
      ) : null}
      {canArchive && status !== "ARCHIVED" ? (
        <Button size="sm" variant="danger" disabled={busy} onClick={() => onAction("archive")}>
          {messages.archive}
        </Button>
      ) : null}
    </>
  );
}
