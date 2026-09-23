import { apiClient } from "./client";

const BASE = "/api/v2/hr";

export interface G2AttendanceRecord {
  id: string;
  employmentId: string;
  recordDate: string;
  clockIn: string | null;
  clockOut: string | null;
  breakMinutes: number | null;
  workedMinutes: number | null;
  source: string;
  state: string;
}

export interface G2Schedule {
  id: string;
  code: string;
  nameAr: string;
  nameEn: string;
  timezone: string;
  shiftStart: string;
  shiftEnd: string;
  breakMinutes: number;
  expectedMinutes: number;
  isOvernight: boolean;
  state: string;
}

export interface G2Timesheet {
  id: string;
  employmentId: string;
  periodStart: string;
  periodEnd: string;
  state: string;
  submittedAt: string | null;
  approverComment: string | null;
}

export interface G2LeaveType {
  id: string;
  code: string;
  nameAr: string;
  nameEn: string;
  isPaid: boolean;
  requiresAttachment: boolean;
  defaultDaysPerYear: number | null;
  state: string;
}

export interface G2LeaveRequest {
  id: string;
  employmentId: string;
  leaveTypeId: string;
  startDate: string;
  endDate: string;
  daysCount: string;
  reason: string | null;
  state: string;
  submittedAt: string | null;
  approvedAt: string | null;
  approverComment: string | null;
}

export interface G2LeaveBalance {
  id: string;
  employmentId: string;
  leaveTypeId: string;
  year: number;
  entitledDays: string;
  usedDays: string;
  pendingDays: string;
  carriedOverDays: string;
}

export interface G2MonthlyAttendanceRow {
  employmentId: string;
  scheduledDays: number | null;
  scheduledMinutes: number | null;
  workedMinutes: number;
  absentDays: number;
  leaveDays: number;
  lateOccurrences: number;
  earlyDepartures: number;
  missingPunches: number;
  attendanceStatus: string;
}

function mutationContext() {
  return { context: { headers: { "Idempotency-Key": crypto.randomUUID() } } };
}

export const hrG2Api = {
  listAttendance: (query?: { startDate?: string; endDate?: string }) =>
    apiClient.get<G2AttendanceRecord[]>(`${BASE}/time/attendance`, { query }),
  clockIn: (recordDate: string) =>
    apiClient.post<G2AttendanceRecord, { recordDate: string }>(
      `${BASE}/time/attendance/clock-in`,
      { recordDate },
      mutationContext(),
    ),
  clockOut: (recordId: string) =>
    apiClient.post<G2AttendanceRecord>(`${BASE}/time/attendance/${recordId}/clock-out`, undefined, mutationContext()),

  listSchedules: () => apiClient.get<G2Schedule[]>(`${BASE}/time/schedules`),
  createSchedule: (body: {
    code: string; nameAr: string; nameEn: string; timezone: string;
    shiftStart: string; shiftEnd: string; breakMinutes: number;
  }) => apiClient.post<{ id: string }, typeof body>(`${BASE}/time/schedules`, body, mutationContext()),

  listTimesheets: (state?: string) =>
    apiClient.get<G2Timesheet[]>(`${BASE}/time/timesheets`, { query: state ? { state } : undefined }),
  submitTimesheet: (id: string) =>
    apiClient.post<void>(`${BASE}/time/timesheets/${id}/submit`, undefined, mutationContext()),
  approveTimesheet: (id: string, comment: string) =>
    apiClient.post<void, { comment: string }>(`${BASE}/time/timesheets/${id}/approve`, { comment }, mutationContext()),
  rejectTimesheet: (id: string, reason: string) =>
    apiClient.post<void, { reason: string }>(`${BASE}/time/timesheets/${id}/reject`, { reason }, mutationContext()),

  listLeaveTypes: () => apiClient.get<G2LeaveType[]>(`${BASE}/leave/types`),
  listLeaveRequests: (state?: string) =>
    apiClient.get<G2LeaveRequest[]>(`${BASE}/leave/requests`, { query: state ? { state } : undefined }),
  listLeaveBalances: () => apiClient.get<G2LeaveBalance[]>(`${BASE}/leave/balances`),
  createLeaveRequest: (body: {
    leaveTypeId: string; startDate: string; endDate: string; reason: string; attachmentUrl?: string;
  }) => apiClient.post<{ requestId: string }, typeof body>(`${BASE}/leave/requests`, body, mutationContext()),
  submitLeaveRequest: (id: string) =>
    apiClient.post<void>(`${BASE}/leave/requests/${id}/submit`, undefined, mutationContext()),
  managerApproveLeave: (id: string, comment: string) =>
    apiClient.post<void, { comment: string }>(`${BASE}/leave/requests/${id}/manager-approve`, { comment }, mutationContext()),
  managerRejectLeave: (id: string, reason: string) =>
    apiClient.post<void, { reason: string }>(`${BASE}/leave/requests/${id}/manager-reject`, { reason }, mutationContext()),
  hrApproveLeave: (id: string, comment: string) =>
    apiClient.post<void, { comment: string }>(`${BASE}/leave/requests/${id}/hr-approve`, { comment }, mutationContext()),
  hrRejectLeave: (id: string, reason: string) =>
    apiClient.post<void, { reason: string }>(`${BASE}/leave/requests/${id}/hr-reject`, { reason }, mutationContext()),

  monthlyAttendanceReport: (year: number, month: number) =>
    apiClient.get<G2MonthlyAttendanceRow[]>(`${BASE}/time/attendance/monthly-report`, { query: { year, month } }),
};
