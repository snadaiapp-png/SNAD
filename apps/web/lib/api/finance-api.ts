/**
 * Finance API Client
 * ------------------
 * Typed API client for the Finance module.
 * Connects to FinanceController at /api/v1/finance.
 */
import { apiClient } from "./client";

export interface FinanceAccountResponse {
  id: string;
  code: string;
  name: string;
  accountType: string;
  status: string;
  currency: string;
  balance: number | string;
  version: number;
}

export interface FinanceInvoiceResponse {
  id: string;
  invoiceNumber: string;
  customerType: string;
  customerName: string;
  status: string;
  currency: string;
  totalAmount: number | string;
  paidAmount: number | string;
  issueDate: string;
  dueDate: string;
  version: number;
}

export interface FinancePaymentResponse {
  id: string;
  paymentNumber: string;
  paymentMethod: string;
  amount: number | string;
  currency: string;
  status: string;
  paymentDate: string;
  version: number;
}

export interface FinanceQuotaResponse {
  tenantId: string;
  completedPaymentsThisMonth: number;
}

const BASE = "/api/v1/finance";

export const financeApi = {
  // Accounts
  listAccounts: (limit = 50) =>
    apiClient.get<FinanceAccountResponse[]>(`${BASE}/accounts?limit=${limit}`),
  getAccount: (id: string) =>
    apiClient.get<FinanceAccountResponse>(`${BASE}/accounts/${id}`),
  createAccount: (data: {
    code: string; name: string; accountType: string;
    parentAccountId?: string | null; currency: string; description?: string;
  }) => apiClient.post<FinanceAccountResponse>(`${BASE}/accounts`, { body: JSON.stringify(data) }),

  // Invoices
  listInvoices: (limit = 50) =>
    apiClient.get<FinanceInvoiceResponse[]>(`${BASE}/invoices?limit=${limit}`),
  getInvoice: (id: string) =>
    apiClient.get<FinanceInvoiceResponse>(`${BASE}/invoices/${id}`),
  createInvoice: (data: {
    invoiceNumber: string; customerType: string; customerId?: string;
    customerName: string; issueDate?: string; dueDate?: string;
    currency: string; notes?: string;
  }) => apiClient.post<FinanceInvoiceResponse>(`${BASE}/invoices`, { body: JSON.stringify(data) }),

  // Payments
  listPayments: (limit = 50) =>
    apiClient.get<FinancePaymentResponse[]>(`${BASE}/payments?limit=${limit}`),
  getPayment: (id: string) =>
    apiClient.get<FinancePaymentResponse>(`${BASE}/payments/${id}`),
  createPayment: (data: {
    paymentNumber: string; paymentDate?: string; paymentMethod: string;
    amount: number; currency: string; invoiceId?: string; notes?: string;
  }) => apiClient.post<FinancePaymentResponse>(`${BASE}/payments`, { body: JSON.stringify(data) }),

  // Quota
  getQuota: () =>
    apiClient.get<FinanceQuotaResponse>(`${BASE}/quota`),
};
