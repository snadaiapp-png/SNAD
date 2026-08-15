/**
 * Website Platform API client — typed wrapper for all /api/v1/websites endpoints.
 */

import { apiClient } from "./client";

export interface WebsiteResponse {
  id: string;
  tenantId: string;
  name: string;
  slug: string;
  status: "DRAFT" | "ACTIVE" | "SUSPENDED" | "ARCHIVED";
  defaultLocale: string;
  isPrimary: boolean;
  themeConfig?: Record<string, unknown>;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface WebsiteSummary {
  totalWebsites: number;
  activeWebsites: number;
  draftWebsites: number;
  suspendedWebsites: number;
  archivedWebsites: number;
  totalPages: number;
  publishedPages: number;
  activeDomains: number;
  verifiedDomains: number;
}

export interface PageResponse {
  id: string;
  websiteId: string;
  title: string;
  slug: string;
  pageType: string;
  status: "DRAFT" | "PUBLISHED" | "UNPUBLISHED" | "ARCHIVED";
  seoTitle?: string;
  seoDescription?: string;
  publishedAt?: string;
  version: number;
}

export interface DomainResponse {
  id: string;
  websiteId: string;
  hostname: string;
  domainType: "CUSTOM" | "DEFAULT_GENERATED";
  verificationStatus: "PENDING" | "VERIFYING" | "VERIFIED" | "FAILED";
  activationStatus: "INACTIVE" | "ACTIVE" | "DISABLED";
  isPrimary: boolean;
  verificationToken?: string;
  verifiedAt?: string;
}

const BASE = "/api/v1/websites";

export const websiteApi = {
  list: () => apiClient.get<WebsiteResponse[]>(BASE),
  summary: () => apiClient.get<WebsiteSummary>(`${BASE}/summary`),
  get: (id: string) => apiClient.get<WebsiteResponse>(`${BASE}/${id}`),
  create: (data: { name: string; slug?: string; defaultLocale?: string }) =>
    apiClient.post<WebsiteResponse>(BASE, data),
  update: (id: string, data: { name?: string; defaultLocale?: string }) =>
    apiClient.put<WebsiteResponse>(`${BASE}/${id}`, data),
  activate: (id: string) => apiClient.post<WebsiteResponse>(`${BASE}/${id}/activate`, {}),
  suspend: (id: string) => apiClient.post<WebsiteResponse>(`${BASE}/${id}/suspend`, {}),
  archive: (id: string) => apiClient.post<WebsiteResponse>(`${BASE}/${id}/archive`, {}),
  setPrimary: (id: string) => apiClient.post<WebsiteResponse>(`${BASE}/${id}/set-primary`, {}),
  listPages: (websiteId: string) => apiClient.get<PageResponse[]>(`${BASE}/${websiteId}/pages`),
  createPage: (websiteId: string, data: { title: string; slug?: string; pageType?: string }) =>
    apiClient.post<PageResponse>(`${BASE}/${websiteId}/pages`, data),
  getPage: (websiteId: string, pageId: string) =>
    apiClient.get<PageResponse>(`${BASE}/${websiteId}/pages/${pageId}`),
  updatePage: (websiteId: string, pageId: string, data: Record<string, unknown>) =>
    apiClient.put<PageResponse>(`${BASE}/${websiteId}/pages/${pageId}`, data),
  publishPage: (websiteId: string, pageId: string) =>
    apiClient.post<PageResponse>(`${BASE}/${websiteId}/pages/${pageId}/publish`, {}),
  unpublishPage: (websiteId: string, pageId: string) =>
    apiClient.post<PageResponse>(`${BASE}/${websiteId}/pages/${pageId}/unpublish`, {}),
  archivePage: (websiteId: string, pageId: string) =>
    apiClient.post<PageResponse>(`${BASE}/${websiteId}/pages/${pageId}/archive`, {}),
  listDomains: (websiteId: string) => apiClient.get<DomainResponse[]>(`${BASE}/${websiteId}/domains`),
  registerDomain: (websiteId: string, data: { hostname: string; verificationMethod?: string }) =>
    apiClient.post<DomainResponse>(`${BASE}/${websiteId}/domains`, data),
  verifyDomain: (websiteId: string, domainId: string, token: string) =>
    apiClient.post<DomainResponse>(`${BASE}/${websiteId}/domains/${domainId}/verify`, { verificationToken: token }),
  activateDomain: (websiteId: string, domainId: string) =>
    apiClient.post<DomainResponse>(`${BASE}/${websiteId}/domains/${domainId}/activate`, {}),
  disableDomain: (websiteId: string, domainId: string) =>
    apiClient.post<DomainResponse>(`${BASE}/${websiteId}/domains/${domainId}/disable`, {}),
  setPrimaryDomain: (websiteId: string, domainId: string) =>
    apiClient.post<DomainResponse>(`${BASE}/${websiteId}/domains/${domainId}/primary`, {}),
};
