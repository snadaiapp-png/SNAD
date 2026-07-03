import { apiClient, ApiClient } from "./client";

export type OrganizationStatus = "ACTIVE" | "INACTIVE" | "ARCHIVED";
export type OrganizationLifecycleAction = "activate" | "deactivate" | "archive";

export interface OrganizationResponse {
  id: string;
  tenantId: string;
  name: string;
  description: string | null;
  status: OrganizationStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateOrganizationRequest {
  tenantId: string;
  name: string;
  description: string | null;
}

export interface UpdateOrganizationRequest {
  name: string;
  description: string | null;
}

function required(value: string, field: string): string {
  const normalized = value.trim();
  if (!normalized) throw new Error(`${field} is required`);
  return normalized;
}

function name(value: string): string {
  const normalized = required(value, "name");
  if (normalized.length > 200) throw new Error("name is too long");
  return normalized;
}

function description(value?: string | null): string | null {
  const normalized = value?.trim() ?? "";
  if (normalized.length > 1000) throw new Error("description is too long");
  return normalized || null;
}

export function createOrganizationsApi(client: ApiClient = apiClient) {
  return {
    list(tenantId: string) {
      return client.get<OrganizationResponse[]>("/api/v1/organizations", {
        query: { tenantId: required(tenantId, "tenantId") },
      });
    },
    create(tenantId: string, input: { name: string; description?: string | null }) {
      const body: CreateOrganizationRequest = {
        tenantId: required(tenantId, "tenantId"),
        name: name(input.name),
        description: description(input.description),
      };
      return client.post<OrganizationResponse, CreateOrganizationRequest>("/api/v1/organizations", body);
    },
    update(tenantId: string, organizationId: string, input: { name: string; description?: string | null }) {
      const body: UpdateOrganizationRequest = {
        name: name(input.name),
        description: description(input.description),
      };
      return client.put<OrganizationResponse, UpdateOrganizationRequest>(`/api/v1/organizations/${required(organizationId, "organizationId")}`, body, {
        query: { tenantId: required(tenantId, "tenantId") },
      });
    },
    transition(tenantId: string, organizationId: string, action: OrganizationLifecycleAction) {
      return client.patch<OrganizationResponse>(`/api/v1/organizations/${required(organizationId, "organizationId")}/${action}`, undefined, {
        query: { tenantId: required(tenantId, "tenantId") },
      });
    },
  };
}

export const organizationsApi = createOrganizationsApi();
