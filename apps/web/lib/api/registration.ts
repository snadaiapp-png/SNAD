import { apiClient, ApiClient } from "./client";

export interface SelfRegistrationRequest {
  displayName: string;
  email: string;
  organizationName: string;
  regionCode: string;
  countryCode: string;
  mobileNumber: string;
  acceptTerms: boolean;
}

export interface SelfRegistrationResponse {
  message: string;
  subdomain: string;
  passwordSetupRequired: boolean;
}

export function createRegistrationApi(client: ApiClient = apiClient) {
  return {
    async register(req: SelfRegistrationRequest): Promise<SelfRegistrationResponse> {
      return client.post<SelfRegistrationResponse, SelfRegistrationRequest>(
        "/api/v1/auth/register",
        req,
      );
    },
  };
}

export const registrationApi = createRegistrationApi();
