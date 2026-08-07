import { MeResponse } from "../api/auth";

/**
 * Capability-based access control utilities for the frontend.
 *
 * The backend is always authoritative for RBAC enforcement.
 * These checks are "best-effort" UX guards to hide/disable UI elements
 * the user cannot use, reducing confusing 403 errors.
 */

/**
 * Check if the user has a specific capability.
 * Capabilities are populated by the /me endpoint.
 */
export function hasCapability(me: MeResponse | null, capability: string): boolean {
  if (!me?.capabilities) return false;
  return me.capabilities.includes(capability);
}

/**
 * Check if the user has any of the specified capabilities.
 */
export function hasAnyCapability(me: MeResponse | null, capabilities: string[]): boolean {
  if (!me?.capabilities) return false;
  return capabilities.some((cap) => me.capabilities.includes(cap));
}

/**
 * Check if the user has all of the specified capabilities.
 */
export function hasAllCapabilities(me: MeResponse | null, capabilities: string[]): boolean {
  if (!me?.capabilities) return false;
  return capabilities.every((cap) => me.capabilities.includes(cap));
}

// ============================================================
// CRM-specific capability constants
// ============================================================

export const CRM_CAPABILITIES = {
  // Accounts
  ACCOUNT_READ: "CRM.ACCOUNT.READ",
  ACCOUNT_WRITE: "CRM.ACCOUNT.WRITE",
  ACCOUNT_ARCHIVE: "CRM.ACCOUNT.ARCHIVE",

  // Contacts
  CONTACT_READ: "CRM.CONTACT.READ",
  CONTACT_WRITE: "CRM.CONTACT.WRITE",
  CONTACT_ARCHIVE: "CRM.CONTACT.ARCHIVE",

  // Leads
  LEAD_READ: "CRM.LEAD.READ",
  LEAD_WRITE: "CRM.LEAD.WRITE",
  LEAD_CONVERT: "CRM.LEAD.CONVERT",

  // Opportunities
  OPPORTUNITY_READ: "CRM.OPPORTUNITY.READ",
  OPPORTUNITY_WRITE: "CRM.OPPORTUNITY.WRITE",

  // Activities
  ACTIVITY_READ: "CRM.ACTIVITY.READ",
  ACTIVITY_WRITE: "CRM.ACTIVITY.WRITE",

  // Tags
  TAG_READ: "CRM.TAG.READ",
  TAG_WRITE: "CRM.TAG.WRITE",

  // Tasks
  TASK_READ: "CRM.TASK.READ",
  TASK_WRITE: "CRM.TASK.WRITE",

  // Notes
  NOTE_READ: "CRM.NOTE.READ",
  NOTE_WRITE: "CRM.NOTE.WRITE",

  // Cases
  CASE_READ: "CRM.CASE.READ",
  CASE_WRITE: "CRM.CASE.WRITE",

  // Email
  EMAIL_READ: "CRM.EMAIL.READ",
  EMAIL_WRITE: "CRM.EMAIL.WRITE",

  // Reports
  REPORTS_READ: "CRM.REPORTS.READ",

  // Intelligence
  INTELLIGENCE_READ: "CRM.CUSTOMER_INTELLIGENCE.READ",

  // Admin
  ADMIN: "CRM.ADMIN",

  // Custom Fields
  CUSTOM_FIELD_READ: "CRM.CUSTOM_FIELD.READ",
  CUSTOM_FIELD_WRITE: "CRM.CUSTOM_FIELD.WRITE",

  // Import
  IMPORT_READ: "CRM.IMPORT.READ",
  IMPORT_WRITE: "CRM.IMPORT.WRITE",

  // Portal
  PORTAL_READ: "CRM.PORTAL.READ",
  PORTAL_WRITE: "CRM.PORTAL.WRITE",
} as const;
