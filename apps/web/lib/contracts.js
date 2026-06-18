export const API_PROXY_PREFIX = "/api/platform";
export const ORGANIZATIONS_PATH = "/api/v1/organizations";
export const USERS_PATH = "/api/v1/users";

function requireValue(value, name) {
  if (!value || !String(value).trim()) throw new Error(`${name} is required`);
  return String(value).trim();
}

function proxy(path, params = {}) {
  const query = new URLSearchParams({ target: path, ...params });
  return `${API_PROXY_PREFIX}?${query.toString()}`;
}

export function withTenant(path, tenantId) {
  return proxy(path, { tenantId: requireValue(tenantId, "tenantId") });
}

export function organizationCollection() {
  return proxy(ORGANIZATIONS_PATH);
}

export function organizationList(tenantId) {
  return withTenant(ORGANIZATIONS_PATH, tenantId);
}

export function organizationResource(organizationId, tenantId) {
  return withTenant(`${ORGANIZATIONS_PATH}/${requireValue(organizationId, "organizationId")}`, tenantId);
}

export function organizationLifecycle(organizationId, action, tenantId) {
  return withTenant(`${ORGANIZATIONS_PATH}/${requireValue(organizationId, "organizationId")}/${requireValue(action, "action")}`, tenantId);
}

export function membershipCollection(organizationId, tenantId) {
  return withTenant(`${ORGANIZATIONS_PATH}/${requireValue(organizationId, "organizationId")}/memberships`, tenantId);
}

export function membershipLifecycle(organizationId, membershipId, action, tenantId) {
  return withTenant(`${ORGANIZATIONS_PATH}/${requireValue(organizationId, "organizationId")}/memberships/${requireValue(membershipId, "membershipId")}/${requireValue(action, "action")}`, tenantId);
}

export function userCollection(tenantId) {
  return withTenant(USERS_PATH, tenantId);
}

export function userResource(userId, tenantId) {
  return withTenant(`${USERS_PATH}/${requireValue(userId, "userId")}`, tenantId);
}

export function userLifecycle(userId, action, tenantId) {
  return withTenant(`${USERS_PATH}/${requireValue(userId, "userId")}/${requireValue(action, "action")}`, tenantId);
}

export function extractApiMessage(payload, fallback = "Request failed") {
  return payload && typeof payload === "object" && typeof payload.message === "string" && payload.message.trim()
    ? payload.message.trim()
    : fallback;
}
