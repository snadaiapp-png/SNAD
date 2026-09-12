/**
 * R2-I Action/Decision Center — component/contract tests
 *
 * Covers GATE R2.19/R2.20 client behavior:
 *   - loads My Tasks over the authenticated workflow API
 *   - marks a notification read (mutation carries the loaded identity)
 *   - 403 → "Authorization revoked" surfaced, no silent retry
 *   - 409 → "Stale — reload" surfaced; the failed mutation is NEVER
 *     re-sent automatically (only a read-only list re-sync happens)
 *
 * The WorkflowApiClient is faked at the ActionCenterApi seam; SecureStore
 * is mocked so module singletons never touch the native keystore.
 */

import * as SecureStore from 'expo-secure-store';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import {
  ActionCenterScreen,
  ActionCenterApi,
  describeWorkflowApiError,
} from '../screens/ActionCenterScreen';
import { WorkflowApiError } from '../workflow/api';
import { safeNotificationDeepLink } from '../workflow/deep-link';
import { ApprovalDecision, WorkItem, WorkflowNotification } from '../workflow/types';

jest.mock('expo-secure-store', () => ({
  setItemAsync: jest.fn(async () => undefined),
  getItemAsync: jest.fn(async () => null),
  deleteItemAsync: jest.fn(async () => undefined),
}));

const workItem = (overrides: Partial<WorkItem> = {}): WorkItem => ({
  id: 'wi-1',
  workflowInstanceId: 'inst-1',
  workflowStepInstanceId: 'step-1',
  type: 'APPROVAL',
  status: 'ASSIGNED',
  assigneeEmployeeId: 'emp-1',
  claimedByEmployeeId: 'emp-1',
  assignmentMode: 'ASSIGN',
  title: 'Review contract',
  priority: 3,
  dueAt: '',
  version: 7,
  ...overrides,
});

const approval = (overrides: Partial<ApprovalDecision> = {}): ApprovalDecision => ({
  id: 'ap-1',
  workflowInstanceId: 'inst-1',
  workflowStepInstanceId: 'step-9',
  requestedFromUserId: 'user-1',
  requestedFromEmployeeId: 'emp-1',
  status: 'PENDING',
  decision: '',
  comments: '',
  version: 3,
  ...overrides,
});

const notification = (overrides: Partial<WorkflowNotification> = {}): WorkflowNotification => ({
  id: 'n-1',
  event_type: 'WORK_ITEM_DUE',
  workflow_instance_id: 'inst-1',
  work_item_id: 'wi-1',
  external_action_id: null,
  title: 'Payment approved',
  body: 'Deadline approaching',
  deep_link: null,
  priority: 'HIGH',
  read_at: null,
  created_at: '2026-09-12T00:00:00Z',
  ...overrides,
});

function makeApi(overrides: Partial<ActionCenterApi> = {}): ActionCenterApi {
  return {
    listMyTasks: jest.fn().mockResolvedValue([] as WorkItem[]),
    listPool: jest.fn().mockResolvedValue([] as WorkItem[]),
    listPendingApprovals: jest.fn().mockResolvedValue([] as ApprovalDecision[]),
    claimWorkItem: jest.fn(),
    completeWorkItem: jest.fn(),
    approveWorkItem: jest.fn(),
    rejectWorkItem: jest.fn(),
    listNotifications: jest.fn().mockResolvedValue([] as WorkflowNotification[]),
    unreadCount: jest.fn().mockResolvedValue(0),
    markNotificationRead: jest.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

describe('R2-I Action Center', () => {
  beforeEach(() => {
    (SecureStore.setItemAsync as jest.Mock).mockClear();
    (SecureStore.getItemAsync as jest.Mock).mockClear();
    (SecureStore.deleteItemAsync as jest.Mock).mockClear();
  });

  test('loads My Tasks and renders task rows with their optimistic-lock version', async () => {
    const api = makeApi({ listMyTasks: jest.fn().mockResolvedValue([workItem()]) });
    render(<ActionCenterScreen api={api} />);

    expect(await screen.findByText('Review contract')).toBeTruthy();
    expect(screen.getByText(/v7/)).toBeTruthy();
    expect(api.listMyTasks).toHaveBeenCalledTimes(1);
  });

  test('empty My Tasks shows the empty state', async () => {
    const api = makeApi();
    render(<ActionCenterScreen api={api} />);

    expect(await screen.findByText('No tasks assigned to you.')).toBeTruthy();
  });

  test('marks a notification read and re-syncs the feed', async () => {
    const api = makeApi({
      listNotifications: jest
        .fn()
        .mockResolvedValueOnce([notification()])
        .mockResolvedValue([notification({ read_at: '2026-09-12T01:00:00Z' })]),
      unreadCount: jest.fn().mockResolvedValue(1),
      markNotificationRead: jest.fn().mockResolvedValue(undefined),
    });
    render(<ActionCenterScreen api={api} />);

    fireEvent.press(await screen.findByTestId('segment-notifications'));

    fireEvent.press(await screen.findByTestId('notification-mark-read-n-1'));

    await waitFor(() => expect(api.markNotificationRead).toHaveBeenCalledTimes(1));
    expect(api.markNotificationRead).toHaveBeenCalledWith('n-1');
    // Success path re-fetches the authoritative feed (no caching)
    await waitFor(() => expect(api.listNotifications).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Read')).toBeTruthy();
  });

  test('403 surfaces the authorization-revoked message and never retries the mutation', async () => {
    const api = makeApi({ listMyTasks: jest.fn().mockResolvedValue([workItem()]) });
    (api.completeWorkItem as jest.Mock).mockRejectedValueOnce(
      new WorkflowApiError('AUTHORIZATION_REVOKED', 403, 'Authorization revoked — the server refused this action for your account.')
    );
    render(<ActionCenterScreen api={api} />);

    fireEvent.press(await screen.findByTestId('task-complete-wi-1'));

    expect(await screen.findByText(/Authorization revoked/)).toBeTruthy();
    await waitFor(() => expect(api.completeWorkItem).toHaveBeenCalledTimes(1));
    expect(api.completeWorkItem).toHaveBeenCalledWith('wi-1', 7);
  });

  test('409 surfaces "Stale — reload", reloads the list, and does NOT re-send the mutation', async () => {
    const api = makeApi({ listMyTasks: jest.fn().mockResolvedValue([workItem()]) });
    (api.completeWorkItem as jest.Mock).mockRejectedValueOnce(
      new WorkflowApiError('STALE', 409, 'Stale — reload: this item changed on the server.')
    );
    render(<ActionCenterScreen api={api} />);

    fireEvent.press(await screen.findByTestId('task-complete-wi-1'));

    expect(await screen.findByText(/Stale — reload/)).toBeTruthy();
    await waitFor(() => expect(api.listMyTasks).toHaveBeenCalledTimes(2)); // read-only re-sync
    expect(api.completeWorkItem).toHaveBeenCalledTimes(1); // no automatic mutation retry
    expect(api.completeWorkItem).toHaveBeenCalledWith('wi-1', 7); // version came from the loaded object
  });

  test('error state renders when the list itself fails (e.g. 403 on load)', async () => {
    const api = makeApi({
      listMyTasks: jest.fn().mockRejectedValue(
        new WorkflowApiError('AUTHORIZATION_REVOKED', 403, 'Authorization revoked — the server refused this action for your account.')
      ),
    });
    render(<ActionCenterScreen api={api} />);

    expect(await screen.findByText(/Authorization revoked/)).toBeTruthy();
    expect(await screen.findByText('Could not load — pull to refresh.')).toBeTruthy();
  });

  test('completing a task re-fetches the authoritative list on success', async () => {
    const api = makeApi({ listMyTasks: jest.fn().mockResolvedValue([workItem()]) });
    (api.completeWorkItem as jest.Mock).mockResolvedValueOnce(workItem({ status: 'COMPLETED', version: 8 }));
    render(<ActionCenterScreen api={api} />);

    fireEvent.press(await screen.findByTestId('task-complete-wi-1'));

    await waitFor(() => {
      expect(api.completeWorkItem).toHaveBeenCalledWith('wi-1', 7);
      expect(api.listMyTasks).toHaveBeenCalledTimes(2);
    });
  });
});

describe('error contract + deep-link validation', () => {
  test('describeWorkflowApiError maps the contract codes to stable strings', () => {
    expect(
      describeWorkflowApiError(new WorkflowApiError('AUTHORIZATION_REVOKED', 403, 'x'))
    ).toMatch(/Authorization revoked/);
    expect(describeWorkflowApiError(new WorkflowApiError('STALE', 409, 'x'))).toMatch(/Stale — reload/);
    expect(
      describeWorkflowApiError(new WorkflowApiError('UNAUTHENTICATED', 401, 'x'))
    ).toMatch(/Session expired/);
    expect(describeWorkflowApiError(new Error('boom'))).toBe('boom');
  });

  test('notification deep links are honored ONLY over https', () => {
    expect(safeNotificationDeepLink('https://sanad.app/action-center')).toBe(
      'https://sanad.app/action-center'
    );
    expect(safeNotificationDeepLink('http://sanad.app/action-center')).toBeNull();
    expect(safeNotificationDeepLink('sanad://action-center')).toBeNull();
    expect(safeNotificationDeepLink('javascript:alert(1)')).toBeNull();
    expect(safeNotificationDeepLink('')).toBeNull();
    expect(safeNotificationDeepLink(null)).toBeNull();
  });
});
