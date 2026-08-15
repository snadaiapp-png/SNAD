/**
 * AI Module API client — typed wrapper for all /api/v1/ai/* endpoints.
 */

import { apiClient } from "./client";

export interface AiAgentResponse {
  id: string;
  code: string;
  name: string;
  status: string;
  provider: string;
  modelName: string;
  version: number;
  versionLock: number;
  createdBy: string;
  advisoryOnly: boolean;
}

export interface AiInferenceResponse {
  id: string;
  agentId: string;
  invokedBy: string;
  status: string;
  advisory: boolean;
  tokensInput: number;
  tokensOutput: number;
  latencyMs: number;
  costCents: number;
  createdAt: string;
}

export interface AiQuotaResponse {
  tenantId: string;
  usedThisMonth: number;
  advisoryOnly: boolean;
}

export interface CreateAgentRequest {
  code: string;
  name: string;
  description?: string;
  provider?: string;
  modelName?: string;
  systemPrompt?: string;
  configuration?: string;
  maxTokens?: number;
  temperature?: number;
}

export interface ExecuteRequest {
  agentId: string;
  input: string;
  correlationId?: string;
  businessEntityType?: string;
  businessEntityId?: string;
}

const BASE = "/api/v1/ai";

export const aiApi = {
  // Agents
  listAgents: (limit = 50) =>
    apiClient.get<AiAgentResponse[]>(`${BASE}/agents?limit=${limit}`),

  getAgent: (id: string) =>
    apiClient.get<AiAgentResponse>(`${BASE}/agents/${id}`),

  createAgent: (data: CreateAgentRequest) =>
    apiClient.post<AiAgentResponse>(`${BASE}/agents`, data),

  activateAgent: (id: string) =>
    apiClient.post<AiAgentResponse>(`${BASE}/agents/${id}/activate`),

  deactivateAgent: (id: string) =>
    apiClient.post<AiAgentResponse>(`${BASE}/agents/${id}/deactivate`),

  archiveAgent: (id: string) =>
    apiClient.post<AiAgentResponse>(`${BASE}/agents/${id}/archive`),

  // Inferences
  listInferences: (limit = 50) =>
    apiClient.get<AiInferenceResponse[]>(`${BASE}/inferences?limit=${limit}`),

  getInference: (id: string) =>
    apiClient.get<AiInferenceResponse>(`${BASE}/inferences/${id}`),

  listAgentInferences: (agentId: string, limit = 50) =>
    apiClient.get<AiInferenceResponse[]>(`${BASE}/agents/${agentId}/inferences?limit=${limit}`),

  // Execution
  execute: (data: ExecuteRequest) =>
    apiClient.post<AiInferenceResponse>(`${BASE}/execute`, data),

  // Quota
  getQuota: () =>
    apiClient.get<AiQuotaResponse>(`${BASE}/quota`),
};
