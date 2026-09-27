"use client";

import { Input } from "@/components/sds";
import type { UserLifecycleAction, UserResponse, UserStatus } from "@/lib/api/users";
import { UserLifecycleActions } from "./UserLifecycleActions";
import type { UsersMessages } from "../users-i18n";
import styles from "../users.module.css";

const STATUSES: UserStatus[] = ["ACTIVE", "INACTIVE", "INVITED", "SUSPENDED", "ARCHIVED"];

interface UserDirectoryProps {
  users: UserResponse[];
  query: string;
  status: UserStatus | "ALL";
  canWrite: boolean;
  canArchive: boolean;
  busyUserId: string | null;
  messages: UsersMessages;
  onQueryChange: (value: string) => void;
  onStatusChange: (value: UserStatus | "ALL") => void;
  onLifecycle: (userId: string, action: UserLifecycleAction) => Promise<void>;
}

function statusLabel(messages: UsersMessages, status: UserStatus): string {
  return messages[`status_${status}` as keyof UsersMessages];
}

export function UserDirectory({
  users,
  query,
  status,
  canWrite,
  canArchive,
  busyUserId,
  messages,
  onQueryChange,
  onStatusChange,
  onLifecycle,
}: UserDirectoryProps) {
  const normalizedQuery = query.trim().toLowerCase();
  const filtered = users.filter((user) => {
    const matchesStatus = status === "ALL" || user.status === status;
    const matchesQuery =
      normalizedQuery.length === 0 ||
      user.email.toLowerCase().includes(normalizedQuery) ||
      (user.displayName ?? "").toLowerCase().includes(normalizedQuery) ||
      user.status.toLowerCase().includes(normalizedQuery);
    return matchesStatus && matchesQuery;
  });

  return (
    <>
      <div className={styles.toolbar}>
        <Input
          type="search"
          aria-label={messages.search}
          placeholder={messages.search}
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
        />
        <div className={styles.filterGroup}>
          <label className={styles.label} htmlFor="tenant-user-status-filter">
            {messages.statusFilter}
          </label>
          <select
            id="tenant-user-status-filter"
            className={styles.select}
            aria-label={messages.statusFilter}
            value={status}
            onChange={(event) => onStatusChange(event.target.value as UserStatus | "ALL")}
          >
            <option value="ALL">{messages.allStatuses}</option>
            {STATUSES.map((item) => (
              <option key={item} value={item}>{statusLabel(messages, item)}</option>
            ))}
          </select>
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className={styles.state}>{messages.noMatches}</div>
      ) : (
        <div className={styles.tableScroll}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>{messages.title}</th>
                <th>{messages.status}</th>
                <th>{messages.actions}</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((user) => (
                <tr key={user.id} data-testid={`tenant-user-${user.id}`}>
                  <td>
                    <div className={styles.identity}>
                      <span className={styles.name}>{user.displayName || user.email}</span>
                      <span className={styles.email}>{user.email}</span>
                    </div>
                  </td>
                  <td><span className={styles.status}>{statusLabel(messages, user.status)}</span></td>
                  <td>
                    <div className={styles.actions}>
                      <a className={styles.link} href={`/management/users/${user.id}`}>
                        {messages.open}
                      </a>
                      <UserLifecycleActions
                        status={user.status}
                        canWrite={canWrite}
                        canArchive={canArchive}
                        busy={busyUserId === user.id}
                        messages={messages}
                        onAction={(action) => onLifecycle(user.id, action)}
                      />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
