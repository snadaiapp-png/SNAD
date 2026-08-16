import { apiClient } from "./client";

export interface StoreResponse {
  id: string; name: string; slug: string; status: string;
  defaultLocale: string; defaultCurrency: string; isPrimary: boolean;
  version: number; createdAt: string; updatedAt: string;
}
export interface ProductResponse {
  id: string; storeId: string; name: string; slug: string; sku?: string;
  status: string; productType: string; publishedAt?: string; version: number;
}
export interface StoreSummary {
  totalStores: number; activeStores: number; draftStores: number;
  totalProducts: number; publishedProducts: number; totalOrders: number;
}
const BASE = "/api/v1/stores";
export const storeApi = {
  list: () => apiClient.get<StoreResponse[]>(BASE),
  summary: () => apiClient.get<StoreSummary>(`${BASE}/summary`),
  get: (id: string) => apiClient.get<StoreResponse>(`${BASE}/${id}`),
  create: (data: { name: string; slug?: string; defaultLocale?: string; defaultCurrency?: string }) =>
    apiClient.post<StoreResponse>(BASE, data),
  activate: (id: string) => apiClient.post<StoreResponse>(`${BASE}/${id}/activate`, {}),
  suspend: (id: string) => apiClient.post<StoreResponse>(`${BASE}/${id}/suspend`, {}),
  archive: (id: string) => apiClient.post<StoreResponse>(`${BASE}/${id}/archive`, {}),
  listProducts: (storeId: string) => apiClient.get<ProductResponse[]>(`${BASE}/${storeId}/products`),
  createProduct: (storeId: string, data: { name: string; slug?: string; sku?: string; description?: string; productType?: string }) =>
    apiClient.post<ProductResponse>(`${BASE}/${storeId}/products`, data),
  publishProduct: (storeId: string, productId: string) =>
    apiClient.post<ProductResponse>(`${BASE}/${storeId}/products/${productId}/publish`, {}),
};
