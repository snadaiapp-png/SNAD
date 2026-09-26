/**
 * HRM G2 leave lifecycle labels.
 * Kept route-scoped through HrI18nAugmenter so workflow states never fall
 * back to raw `hrm.*` keys in Employee/Manager/HR product surfaces.
 */

export const HRM_G2_LIFECYCLE_I18N_AR: Record<string, string> = {
  "hrm.leave.state.DRAFT": "مسودة",
  "hrm.leave.state.SUBMITTED": "مُقدَّم",
  "hrm.leave.state.PENDING_MANAGER": "بانتظار اعتماد المدير",
  "hrm.leave.state.PENDING_HR": "بانتظار اعتماد الموارد البشرية",
  "hrm.leave.state.APPROVED": "موافق عليها",
  "hrm.leave.state.REJECTED": "مرفوضة",
  "hrm.leave.state.WITHDRAWN": "مسحوب",
  "hrm.leave.state.CANCELLED": "ملغاة",
};

export const HRM_G2_LIFECYCLE_I18N_EN: Record<string, string> = {
  "hrm.leave.state.DRAFT": "Draft",
  "hrm.leave.state.SUBMITTED": "Submitted",
  "hrm.leave.state.PENDING_MANAGER": "Pending Manager Approval",
  "hrm.leave.state.PENDING_HR": "Pending HR Approval",
  "hrm.leave.state.APPROVED": "Approved",
  "hrm.leave.state.REJECTED": "Rejected",
  "hrm.leave.state.WITHDRAWN": "Withdrawn",
  "hrm.leave.state.CANCELLED": "Cancelled",
};
