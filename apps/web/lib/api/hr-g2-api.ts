import { apiClient } from "./client";

const ROOT = "/api/v2/hr";

function mutationOptions() {
  return {
    context: {
      headers: {
        "Idempotency-Key": globalThis.crypto.randomUUID(),
      },
    },
  };
}

export interface G2AttendanceRecord {
  id: string;
  employmentId: string;
  recordDate: string;
  clockIn: string | null;
  clockOut: string | null;
  breakMinutes?: number | null;
  workedMinutes: number | null;
  source?: string;
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
  requiresAttachment?: boolean;
  defaultDaysPerYear?: number | null;
  state?: string;
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
  approvedAt?: string | null;
  approverComment?: string | null;
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

export interface G2MonthlyAttendanceReportRow {
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

export interface G2CreateScheduleRequest {
  code: string;
  nameAr: string;
  nameEn: string;
  timezone: string;
  shiftStart: string;
  shiftEnd: string;
  breakMinutes: number;
}

export interface G2CreateLeaveRequest {
  employmentId: string;
  leaveTypeId: string;
  startDate: string;
  endDate: string;
  reason: string;
  attachmentUrl?: string;
}

export const hrG2Api = {
  listAttendance: (query?: { employmentId?: string; startDate?: string; endDate?: string }) =>
    apiClient.get<G2AttendanceRecord[]>(`${ROOT}/time/attendance`, { query }),

  clockIn: (body: { employmentId: string; recordDate: string }) =>
    apiClient.post<G2AttendanceRecord>(`${ROOT}/time/attendance/clock-in`, body, mutationOptions()),

  clockOut: (recordId: string) =>
    apiClient.post<G2AttendanceRecord>(`${ROOT}/time/attendance/${recordId}/clock-out`, undefined, mutationOptions()),

  listSchedules: () => apiClient.get<G2Schedule[]>(`${ROOT}/time/schedules`),

  createSchedule: (body: G2CreateScheduleRequest) =>
    apiClient.post<{ id: string }>(`${ROOT}/time/schedules`, body, mutationOptions()),

  listTimesheets: (query?: { employmentId?: string; state?: string }) =>
    apiClient.get<G2Timesheet[]>(`${ROOT}/time/timesheets`, { query }),

  submitTimesheet: (timesheetId: string, employmentId: string) =>
    apiClient.post<void>(`${ROOT}/time/timesheets/${timesheetId}/submit`, undefined, {
      ...mutationOptions(),
      query: { employmentId },
    }),

  approveTimesheet: (timesheetId: string, comment: string) =>
    apiClient.post<void>(`${ROOT}/time/timesheets/${timesheetId}/approve`, { comment }, mutationOptions()),

  rejectTimesheet: (timesheetId: string, reason: string) =>
    apiClient.post<void>(`${ROOT}/time/timesheets/${timesheetId}/reject`, { reason }, mutationOptions()),

  listLeaveTypes: () => apiClient.get<G2LeaveType[]>(`${ROOT}/leave/types`),

  listLeaveRequests: (query?: { employmentId?: string; state?: string }) =>
    apiClient.get<G2LeaveRequest[]>(`${ROOT}/leave/requests`, { query }),

  listLeaveBalances: (query?: { employmentId?: string; year?: number }) =>
    apiClient.get<G2LeaveBalance[]>(`${ROOT}/leave/balances`, { query }),

  createLeaveRequest: (body: G2CreateLeaveRequest) =>
    apiClient.post<{ requestId: string }>(`${ROOT}/leave/requests`, body, mutationOptions()),

  submitLeaveRequest: (requestId: string) =>
    apiClient.post<void>(`${ROOT}/leave/requests/${requestId}/submit`, undefined, mutationOptions()),

  managerApproveLeave: (requestId: string, comment: string) =>
    apiClient.post<void>(`${ROOT}/leave/requests/${requestId}/manager-approve`, { comment }, mutationOptions()),

  managerRejectLeave: (requestId: string, reason: string) =>
    apiClient.post<void>(`${ROOT}/leave/requests/${requestId}/manager-reject`, { reason }, mutationOptions()),

  hrApproveLeave: (requestId: string, comment: string) =>
    apiClient.post<void>(`${ROOT}/leave/requests/${requestId}/hr-approve`, { comment }, mutationOptions()),

  hrRejectLeave: (requestId: string, reason: string) =>
    apiClient.post<void>(`${ROOT}/leave/requests/${requestId}/hr-reject`, { reason }, mutationOptions()),

  legacyLeaveDecision: (requestId: string, action: "approve" | "reject", body: { comment?: string; reason?: string }) =>
    apiClient.post<void>(`${ROOT}/leave/requests/${requestId}/${action}`, body, mutationOptions()),

  monthlyAttendanceReport: (year: number, month: number, employmentId?: string) =>
    apiClient.get<G2MonthlyAttendanceReportRow[]>(`${ROOT}/time/attendance/monthly-report`, {
      query: { year, month, employmentId },
    }),
};
