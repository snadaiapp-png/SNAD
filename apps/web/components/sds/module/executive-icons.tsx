import type { SVGProps } from "react";

/**
 * Executive subset of the shared SNAD module icon geometry.
 * Kept separate so Executive IAM does not pull the full CRM/HR icon catalog
 * into every /executive route. Geometry and visual contract are identical.
 */
const iconProps: SVGProps<SVGSVGElement> = {
  viewBox: "0 0 16 16",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.5,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
  focusable: "false",
};

export function OverviewIcon(){return <svg {...iconProps}><rect x="2" y="2" width="5" height="5" rx="1"/><rect x="9" y="2" width="5" height="3" rx="1"/><rect x="9" y="7" width="5" height="7" rx="1"/><rect x="2" y="9" width="5" height="5" rx="1"/></svg>}
export function CustomFieldsIcon(){return <svg {...iconProps}><rect x="2" y="3" width="12" height="10" rx="1.5"/><path d="M2 6h12M5 9h2M5 11h4"/></svg>}
export function OrgIcon(){return <svg {...iconProps}><rect x="6" y="1.5" width="4" height="3" rx="0.6"/><rect x="1.5" y="11" width="4" height="3" rx="0.6"/><rect x="10.5" y="11" width="4" height="3" rx="0.6"/><path d="M8 4.5v3M8 7.5H3.5V11M8 7.5h4.5V11"/></svg>}
export function PolicyIcon(){return <svg {...iconProps}><path d="M3 2h8l3 3v9a1 1 0 01-1 1H3a1 1 0 01-1-1V3a1 1 0 011-1z"/><path d="M11 2v3h3"/><path d="M5 8h6M5 11h4"/></svg>}
export const NotesIcon = PolicyIcon;
export function PeopleIcon(){return <svg {...iconProps}><circle cx="5.5" cy="5" r="2.2"/><path d="M1.5 13c0-2.2 1.8-4 4-4s4 1.8 4 4"/><circle cx="11" cy="5.5" r="1.8"/><path d="M10 9.2c2 .3 3.5 1.9 3.5 3.8"/></svg>}
export function AdminIcon(){return <svg {...iconProps}><path d="M2 5h8M12.5 5H14M2 11h3.5M8 11h6"/><circle cx="11.5" cy="5" r="1.8"/><circle cx="6.5" cy="11" r="1.8"/></svg>}
export function ComplianceIcon(){return <svg {...iconProps}><path d="M8 1.5l5.5 2v4c0 3.5-2.3 5.9-5.5 7-3.2-1.1-5.5-3.5-5.5-7v-4z"/><path d="M5.5 8l1.8 1.8L10.5 6.5"/></svg>}
export function AssignmentsIcon(){return <svg {...iconProps}><path d="M6.5 9.5l3-3"/><path d="M4 12a2.5 2.5 0 0 1 0-3.5l1.5-1.5M12 4a2.5 2.5 0 0 1 0 3.5L10.5 9"/></svg>}
export function ReportsIcon(){return <svg {...iconProps}><rect x="2" y="9" width="3" height="5"/><rect x="6.5" y="5" width="3" height="9"/><rect x="11" y="2" width="3" height="12"/></svg>}
export function AccountsIcon(){return <svg {...iconProps}><path d="M3 13.5c0-2.2 1.8-4 4-4s4 1.8 4 4"/><circle cx="7" cy="5" r="2.5"/><path d="M11 5v3M9.5 6.5h3"/></svg>}
export function ExecutionIcon(){return <svg {...iconProps}><circle cx="8" cy="8" r="6"/><path d="M8 4v4l3 2"/></svg>}
export function TasksIcon(){return <svg {...iconProps}><path d="M3 4h10M3 8h10M3 12h6"/><rect x="0.5" y="2.5" width="2" height="2" rx="0.3"/><rect x="0.5" y="6.5" width="2" height="2" rx="0.3"/><rect x="0.5" y="10.5" width="2" height="2" rx="0.3"/></svg>}
