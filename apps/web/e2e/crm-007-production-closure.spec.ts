import { expect, test } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";
import { loginThroughUi } from "./crm-auth-session";

const TENANT_A_EMAIL = process.env.CRM_TENANT_A_EMAIL ?? "";
const TENANT_A_PASSWORD = process.env.CRM_TENANT_A_PASSWORD ?? "";
const TENANT_B_EMAIL = process.env.CRM_TENANT_B_EMAIL ?? "";
const TENANT_B_PASSWORD = process.env.CRM_TENANT_B_PASSWORD ?? "";
const EVIDENCE_FILE = process.env.CRM007_EVIDENCE_FILE ?? "crm-007-production-smoke.json";
const RELEASE_SHA = process.env.CRM_TESTED_SHA ?? "unknown";

test("CRM-007 authenticated lifecycle, conflict, refresh and two-tenant isolation", async ({ browser }) => {
  expect(TENANT_A_EMAIL).toBeTruthy();
  expect(TENANT_A_PASSWORD).toBeTruthy();
  expect(TENANT_B_EMAIL).toBeTruthy();
  expect(TENANT_B_PASSWORD).toBeTruthy();

  const contextA = await browser.newContext();
  const contextB = await browser.newContext();
  const pageA = await contextA.newPage();
  const pageB = await contextB.newPage();

  try {
    const loginA = await loginThroughUi(pageA, TENANT_A_EMAIL, TENANT_A_PASSWORD);
    const loginB = await loginThroughUi(pageB, TENANT_B_EMAIL, TENANT_B_PASSWORD);
    expect(loginA.user.tenantId).not.toBe(loginB.user.tenantId);

    let authA = { Authorization: `Bearer ${loginA.accessToken}` };
    const authB = { Authorization: `Bearer ${loginB.accessToken}` };
    const runId = `${Date.now()}-${randomUUID().slice(0, 8)}`;

    const accountResponse = await pageA.request.post("/api/platform/api/v1/crm/accounts", {
      headers: authA,
      data: {
        displayName: `CRM-007 Closure ${runId}`,
        accountType: "PROSPECT",
        primaryCurrencyCode: "SAR",
        preferredLocale: "ar-SA",
        timeZone: "Asia/Riyadh",
        source: "CRM007_PRODUCTION_CLOSURE",
      },
    });
    expect(accountResponse.ok(), `Account creation failed: ${accountResponse.status()}`).toBe(true);
    const accountBody = await accountResponse.json();
    const accountId = accountBody.id ?? accountBody.data?.id;
    expect(accountId).toBeTruthy();

    const addressPayload = {
      addressType: "OFFICE",
      label: "Production closure",
      line1: `Olaya ${runId}`,
      city: "Riyadh",
      countryCode: "SA",
      primaryAddress: true,
      verified: false,
    };
    const addressIdempotencyKey = randomUUID();
    const addressResponse = await pageA.request.post(
      `/api/platform/api/v2/crm/accounts/${accountId}/addresses`,
      {
        headers: { ...authA, "Idempotency-Key": addressIdempotencyKey },
        data: addressPayload,
      },
    );
    expect(addressResponse.status()).toBe(201);
    const addressBody = await addressResponse.json();
    const addressId = addressBody.data?.id;
    const addressHeaders = addressResponse.headers();
    const addressRepresentationEtag = addressHeaders.etag;
    const addressEtag = addressHeaders["x-snad-entity-tag"];
    expect(addressId).toBeTruthy();
    expect(addressRepresentationEtag).toBeTruthy();
    expect(addressEtag).toBeTruthy();
    expect(addressEtag?.startsWith("W/")).toBe(false);

    const replayResponse = await pageA.request.post(
      `/api/platform/api/v2/crm/accounts/${accountId}/addresses`,
      {
        headers: { ...authA, "Idempotency-Key": addressIdempotencyKey },
        data: addressPayload,
      },
    );
    expect(replayResponse.status()).toBe(201);
    expect((await replayResponse.json()).data?.id).toBe(addressId);
    expect(replayResponse.headers()["x-snad-entity-tag"]).toBe(addressEtag);

    const isolatedAddressRead = await pageB.request.get(
      `/api/platform/api/v2/crm/addresses/${addressId}`,
      { headers: authB },
    );
    expect(isolatedAddressRead.status()).toBe(404);

    const updateResponse = await pageA.request.patch(
      `/api/platform/api/v2/crm/addresses/${addressId}`,
      {
        headers: { ...authA, "X-SNAD-If-Match": addressEtag },
        data: { city: "Jeddah" },
      },
    );
    expect(updateResponse.status()).toBe(200);
    expect((await updateResponse.json()).data?.city).toBe("Jeddah");
    const updatedAddressHeaders = updateResponse.headers();
    const updatedAddressRepresentationEtag = updatedAddressHeaders.etag;
    const updatedAddressEtag = updatedAddressHeaders["x-snad-entity-tag"];
    expect(updatedAddressRepresentationEtag).toBeTruthy();
    expect(updatedAddressEtag).toBeTruthy();
    expect(updatedAddressEtag?.startsWith("W/")).toBe(false);

    const staleUpdateResponse = await pageA.request.patch(
      `/api/platform/api/v2/crm/addresses/${addressId}`,
      {
        headers: { ...authA, "X-SNAD-If-Match": addressEtag },
        data: { city: "Dammam" },
      },
    );
    expect(staleUpdateResponse.status()).toBe(412);

    const communicationValue = `crm007-${runId}@example.test`;
    const communicationPayload = {
      methodType: "EMAIL",
      rawValue: communicationValue,
      preferred: true,
      privacyClassification: "CONFIDENTIAL",
      usagePurpose: "SUPPORT",
    };
    const communicationResponse = await pageA.request.post(
      `/api/platform/api/v2/crm/accounts/${accountId}/communication-methods`,
      {
        headers: { ...authA, "Idempotency-Key": randomUUID() },
        data: communicationPayload,
      },
    );
    expect(communicationResponse.status()).toBe(201);
    const communicationBody = await communicationResponse.json();
    const communicationMethodId = communicationBody.data?.id;
    const communicationHeaders = communicationResponse.headers();
    const communicationRepresentationEtag = communicationHeaders.etag;
    const communicationEtag = communicationHeaders["x-snad-entity-tag"];
    expect(communicationMethodId).toBeTruthy();
    expect(communicationRepresentationEtag).toBeTruthy();
    expect(communicationEtag).toBeTruthy();
    expect(communicationEtag?.startsWith("W/")).toBe(false);

    const duplicateResponse = await pageA.request.post(
      `/api/platform/api/v2/crm/accounts/${accountId}/communication-methods`,
      {
        headers: { ...authA, "Idempotency-Key": randomUUID() },
        data: { ...communicationPayload, rawValue: communicationValue.toUpperCase() },
      },
    );
    expect(duplicateResponse.status()).toBe(409);

    const isolatedCommunicationRead = await pageB.request.get(
      `/api/platform/api/v2/crm/communication-methods/${communicationMethodId}`,
      { headers: authB },
    );
    expect(isolatedCommunicationRead.status()).toBe(404);

    const refreshResponse = await pageA.request.post("/api/platform/api/v1/auth/refresh");
    expect(refreshResponse.ok(), `Refresh failed: ${refreshResponse.status()}`).toBe(true);
    const refreshed = await refreshResponse.json();
    expect(refreshed.accessToken).toBeTruthy();
    const refreshedMe = await pageA.request.get("/api/platform/api/v1/auth/me", {
      headers: { Authorization: `Bearer ${refreshed.accessToken}` },
    });
    expect(refreshedMe.ok()).toBe(true);
    authA = { Authorization: `Bearer ${refreshed.accessToken}` };

    const archiveCommunication = await pageA.request.patch(
      `/api/platform/api/v2/crm/communication-methods/${communicationMethodId}/archive`,
      {
        headers: { ...authA, "X-SNAD-If-Match": communicationEtag },
        data: {},
      },
    );
    expect(archiveCommunication.status()).toBe(200);
    expect((await archiveCommunication.json()).data?.status).toBe("ARCHIVED");

    const archiveAddress = await pageA.request.patch(
      `/api/platform/api/v2/crm/addresses/${addressId}/archive`,
      {
        headers: { ...authA, "X-SNAD-If-Match": updatedAddressEtag },
        data: {},
      },
    );
    expect(archiveAddress.status()).toBe(200);
    expect((await archiveAddress.json()).data?.status).toBe("ARCHIVED");

    const timelineResponse = await pageA.request.get(
      `/api/platform/api/v1/crm/timeline/ACCOUNT/${accountId}`,
      { headers: authA },
    );
    expect(timelineResponse.ok()).toBe(true);
    const timeline = await timelineResponse.json();
    expect(Array.isArray(timeline)).toBe(true);
    expect(timeline.length).toBeGreaterThanOrEqual(4);

    writeFileSync(
      EVIDENCE_FILE,
      JSON.stringify(
        {
          schema: "snad.crm007.production-closure.v1",
          result: "PASS",
          releaseSha: RELEASE_SHA,
          completedAt: new Date().toISOString(),
          tenantAId: loginA.user.tenantId,
          tenantBId: loginB.user.tenantId,
          accountId,
          addressId,
          communicationMethodId,
          checks: {
            authenticatedLogin: "PASS",
            refreshAndMe: "PASS",
            addressCreateIdempotencyUpdateConflictArchive: "PASS",
            communicationCreateDuplicateConflictArchive: "PASS",
            timeline: "PASS",
            twoTenantIsolation: "PASS",
          },
        },
        null,
        2,
      ),
      "utf8",
    );
  } finally {
    await contextA.close();
    await contextB.close();
  }
});
