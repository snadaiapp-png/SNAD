import { apiClient } from "./client";

const root = "/api/v2/crm/intelligence";

export interface IntelligenceScore {
  id: string;
  accountId: string;
  scoreType: string;
  scoreValue: number;
  scoreBand: string;
  confidence: number;
  calculatedAt: string;
  triggerReason: string;
  components: Record<string, unknown>;
}

export interface IntelligenceInsight {
  accountId: string;
  scores: Record<string, { value: number; band: string; calculatedAt: string }>;
  nextBestActions: IntelligenceNba[];
  segments: IntelligenceSegmentMembership[];
  summary: { healthBand?: string; clvTier?: string; riskBand?: string };
}

export interface IntelligenceNba {
  id: string;
  accountId: string;
  actionCode: string;
  description: string;
  confidence: number;
  reasoning: string;
  status: string;
  generatedAt: string;
  expiresAt: string;
  humanConfirmationRequired: boolean;
  resolvedAt: string | null;
  resolvedBy: string | null;
  version: number;
}

export interface IntelligenceSegment {
  id: string;
  segmentCode: string;
  segmentName: string;
  segmentType: string;
  description: string;
  criteria: unknown;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface IntelligenceSegmentMembership {
  id: string;
  accountId: string;
  segmentId: string;
  membershipType: string;
  assignedAt: string;
  active: boolean;
}

export interface ScoreHistoryEntry {
  scoreType: string;
  previousValue: number | null;
  previousBand: string | null;
  newValue: number;
  newBand: string;
  delta: number;
  changedAt: string;
  triggerReason: string;
}

export const crmIntelligenceApi = {
  // Scores
  getScores: (accountId: string) =>
    apiClient.get<IntelligenceScore[]>(`${root}/${accountId}/scores`, { cache: "no-store" }),
  getScore: (accountId: string, scoreType: string) =>
    apiClient.get<IntelligenceScore>(`${root}/${accountId}/scores/${scoreType}`, { cache: "no-store" }),
  getScoreHistory: (accountId: string, scoreType: string) =>
    apiClient.get<ScoreHistoryEntry[]>(`${root}/${accountId}/scores/${scoreType}/history`, { cache: "no-store" }),
  calculateHealth: (accountId: string, indicators: Record<string, unknown>) =>
    apiClient.post<IntelligenceScore, Record<string, unknown>>(`${root}/${accountId}/scores/health`, indicators),
  calculateClv: (accountId: string, indicators: Record<string, unknown>) =>
    apiClient.post<IntelligenceScore, Record<string, unknown>>(`${root}/${accountId}/scores/clv`, indicators),
  predictChurn: (accountId: string, indicators: Record<string, unknown>) =>
    apiClient.post<IntelligenceScore, Record<string, unknown>>(`${root}/${accountId}/scores/churn`, indicators),

  // Insights
  getInsights: (accountId: string) =>
    apiClient.get<IntelligenceInsight>(`${root}/${accountId}/insights`, { cache: "no-store" }),

  // Segments
  getAccountSegments: (accountId: string) =>
    apiClient.get<IntelligenceSegmentMembership[]>(`${root}/${accountId}/segments`, { cache: "no-store" }),
  getAllSegments: () =>
    apiClient.get<IntelligenceSegment[]>(`${root}/segments`, { cache: "no-store" }),
  createSegment: (data: { segmentCode: string; segmentName: string; segmentType: string; description?: string }) =>
    apiClient.post<IntelligenceSegment, typeof data>(`${root}/segments`, data),
  addToSegment: (segmentId: string, accountId: string) =>
    apiClient.post<IntelligenceSegmentMembership, { accountId: string }>(`${root}/segments/${segmentId}/members`, { accountId }),
  removeFromSegment: (segmentId: string, accountId: string) =>
    apiClient.delete<void>(`${root}/segments/${segmentId}/members/${accountId}`),

  // Next Best Actions
  getNba: (accountId: string) =>
    apiClient.get<IntelligenceNba[]>(`${root}/${accountId}/nba`, { cache: "no-store" }),
  acceptNba: (actionId: string, expectedVersion: number) =>
    apiClient.post<IntelligenceNba, { expectedVersion: number }>(`${root}/nba/${actionId}/accept`, { expectedVersion }),
  rejectNba: (actionId: string, expectedVersion: number) =>
    apiClient.post<IntelligenceNba, { expectedVersion: number }>(`${root}/nba/${actionId}/reject`, { expectedVersion }),
};
