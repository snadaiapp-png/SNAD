import {
  extractApiMessage,
  membershipCollection,
  membershipLifecycle,
  organizationCollection,
  organizationLifecycle,
  organizationList,
  organizationResource,
  userCollection,
  userLifecycle,
  userResource,
} from "./contracts.js";
import type {
  ApiErrorPayload,
  Membership,
  MembershipDraft,
  Organization,
  OrganizationDraft,
  User,
  UserDraft,
} from "./types";

export class SanadApiError extends Error {
  readonly status: number;
  readonly payload: ApiErrorPayload | null;

  constructor(status: number, payload: ApiErrorPayload | null) {
    super(extractApiMessage(payload, `Request failed with status ${status}`));
    this.name = "SanadApiError";
    this.status = status;
    this.payload = payload;
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    cache: "no-store",
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });

  const text = await response.text();
  let payload: unknown = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = { message: text };
    }
  }

  if (!response.ok) {
    throw new SanadApiError(response.status, payload as ApiErrorPayload | null);
  }

  return payload as T;
}

export const sanadApi = {
  listOrganizations: (tenantId: string) => request<Organization[]>(organizationList(tenantId)),
  createOrganization: (tenantId: string, draft: OrganizationDraft) =>
    request<Organization>(organizationCollection(), {
      method: "POST",
      body: JSON.stringify({ tenantId, name: draft.name.trim(), description: draft.description.trim() || null }),
    }),
  updateOrganization: (tenantId: string, id: string, draft: OrganizationDraft) =>
    request<Organization>(organizationResource(id, tenantId), {
      method: "PUT",
      body: JSON.stringify({ name: draft.name.trim(), description: draft.description.trim() || null }),
    }),
  transitionOrganization: (tenantId: string, id: string, action: string) =>
    request<Organization>(organizationLifecycle(id, action, tenantId), { method: "PATCH" }),
  listMemberships: (tenantId: string, organizationId: string) =>
    request<Membership[]>(membershipCollection(organizationId, tenantId)),
  inviteMembership: (tenantId: string, organizationId: string, draft: MembershipDraft) =>
    request<Membership>(membershipCollection(organizationId, tenantId), {
      method: "POST",
      body: JSON.stringify({
        tenantId,
        organizationId,
        email: draft.email.trim(),
        displayName: draft.displayName.trim() || null,
      }),
    }),
  transitionMembership: (tenantId: string, organizationId: string, id: string, action: string) =>
    request<Membership>(membershipLifecycle(organizationId, id, action, tenantId), { method: "PATCH" }),
  listUsers: (tenantId: string) => request<User[]>(userCollection(tenantId)),
  createUser: (tenantId: string, draft: UserDraft) =>
    request<User>(userCollection(tenantId), {
      method: "POST",
      body: JSON.stringify({
        email: draft.email.trim(),
        displayName: draft.displayName.trim() || null,
        status: draft.status,
      }),
    }),
  updateUser: (tenantId: string, id: string, draft: UserDraft) =>
    request<User>(userResource(id, tenantId), {
      method: "PUT",
      body: JSON.stringify({ email: draft.email.trim(), displayName: draft.displayName.trim() || null }),
    }),
  transitionUser: (tenantId: string, id: string, action: string) =>
    request<User>(userLifecycle(id, action, tenantId), { method: "PATCH" }),
};
