import { apiClient } from "./client";
import type { ApiList, ApiSingle } from "./contact-relationships";

const root = "/api/v2/crm";

export type OwnerType = "ACCOUNT" | "PERSON";
export type AddressType = "REGISTERED" | "BILLING" | "SHIPPING" | "OFFICE" | "HOME" | "OTHER";
export type CommunicationMethodType =
  | "EMAIL" | "PHONE" | "MOBILE" | "FAX" | "WHATSAPP" | "SMS"
  | "MESSAGING_HANDLE" | "WEBSITE" | "OTHER";
export type PrivacyClassification = "PUBLIC" | "INTERNAL" | "CONFIDENTIAL" | "RESTRICTED";
export type VerificationStatus = "UNVERIFIED" | "PENDING" | "VERIFIED" | "FAILED" | "REVOKED";

export interface CrmAddress {
  id: string;
  version: number;
  ownerType: OwnerType;
  ownerId: string;
  addressType: AddressType;
  label?: string | null;
  rawFormattedAddress?: string | null;
  line1: string;
  line2?: string | null;
  line3?: string | null;
  district?: string | null;
  city: string;
  stateRegion?: string | null;
  postalCode?: string | null;
  countryCode: string;
  countryExtensionJson?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  primaryAddress: boolean;
  verified: boolean;
  verificationSource?: string | null;
  status: "ACTIVE" | "INACTIVE" | "ARCHIVED";
  validFrom?: string | null;
  validTo?: string | null;
  createdAt: string;
  updatedAt: string;
  archivedAt?: string | null;
}

export interface CommunicationMethod {
  id: string;
  version: number;
  ownerType: OwnerType;
  ownerId: string;
  methodType: CommunicationMethodType;
  rawValue?: string | null;
  normalizedValue?: string | null;
  displayValue: string;
  label?: string | null;
  preferred: boolean;
  verified: boolean;
  verificationStatus: VerificationStatus;
  verifiedAt?: string | null;
  privacyClassification: PrivacyClassification;
  consentStateReference?: string | null;
  usagePurpose?: string | null;
  status: "ACTIVE" | "INACTIVE" | "ARCHIVED";
  validFrom?: string | null;
  validTo?: string | null;
  createdAt: string;
  updatedAt: string;
  archivedAt?: string | null;
}

export interface AddressInput {
  addressType: AddressType;
  label?: string | null;
  rawFormattedAddress?: string | null;
  line1: string;
  line2?: string | null;
  line3?: string | null;
  district?: string | null;
  city: string;
  stateRegion?: string | null;
  postalCode?: string | null;
  countryCode: string;
  countryExtension?: Record<string, string | number | boolean | null> | null;
  latitude?: number | null;
  longitude?: number | null;
  primaryAddress: boolean;
  verified: boolean;
  verificationSource?: string | null;
  validFrom?: string | null;
  validTo?: string | null;
}

export type AddressUpdateInput = Omit<AddressInput, "primaryAddress">;

export interface CommunicationMethodInput {
  methodType: CommunicationMethodType;
  rawValue: string;
  displayValue?: string | null;
  label?: string | null;
  preferred: boolean;
  privacyClassification: PrivacyClassification;
  consentStateReference?: string | null;
  usagePurpose?: string | null;
  countryHint?: string | null;
  validFrom?: string | null;
  validTo?: string | null;
}

export type CommunicationMethodUpdateInput = Omit<CommunicationMethodInput, "methodType" | "preferred">;

async function etag(entityType: string, id: string, version: number): Promise<string> {
  const material = `${entityType.toLowerCase()}:${id}:${version}`;
  const digest = await globalThis.crypto.subtle.digest("SHA-256", new TextEncoder().encode(material));
  const hex = Array.from(new Uint8Array(digest).slice(0, 8))
    .map((byte) => byte.toString(16).padStart(2, "0")).join("");
  return `"${entityType.toLowerCase()}-${id}-v${version}-${hex}"`;
}

function ownerPath(ownerType: OwnerType, ownerId: string): string {
  return ownerType === "ACCOUNT" ? `accounts/${ownerId}` : `contacts/${ownerId}`;
}

export const addressCommunicationApi = {
  addresses: async (ownerType: OwnerType, ownerId: string): Promise<CrmAddress[]> => {
    const response = await apiClient.get<ApiList<CrmAddress>>(
      `${root}/${ownerPath(ownerType, ownerId)}/addresses`,
      { query: { limit: 200 }, cache: "no-store" },
    );
    return response.data;
  },

  createAddress: async (ownerType: OwnerType, ownerId: string, input: AddressInput): Promise<CrmAddress> => {
    const response = await apiClient.post<ApiSingle<CrmAddress>, AddressInput>(
      `${root}/${ownerPath(ownerType, ownerId)}/addresses`, input,
      { context: { headers: { "Idempotency-Key": globalThis.crypto.randomUUID() } } },
    );
    return response.data;
  },

  updateAddress: async (address: CrmAddress, input: AddressInput): Promise<CrmAddress> => {
    const { primaryAddress, ...payload } = input;
    void primaryAddress;
    const response = await apiClient.patch<ApiSingle<CrmAddress>, AddressUpdateInput>(
      `${root}/addresses/${address.id}`, payload,
      { context: { headers: { "If-Match": await etag("address", address.id, address.version) } } },
    );
    return response.data;
  },

  addressCommand: async (address: CrmAddress, command: "primary" | "archive" | "reactivate"): Promise<CrmAddress> => {
    const response = await apiClient.patch<ApiSingle<CrmAddress>, Record<string, never>>(
      `${root}/addresses/${address.id}/${command}`, {},
      { context: { headers: { "If-Match": await etag("address", address.id, address.version) } } },
    );
    return response.data;
  },

  communicationMethods: async (ownerType: OwnerType, ownerId: string): Promise<CommunicationMethod[]> => {
    const response = await apiClient.get<ApiList<CommunicationMethod>>(
      `${root}/${ownerPath(ownerType, ownerId)}/communication-methods`,
      { query: { limit: 200 }, cache: "no-store" },
    );
    return response.data;
  },

  createCommunicationMethod: async (
    ownerType: OwnerType, ownerId: string, input: CommunicationMethodInput,
  ): Promise<CommunicationMethod> => {
    const response = await apiClient.post<ApiSingle<CommunicationMethod>, CommunicationMethodInput>(
      `${root}/${ownerPath(ownerType, ownerId)}/communication-methods`, input,
      { context: { headers: { "Idempotency-Key": globalThis.crypto.randomUUID() } } },
    );
    return response.data;
  },

  updateCommunicationMethod: async (
    method: CommunicationMethod, input: CommunicationMethodInput,
  ): Promise<CommunicationMethod> => {
    const { methodType, preferred, ...payload } = input;
    void methodType;
    void preferred;
    const response = await apiClient.patch<ApiSingle<CommunicationMethod>, CommunicationMethodUpdateInput>(
      `${root}/communication-methods/${method.id}`, payload,
      { context: { headers: { "If-Match": await etag("communication-method", method.id, method.version) } } },
    );
    return response.data;
  },

  communicationCommand: async (
    method: CommunicationMethod,
    command: "preferred" | "archive" | "reactivate",
  ): Promise<CommunicationMethod> => {
    const response = await apiClient.patch<ApiSingle<CommunicationMethod>, Record<string, never>>(
      `${root}/communication-methods/${method.id}/${command}`, {},
      { context: { headers: { "If-Match": await etag("communication-method", method.id, method.version) } } },
    );
    return response.data;
  },

  verify: async (method: CommunicationMethod, verificationStatus: VerificationStatus): Promise<CommunicationMethod> => {
    const response = await apiClient.patch<ApiSingle<CommunicationMethod>, { verificationStatus: VerificationStatus }>(
      `${root}/communication-methods/${method.id}/verification`, { verificationStatus },
      { context: { headers: { "If-Match": await etag("communication-method", method.id, method.version) } } },
    );
    return response.data;
  },
};
