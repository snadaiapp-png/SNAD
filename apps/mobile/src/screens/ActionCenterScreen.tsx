/**
 * R2-I Mobile Action/Decision Center — Action Center screen
 *
 * Segmented view: My Tasks / Pool / Approvals / Notifications.
 *  - My Tasks rows: Complete        (POST /work-items/{id}/complete)
 *  - Pool rows:     Claim           (POST /work-items/{id}/claim)
 *  - Approval rows: Approve/Reject  (POST /approvals/{id}/approve|reject)
 *  - Notification rows: Mark read   (POST /notifications/{id}/read)
 *
 * Contract (GATE R2.19/R2.20): mobile UI state is NEVER authorization.
 *  - Every mutation carries the optimistic-lock `version` of the object
 *    it loaded; the server revalidates assignment + capability.
 *  - 403 → "Authorization revoked" banner (no retry).
 *  - 409 → "Stale — reload" banner; the failed mutation is NOT retried,
 *    only the list is re-fetched (read-only re-sync).
 *  - On success the list is re-fetched so no eligibility is cached.
 */

import * as React from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Linking,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { WorkflowApiClient, WorkflowApiError } from '../workflow/api';
import { safeNotificationDeepLink } from '../workflow/deep-link';
import { ApprovalDecision, WorkItem, WorkflowNotification } from '../workflow/types';

export type ActionCenterSegment = 'MY_TASKS' | 'POOL' | 'APPROVALS' | 'NOTIFICATIONS';

/** Subset of WorkflowApiClient the screen needs (injectable for tests). */
export interface ActionCenterApi {
  listMyTasks(): Promise<WorkItem[]>;
  listPool(): Promise<WorkItem[]>;
  listPendingApprovals(): Promise<ApprovalDecision[]>;
  claimWorkItem(id: string, expectedVersion: number): Promise<WorkItem>;
  completeWorkItem(id: string, expectedVersion: number): Promise<WorkItem>;
  approveWorkItem(id: string, expectedVersion: number): Promise<ApprovalDecision>;
  rejectWorkItem(id: string, expectedVersion: number): Promise<ApprovalDecision>;
  listNotifications(): Promise<WorkflowNotification[]>;
  unreadCount(): Promise<number>;
  markNotificationRead(id: string): Promise<void>;
}

export type ActionCenterListState = 'loading' | 'loaded' | 'error';

export interface ActionCenterBanner {
  kind: 'error' | 'info';
  message: string;
}

export interface ActionCenterScreenProps {
  api?: ActionCenterApi;
  onSignOut?: () => void | Promise<void>;
}

/** Maps WorkflowApiError codes to the exact user-facing contract strings. */
export function describeWorkflowApiError(error: unknown): string {
  if (error instanceof WorkflowApiError) {
    switch (error.code) {
      case 'AUTHORIZATION_REVOKED':
        return 'Authorization revoked — the server refused this action for your account.';
      case 'STALE':
        return 'Stale — reload: this item changed on the server.';
      case 'UNAUTHENTICATED':
        return 'Session expired — sign in again.';
      case 'NETWORK':
        return 'Network error — check your connection and retry.';
      default:
        return `Request failed (HTTP ${error.status}).`;
    }
  }
  return error instanceof Error ? error.message : 'Unexpected error.';
}

const SEGMENTS: Array<{ key: ActionCenterSegment; label: string }> = [
  { key: 'MY_TASKS', label: 'My Tasks' },
  { key: 'POOL', label: 'Pool' },
  { key: 'APPROVALS', label: 'Approvals' },
  { key: 'NOTIFICATIONS', label: 'Notifications' },
];

export function ActionCenterScreen({ api, onSignOut }: ActionCenterScreenProps) {
  const client = useMemo<ActionCenterApi>(() => api ?? new WorkflowApiClient(), [api]);

  const [segment, setSegment] = useState<ActionCenterSegment>('MY_TASKS');
  const [listState, setListState] = useState<ActionCenterListState>('loading');
  const [tasks, setTasks] = useState<WorkItem[]>([]);
  const [pool, setPool] = useState<WorkItem[]>([]);
  const [approvals, setApprovals] = useState<ApprovalDecision[]>([]);
  const [notifications, setNotifications] = useState<WorkflowNotification[]>([]);
  const [unread, setUnread] = useState(0);
  const [banner, setBanner] = useState<ActionCenterBanner | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [actingId, setActingId] = useState<string | null>(null);

  const load = useCallback(
    async (opts: { showSpinner?: boolean } = {}) => {
      if (opts.showSpinner) setListState('loading');
      setRefreshing(true);
      try {
        if (segment === 'MY_TASKS') {
          setTasks(await client.listMyTasks());
        } else if (segment === 'POOL') {
          setPool(await client.listPool());
        } else if (segment === 'APPROVALS') {
          setApprovals(await client.listPendingApprovals());
        } else {
          const [items, count] = await Promise.all([
            client.listNotifications(),
            client.unreadCount(),
          ]);
          setNotifications(items);
          setUnread(count);
        }
        setListState('loaded');
      } catch (err) {
        setListState('error');
        setBanner({ kind: 'error', message: describeWorkflowApiError(err) });
      } finally {
        setRefreshing(false);
      }
    },
    [client, segment]
  );

  useEffect(() => {
    setBanner(null);
    void load({ showSpinner: true });
  }, [load]);

  /**
   * Runs a mutation with the optimistic-lock version of the loaded object.
   * On ANY failure the mutation is never retried automatically; on 409/403
   * only a read-only re-sync happens. On success the list is re-fetched
   * (server revalidation — the client never caches eligibility).
   */
  const runAction = useCallback(
    async (id: string, action: () => Promise<unknown>) => {
      setActingId(id);
      setBanner(null);
      try {
        await action();
        await load();
      } catch (err) {
        const message = describeWorkflowApiError(err);
        setBanner({ kind: 'error', message });
        if (err instanceof WorkflowApiError && (err.code === 'STALE' || err.code === 'AUTHORIZATION_REVOKED')) {
          await load();
        }
      } finally {
        setActingId(null);
      }
    },
    [load]
  );

  const activeLabel =
    segment === 'NOTIFICATIONS' && unread > 0 ? `Notifications (${unread})` : 'Notifications';

  let rows: React.ReactNode = null;
  if (listState === 'loading') {
    rows = (
      <View style={styles.centered} testID="list-loading">
        <ActivityIndicator testID="list-spinner" size="small" />
        <Text style={styles.emptyText}>Loading…</Text>
      </View>
    );
  } else if (listState === 'error') {
    rows = (
      <View style={styles.centered} testID="list-error-state">
        <Text testID="list-error" style={styles.errorText}>
          Could not load — pull to refresh.
        </Text>
      </View>
    );
  } else if (segment === 'MY_TASKS') {
    rows =
      tasks.length === 0 ? (
        <View style={styles.centered} testID="list-empty">
          <Text style={styles.emptyText}>No tasks assigned to you.</Text>
        </View>
      ) : (
        tasks.map((item) => (
          <View key={item.id} testID={`task-row-${item.id}`} style={styles.row}>
            <View style={styles.rowBody}>
              <Text style={styles.rowTitle}>{item.title}</Text>
              <Text style={styles.rowMeta}>
                {item.type} · {item.status} · v{item.version}
              </Text>
            </View>
            <Pressable
              testID={`task-complete-${item.id}`}
              style={[styles.actionButton, actingId === item.id && styles.actionButtonBusy]}
              onPress={() => void runAction(item.id, () => client.completeWorkItem(item.id, item.version))}
              disabled={actingId === item.id}
              accessibilityRole="button"
            >
              <Text style={styles.actionLabel}>Complete</Text>
            </Pressable>
          </View>
        ))
      );
  } else if (segment === 'POOL') {
    rows =
      pool.length === 0 ? (
        <View style={styles.centered} testID="list-empty">
          <Text style={styles.emptyText}>The pool is empty.</Text>
        </View>
      ) : (
        pool.map((item) => (
          <View key={item.id} testID={`task-row-${item.id}`} style={styles.row}>
            <View style={styles.rowBody}>
              <Text style={styles.rowTitle}>{item.title}</Text>
              <Text style={styles.rowMeta}>
                {item.type} · {item.status} · v{item.version}
              </Text>
            </View>
            <Pressable
              testID={`task-claim-${item.id}`}
              style={[styles.actionButton, actingId === item.id && styles.actionButtonBusy]}
              onPress={() => void runAction(item.id, () => client.claimWorkItem(item.id, item.version))}
              disabled={actingId === item.id}
              accessibilityRole="button"
            >
              <Text style={styles.actionLabel}>Claim</Text>
            </Pressable>
          </View>
        ))
      );
  } else if (segment === 'APPROVALS') {
    rows =
      approvals.length === 0 ? (
        <View style={styles.centered} testID="list-empty">
          <Text style={styles.emptyText}>No approvals waiting on you.</Text>
        </View>
      ) : (
        approvals.map((item) => (
          <View key={item.id} testID={`approval-row-${item.id}`} style={styles.row}>
            <View style={styles.rowBody}>
              <Text style={styles.rowTitle}>{item.workflowStepInstanceId}</Text>
              <Text style={styles.rowMeta}>
                Approval · {item.status} · v{item.version}
              </Text>
            </View>
            <View style={styles.rowActions}>
              <Pressable
                testID={`approval-approve-${item.id}`}
                style={[styles.actionButton, actingId === item.id && styles.actionButtonBusy]}
                onPress={() => void runAction(item.id, () => client.approveWorkItem(item.id, item.version))}
                disabled={actingId === item.id}
                accessibilityRole="button"
              >
                <Text style={styles.actionLabel}>Approve</Text>
              </Pressable>
              <Pressable
                testID={`approval-reject-${item.id}`}
                style={[styles.secondaryButton, actingId === item.id && styles.actionButtonBusy]}
                onPress={() => void runAction(item.id, () => client.rejectWorkItem(item.id, item.version))}
                disabled={actingId === item.id}
                accessibilityRole="button"
              >
                <Text style={styles.secondaryLabel}>Reject</Text>
              </Pressable>
            </View>
          </View>
        ))
      );
  } else {
    rows =
      notifications.length === 0 ? (
        <View style={styles.centered} testID="list-empty">
          <Text style={styles.emptyText}>No notifications.</Text>
        </View>
      ) : (
        notifications.map((item) => {
          const safeLink = safeNotificationDeepLink(item.deep_link);
          return (
            <View key={item.id} testID={`notification-row-${item.id}`} style={styles.row}>
              <View style={styles.rowBody}>
                <Text style={[styles.rowTitle, item.read_at ? styles.readTitle : null]}>
                  {item.title}
                </Text>
                {item.body ? <Text style={styles.rowMeta}>{item.body}</Text> : null}
                {safeLink ? (
                  <Text
                    testID={`notification-link-${item.id}`}
                    style={styles.linkText}
                    onPress={() => {
                      void Linking.openURL(safeLink).catch(() => undefined);
                    }}
                  >
                    {safeLink}
                  </Text>
                ) : null}
              </View>
              {!item.read_at ? (
                <Pressable
                  testID={`notification-mark-read-${item.id}`}
                  style={[styles.actionButton, actingId === item.id && styles.actionButtonBusy]}
                  onPress={() => void runAction(item.id, () => client.markNotificationRead(item.id))}
                  disabled={actingId === item.id}
                  accessibilityRole="button"
                >
                  <Text style={styles.actionLabel}>Mark read</Text>
                </Pressable>
              ) : (
                <Text testID={`notification-read-${item.id}`} style={styles.readLabel}>
                  Read
                </Text>
              )}
            </View>
          );
        })
      );
  }

  return (
    <View testID="action-center-screen" style={styles.screen}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Action Center</Text>
        {onSignOut ? (
          <Pressable
            testID="sign-out"
            onPress={() => {
              void onSignOut();
            }}
            accessibilityRole="button"
          >
            <Text style={styles.signOutLabel}>Sign out</Text>
          </Pressable>
        ) : null}
      </View>

      <View style={styles.segmentRow}>
        {SEGMENTS.map((s) => (
          <Pressable
            key={s.key}
            testID={`segment-${s.key.toLowerCase()}`}
            style={[styles.segment, segment === s.key && styles.segmentActive]}
            onPress={() => setSegment(s.key)}
            accessibilityRole="button"
          >
            <Text style={[styles.segmentLabel, segment === s.key && styles.segmentLabelActive]}>
              {s.key === 'NOTIFICATIONS' ? activeLabel : s.label}
            </Text>
          </Pressable>
        ))}
      </View>

      {banner ? (
        <Text
          testID="banner"
          accessibilityRole="alert"
          style={banner.kind === 'error' ? styles.bannerError : styles.bannerInfo}
        >
          {banner.message}
        </Text>
      ) : null}

      <ScrollView
        testID="action-center-scroll"
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={() => void load()} />
        }
      >
        {rows}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f5f6f8' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
  },
  headerTitle: { fontSize: 20, fontWeight: '700', color: '#101828' },
  signOutLabel: { fontSize: 14, color: '#175cd3', fontWeight: '600' },
  segmentRow: {
    flexDirection: 'row',
    paddingHorizontal: 12,
    paddingBottom: 8,
    flexWrap: 'wrap',
  },
  segment: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 16,
    backgroundColor: '#e4e7ec',
    marginRight: 8,
    marginBottom: 4,
  },
  segmentActive: { backgroundColor: '#175cd3' },
  segmentLabel: { fontSize: 13, color: '#344054', fontWeight: '600' },
  segmentLabelActive: { color: '#ffffff' },
  bannerError: {
    marginHorizontal: 12,
    marginBottom: 8,
    padding: 10,
    borderRadius: 8,
    backgroundColor: '#fef3f2',
    color: '#b42318',
    fontSize: 13,
  },
  bannerInfo: {
    marginHorizontal: 12,
    marginBottom: 8,
    padding: 10,
    borderRadius: 8,
    backgroundColor: '#eff8ff',
    color: '#175cd3',
    fontSize: 13,
  },
  scroll: { flex: 1 },
  scrollContent: { paddingHorizontal: 12, paddingBottom: 24 },
  centered: { alignItems: 'center', paddingVertical: 32 },
  emptyText: { color: '#667085', fontSize: 14 },
  errorText: { color: '#b42318', fontSize: 14 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    borderRadius: 8,
    padding: 12,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: '#eaecf0',
  },
  rowBody: { flex: 1, paddingRight: 8 },
  rowTitle: { fontSize: 15, fontWeight: '600', color: '#101828' },
  readTitle: { color: '#98a2b3' },
  rowMeta: { fontSize: 12, color: '#667085', marginTop: 2 },
  linkText: { fontSize: 12, color: '#175cd3', marginTop: 4 },
  rowActions: { flexDirection: 'row', alignItems: 'center' },
  actionButton: {
    backgroundColor: '#175cd3',
    borderRadius: 6,
    paddingVertical: 6,
    paddingHorizontal: 10,
  },
  actionButtonBusy: { opacity: 0.5 },
  actionLabel: { color: '#ffffff', fontSize: 13, fontWeight: '600' },
  secondaryButton: {
    backgroundColor: '#ffffff',
    borderColor: '#b42318',
    borderWidth: 1,
    borderRadius: 6,
    paddingVertical: 6,
    paddingHorizontal: 10,
    marginLeft: 6,
  },
  secondaryLabel: { color: '#b42318', fontSize: 13, fontWeight: '600' },
  readLabel: { fontSize: 12, color: '#98a2b3' },
});
