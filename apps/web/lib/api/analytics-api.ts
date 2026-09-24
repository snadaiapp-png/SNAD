import { apiClient } from "./client";

export interface AnalyticsDashboardResponse {
  id: string; code: string; name: string; dashboardType: string; status: string; version: number;
}
export interface AnalyticsReportResponse {
  id: string; code: string; name: string; reportType: string; outputFormat: string; status: string; version: number;
}
export interface AnalyticsDataSourceResponse {
  id: string; code: string; name: string; sourceType: string; status: string; version: number; module: string;
}
const BASE = "/api/v1/analytics";
export const analyticsApi = {
  listDashboards: (limit = 50) => apiClient.get<AnalyticsDashboardResponse[]>(`${BASE}/dashboards?limit=${limit}`),
  getDashboard: (id: string) => apiClient.get<AnalyticsDashboardResponse>(`${BASE}/dashboards/${id}`),
  createDashboard: (data: { code: string; name: string; description?: string; dashboardType?: string }) =>
    apiClient.post<AnalyticsDashboardResponse>(`${BASE}/dashboards`, data),
  activateDashboard: (id: string) => apiClient.post<AnalyticsDashboardResponse>(`${BASE}/dashboards/${id}/activate`),
  deactivateDashboard: (id: string) => apiClient.post<AnalyticsDashboardResponse>(`${BASE}/dashboards/${id}/deactivate`),
  archiveDashboard: (id: string) => apiClient.post<AnalyticsDashboardResponse>(`${BASE}/dashboards/${id}/archive`),
  listReports: (limit = 50) => apiClient.get<AnalyticsReportResponse[]>(`${BASE}/reports?limit=${limit}`),
  getReport: (id: string) => apiClient.get<AnalyticsReportResponse>(`${BASE}/reports/${id}`),
  createReport: (data: { code: string; name: string; description?: string; reportType?: string; queryText?: string }) =>
    apiClient.post<AnalyticsReportResponse>(`${BASE}/reports`, data),
  activateReport: (id: string) => apiClient.post<AnalyticsReportResponse>(`${BASE}/reports/${id}/activate`),
  executeReport: (id: string) => apiClient.post<AnalyticsReportResponse>(`${BASE}/reports/${id}/execute`),
  archiveReport: (id: string) => apiClient.post<AnalyticsReportResponse>(`${BASE}/reports/${id}/archive`),
  listDataSources: (limit = 50) => apiClient.get<AnalyticsDataSourceResponse[]>(`${BASE}/data-sources?limit=${limit}`),
  getDataSource: (id: string) => apiClient.get<AnalyticsDataSourceResponse>(`${BASE}/data-sources/${id}`),
  activateDataSource: (id: string) => apiClient.post<AnalyticsDataSourceResponse>(`${BASE}/data-sources/${id}/activate`),
  deactivateDataSource: (id: string) => apiClient.post<AnalyticsDataSourceResponse>(`${BASE}/data-sources/${id}/deactivate`),
};
