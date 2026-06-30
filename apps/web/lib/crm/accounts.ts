import { apiClient } from "@/lib/api/client";

export type CrmAccount = {
  id: string;
  displayName: string;
  accountType: string;
  lifecycleStatus: string;
  primaryCurrencyCode: string | null;
  preferredLocale: string | null;
  timeZone: string | null;
};

export type CreateCrmAccountInput = {
  displayName: string;
  accountType: string;
  primaryCurrencyCode?: string;
  preferredLocale?: string;
  timeZone?: string;
  source?: string;
};

export const listCrmAccounts = () =>
  apiClient.get<CrmAccount[]>("/api/v1/crm/accounts");

export const createCrmAccount = (input: CreateCrmAccountInput) =>
  apiClient.post<CrmAccount, CreateCrmAccountInput>("/api/v1/crm/accounts", input);
