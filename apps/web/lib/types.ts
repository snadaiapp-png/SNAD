export type OrganizationStatus = "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type MembershipStatus = "ACTIVE" | "INACTIVE" | "INVITED" | "REMOVED";
export type UserStatus = "ACTIVE" | "INACTIVE" | "INVITED" | "SUSPENDED" | "ARCHIVED";

export interface Organization {
  id: string;
  tenantId: string;
  name: string;
  description: string | null;
  status: OrganizationStatus;
  createdAt: string;
  updatedAt: string;
}

export interface Membership {
  id: string;
  tenantId: string;
  organizationId: string;
  email: string;
  displayName: string | null;
  status: MembershipStatus;
  createdAt: string;
  updatedAt: string;
}

export interface User {
  id: string;
  tenantId: string;
  email: string;
  displayName: string | null;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ApiErrorPayload {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
}

export interface OrganizationDraft {
  name: string;
  description: string;
}

export interface MembershipDraft {
  email: string;
  displayName: string;
}

export interface UserDraft {
  email: string;
  displayName: string;
  status: UserStatus;
}
