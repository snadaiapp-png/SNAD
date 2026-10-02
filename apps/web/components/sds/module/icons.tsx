import type { ComponentType } from "react";

/**
 * SNAD shared module icon set — 16x16, stroke="currentColor", strokeWidth 1.5,
 * aria-hidden decorative. Extracted from the CRM reference navigation and
 * generalized for every SNAD module (CRM + HR + future modules).
 *
 * Rules (SNAD Module Visual Contract):
 *   - 16x16 viewBox geometry, consistent stroke weight
 *   - currentColor only (active nav icons inherit the gold accent)
 *   - no emoji, no external icon libraries
 */

function iconProps() {
  return {
    viewBox: "0 0 16 16",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.5,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
    focusable: false as const,
  };
}

/* ==== CRM reference icon set (moved verbatim from crm-shell.tsx) ========= */

export function OverviewIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="2" y="2" width="5" height="5" rx="1" />
      <rect x="9" y="2" width="5" height="3" rx="1" />
      <rect x="9" y="7" width="5" height="7" rx="1" />
      <rect x="2" y="9" width="5" height="5" rx="1" />
    </svg>
  );
}

export function AccountsIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M3 13.5c0-2.2 1.8-4 4-4s4 1.8 4 4" />
      <circle cx="7" cy="5" r="2.5" />
      <path d="M11 5v3M9.5 6.5h3" />
    </svg>
  );
}

export function ContactsIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="2" y="2" width="11" height="12" rx="1.5" />
      <circle cx="7.5" cy="6" r="1.8" />
      <path d="M4 11.5c0-1.9 1.6-3 3.5-3s3.5 1.1 3.5 3" />
      <line x1="13" y1="4.5" x2="14.5" y2="4.5" />
      <line x1="13" y1="7.5" x2="14.5" y2="7.5" />
    </svg>
  );
}

export function LeadsIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M2 2l1.5 11 4.5-2 4.5 2L14 2z" />
      <path d="M5 5h6M5 8h4" />
    </svg>
  );
}

export function PipelinesIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="2" y="3" width="3.5" height="10" rx="0.5" />
      <rect x="6.5" y="3" width="3" height="7" rx="0.5" />
      <rect x="10.5" y="3" width="3.5" height="12" rx="0.5" />
    </svg>
  );
}

export function OpportunitiesIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="8" cy="8" r="6" />
      <circle cx="8" cy="8" r="3.5" />
      <circle cx="8" cy="8" r="1" fill="currentColor" />
    </svg>
  );
}

export function ActivitiesIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="2" y="2" width="12" height="12" rx="1.5" />
      <path d="M5 8l2 2 4-4" />
    </svg>
  );
}

export function TagsIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M2 8l5-5h6v6l-5 5z" />
      <circle cx="10" cy="5" r="1" fill="currentColor" />
    </svg>
  );
}

export function SearchIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="7" cy="7" r="4" />
      <path d="M10 10l3 3" />
    </svg>
  );
}

export function ReportsIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="2" y="9" width="3" height="5" />
      <rect x="6.5" y="5" width="3" height="9" />
      <rect x="11" y="2" width="3" height="12" />
    </svg>
  );
}

export function NotesIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M3 2h8l3 3v9a1 1 0 01-1 1H3a1 1 0 01-1-1V3a1 1 0 011-1z" />
      <path d="M11 2v3h3" />
      <path d="M5 8h6M5 11h4" />
    </svg>
  );
}

export function TasksIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M3 4h10M3 8h10M3 12h6" />
      <rect x="0.5" y="2.5" width="2" height="2" rx="0.3" />
      <rect x="0.5" y="6.5" width="2" height="2" rx="0.3" />
      <rect x="0.5" y="10.5" width="2" height="2" rx="0.3" />
    </svg>
  );
}

export function CasesIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="2" y="1" width="12" height="14" rx="2" />
      <path d="M5 5h6M5 8h6M5 11h3" />
    </svg>
  );
}

export function IntelligenceIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="8" cy="8" r="5.5" />
      <circle cx="8" cy="8" r="2" />
      <path d="M8 2.5V5M8 11v2.5M2.5 8H5M11 8h2.5" />
    </svg>
  );
}

export function ImportsIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M8 2v8M4.5 6.5L8 10l3.5-3.5" />
      <path d="M2 12v1.5A.5.5 0 0 0 2.5 14h11a.5.5 0 0 0 .5-.5V12" />
    </svg>
  );
}

export function CustomFieldsIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="2" y="3" width="12" height="10" rx="1.5" />
      <path d="M2 6h12M5 9h2M5 11h4" />
    </svg>
  );
}

export function ExecutionIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="8" cy="8" r="6" />
      <path d="M8 4v4l3 2" />
    </svg>
  );
}

/* ==== Module-neutral header icons ======================================== */

export function LangIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="8" cy="8" r="5.5" />
      <path d="M2.5 8h11M8 2.5c1.5 1.6 2.2 3.6 2.2 5.5S9.5 12 8 13.5M8 2.5C6.5 4 5.8 6 5.8 7.5S6.5 12 8 13.5" />
    </svg>
  );
}

export function BackIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M10 4l-4 4 4 4" />
      <line x1="6" y1="8" x2="14" y2="8" />
    </svg>
  );
}

export function LogoutIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M6 2H3.5A1.5 1.5 0 0 0 2 3.5v9A1.5 1.5 0 0 0 3.5 14H6" />
      <path d="M10 11l3-3-3-3" />
      <line x1="13" y1="8" x2="6" y2="8" />
    </svg>
  );
}

/* ==== HR module icons (same geometry language) =========================== */

export function PeopleIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="5.5" cy="5" r="2.2" />
      <path d="M1.5 13c0-2.2 1.8-4 4-4s4 1.8 4 4" />
      <circle cx="11" cy="5.5" r="1.8" />
      <path d="M10 9.2c2 .3 3.5 1.9 3.5 3.8" />
    </svg>
  );
}

export function OrgIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="6" y="1.5" width="4" height="3" rx="0.6" />
      <rect x="1.5" y="11" width="4" height="3" rx="0.6" />
      <rect x="10.5" y="11" width="4" height="3" rx="0.6" />
      <path d="M8 4.5v3M8 7.5H3.5V11M8 7.5h4.5V11" />
    </svg>
  );
}

export function JobsIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="2" y="5" width="12" height="8.5" rx="1.5" />
      <path d="M5.5 5V3.5A1.5 1.5 0 0 1 7 2h2a1.5 1.5 0 0 1 1.5 1.5V5" />
      <path d="M2 8.5h12" />
    </svg>
  );
}

export function PositionsIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="3" y="2" width="10" height="12" rx="1.5" />
      <circle cx="8" cy="6.5" r="1.8" />
      <path d="M5 12c0-1.7 1.3-3 3-3s3 1.3 3 3" />
    </svg>
  );
}

export function AssignmentsIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M6.5 9.5l3-3" />
      <path d="M4 12a2.5 2.5 0 0 1 0-3.5l1.5-1.5M12 4a2.5 2.5 0 0 1 0 3.5L10.5 9" />
    </svg>
  );
}

export function ComplianceIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M8 1.5l5.5 2v4c0 3.5-2.3 5.9-5.5 7-3.2-1.1-5.5-3.5-5.5-7v-4z" />
      <path d="M5.5 8l1.8 1.8L10.5 6.5" />
    </svg>
  );
}

export function RecruitmentIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M2 2.5h12l-4.5 5.5v4.5l-3 1.5V8z" />
    </svg>
  );
}

export function OnboardingIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M2.5 13.5v-5l3-2 3 2v5" />
      <path d="M8.5 13.5V6l3-2 2 1.5v8z" />
      <path d="M1.5 13.5h13" />
    </svg>
  );
}

export function GoalsIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="8" cy="8" r="6" />
      <circle cx="8" cy="8" r="3" />
      <circle cx="8" cy="8" r="0.5" fill="currentColor" />
    </svg>
  );
}

export function ReviewsIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M8 1.8l1.9 3.8 4.2.6-3 3 .7 4.2L8 11.4l-3.8 2 .7-4.2-3-3 4.2-.6z" />
    </svg>
  );
}

export function AttendanceIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="8" cy="8" r="6" />
      <path d="M8 4.5V8l2.5 1.5" />
    </svg>
  );
}

export function TimesheetIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="2" y="3" width="12" height="11" rx="1.5" />
      <path d="M2 6.5h12M5.5 1.5V4M10.5 1.5V4" />
      <path d="M5.5 10l1.5 1.5 3-3" />
    </svg>
  );
}

export function LeaveIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="8" cy="8" r="3" />
      <path d="M8 1.5V3M8 13v1.5M1.5 8H3M13 8h1.5M3.4 3.4l1 1M11.6 11.6l1 1M12.6 3.4l-1 1M4.4 11.6l-1 1" />
    </svg>
  );
}

export function TeamIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="5" cy="5.5" r="2" />
      <circle cx="11" cy="5.5" r="2" />
      <path d="M1.5 13c0-2 1.5-3.5 3.5-3.5S8.5 11 8.5 13" />
      <path d="M8.5 9.8c.6-.2 1.2-.3 2.5-.3 2 0 3.5 1.5 3.5 3.5" />
    </svg>
  );
}

export function ApprovalsIcon() {
  return (
    <svg {...iconProps()}>
      <circle cx="8" cy="8" r="6" />
      <path d="M5 8.5l2 2 4-4.5" />
    </svg>
  );
}

export function SchedulesIcon() {
  return (
    <svg {...iconProps()}>
      <rect x="2" y="3" width="12" height="11" rx="1.5" />
      <path d="M2 6.5h12M5.5 1.5V4M10.5 1.5V4M5.5 9h2M5.5 11.5h5" />
    </svg>
  );
}

export function AdminIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M2 5h8M12.5 5H14M2 11h3.5M8 11h6" />
      <circle cx="11.5" cy="5" r="1.8" />
      <circle cx="6.5" cy="11" r="1.8" />
    </svg>
  );
}

export function PolicyIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M3 2h8l3 3v9a1 1 0 01-1 1H3a1 1 0 01-1-1V3a1 1 0 011-1z" />
      <path d="M11 2v3h3" />
      <path d="M5 8h6M5 11h4" />
    </svg>
  );
}

export function InboxIcon() {
  return (
    <svg {...iconProps()}>
      <path d="M2 8.5V12a1.5 1.5 0 0 0 1.5 1.5h9A1.5 1.5 0 0 0 14 12V8.5" />
      <path d="M2 8.5L4 2.5h8l2 6h-3.5a2.5 2.5 0 0 1-5 0z" />
    </svg>
  );
}

export function TargetIcon() {
  return GoalsIcon();
}

export type SnadModuleIcon = ComponentType;
