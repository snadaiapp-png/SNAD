import { expect, test, type APIRequestContext } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { writeFileSync } from "node:fs";
import { loginThroughUi } from "./crm-auth-session";

const TENANT_A_EMAIL = process.env.CRM_TENANT_A_EMAIL ?? "";
const TENANT_A_PASSWORD = process.env.CRM_TENANT_A_PASSWORD ?? "";
const TENANT_B_EMAIL = process.env.CRM_TENANT_B_EMAIL ?? "";
const TENANT_B_PASSWORD = process.env.CRM_TENANT_B_PASSWORD ?? "";
const RELEASE_SHA = process.env.CRM_TESTED_SHA ?? "unknown";
const EVIDENCE_FILE = process.env.CRM008R_EVIDENCE_FILE ?? "crm-008r-production-smoke.json";

type AuthHeaders = { Authorization: string };

type Team = {
  id: string;
  code: string;
  displayName: string;
  description?: string | null;
  status: "ACTIVE" | "ARCHIVED";
  managerUserId?: string | null;
  defaultQueueId?: string | null;
  defaultTerritoryId?: string | null;
};

type TeamResponse = { data?: Team };
type TeamDetailResponse = { data?: { team?: Team } };
type TeamPageResponse = {
  data?: Team[];
  page?: { nextCursor?: string | null; hasMore?: boolean; limit?: number };
};

function entityTag(headers: Record<string, string>): string {
  return headers["x-snad-entity-tag"] ?? headers.etag ?? "";
}

function updatePayload(team: Team, displayName: string, status = team.status) {
  return {
    displayName,
    description: team.description ?? null,
    status,
    managerUserId: team.managerUserId ?? null,
    defaultQueueId: team.defaultQueueId ?? null,
    defaultTerritoryId: team.defaultTerritoryId ?? null,
  };
}

async function readTeam(
  request: APIRequestContext,
  auth: AuthHeaders,
  teamId: string,
): Promise<{ team: Team; tag: string }> {
  const response = await request.get(`/api/platform/api/v2/crm/teams/${teamId}`, {
    headers: auth,
  });
  expect(response.status(), `Team ${teamId} read failed`).toBe(200);
  const body = (await response.json()) as TeamDetailResponse;
  const team = body.data?.team;
  expect(team?.id).toBe(teamId);
  const tag = entityTag(response.headers());
  expect(tag, `Team ${teamId} response is missing a strong entity tag`).toBeTruthy();
  expect(tag.startsWith("W/")).toBe(false);
  return { team: team as Team, tag };
}

async function archiveTeam(
  request: APIRequestContext,
  auth: AuthHeaders,
  teamId: string,
): Promise<void> {
  const current = await readTeam(request, auth, teamId);
  if (current.team.status === "ARCHIVED") return;
  const response = await request.patch(`/api/platform/api/v2/crm/teams/${teamId}`, {
    headers: { ...auth, "X-SNAD-If-Match": current.tag },
    data: updatePayload(current.team, current.team.displayName, "ARCHIVED"),
  });
  expect(response.status(), `Team ${teamId} cleanup failed`).toBe(200);
}

test("CRM-008R exact-production atomic ETag, cursor integrity and tenant isolation", async ({ browser }) => {
  expect(TENANT_A_EMAIL).toBeTruthy();
  expect(TENANT_A_PASSWORD).toBeTruthy();
  expect(TENANT_B_EMAIL).toBeTruthy();
  expect(TENANT_B_PASSWORD).toBeTruthy();
  expect(RELEASE_SHA).not.toBe("unknown");

  const contextA = await browser.newContext();
  const contextB = await browser.newContext();
  const pageA = await contextA.newPage();
  const pageB = await contextB.newPage();
  const createdTeamIds: string[] = [];
  let authA: AuthHeaders | undefined;

  try {
    const loginA = await loginThroughUi(pageA, TENANT_A_EMAIL, TENANT_A_PASSWORD);
    const loginB = await loginThroughUi(pageB, TENANT_B_EMAIL, TENANT_B_PASSWORD);
    expect(loginA.user.tenantId).not.toBe(loginB.user.tenantId);

    authA = { Authorization: `Bearer ${loginA.accessToken}` };
    const authB: AuthHeaders = { Authorization: `Bearer ${loginB.accessToken}` };
    const runId = `${Date.now()}-${randomUUID().slice(0, 8)}`;

    for (let index = 1; index <= 3; index += 1) {
      const response = await pageA.request.post("/api/platform/api/v2/crm/teams", {
        headers: { ...authA, "Idempotency-Key": randomUUID() },
        data: {
          code: `CRM008R-${runId}-${index}`,
          displayName: `CRM-008R Production ${runId} ${index}`,
          description: `Temporary exact-production closure team ${index}`,
          managerUserId: null,
          defaultQueueId: null,
          defaultTerritoryId: null,
        },
      });
      expect(response.status(), `Team ${index} creation failed`).toBe(201);
      const body = (await response.json()) as TeamResponse;
      expect(body.data?.id).toBeTruthy();
      createdTeamIds.push(body.data!.id);
    }

    const firstPageResponse = await pageA.request.get(
      "/api/platform/api/v2/crm/teams?pageSize=2&status=ACTIVE",
      { headers: authA },
    );
    expect(firstPageResponse.status()).toBe(200);
    const firstPage = (await firstPageResponse.json()) as TeamPageResponse;
    expect(firstPage.data).toHaveLength(2);
    expect(firstPage.page?.limit).toBe(2);
    expect(firstPage.page?.hasMore).toBe(true);
    expect(firstPage.page?.nextCursor).toBeTruthy();
    const cursor = firstPage.page!.nextCursor!;

    const secondPageResponse = await pageA.request.get(
      `/api/platform/api/v2/crm/teams?pageSize=2&status=ACTIVE&cursor=${encodeURIComponent(cursor)}`,
      { headers: authA },
    );
    expect(secondPageResponse.status()).toBe(200);
    const secondPage = (await secondPageResponse.json()) as TeamPageResponse;
    expect((secondPage.data ?? []).length).toBeGreaterThan(0);
    expect((secondPage.data ?? []).length).toBeLessThanOrEqual(2);

    const crossTenantCursor = await pageB.request.get(
      `/api/platform/api/v2/crm/teams?pageSize=2&status=ACTIVE&cursor=${encodeURIComponent(cursor)}`,
      { headers: authB },
    );
    expect(crossTenantCursor.status()).toBe(400);

    const filterMismatch = await pageA.request.get(
      `/api/platform/api/v2/crm/teams?pageSize=2&status=ARCHIVED&cursor=${encodeURIComponent(cursor)}`,
      { headers: authA },
    );
    expect(filterMismatch.status()).toBe(400);

    const finalCharacter = cursor.at(-1) === "A" ? "B" : "A";
    const tampered = `${cursor.slice(0, -1)}${finalCharacter}`;
    const tamperedCursor = await pageA.request.get(
      `/api/platform/api/v2/crm/teams?pageSize=2&status=ACTIVE&cursor=${encodeURIComponent(tampered)}`,
      { headers: authA },
    );
    expect(tamperedCursor.status()).toBe(400);

    const targetId = createdTeamIds[0];
    const initial = await readTeam(pageA.request, authA, targetId);
    const firstPayload = updatePayload(initial.team, `CRM-008R Race Winner A ${runId}`);
    const secondPayload = updatePayload(initial.team, `CRM-008R Race Winner B ${runId}`);

    const [firstMutation, secondMutation] = await Promise.all([
      pageA.request.patch(`/api/platform/api/v2/crm/teams/${targetId}`, {
        headers: { ...authA, "X-SNAD-If-Match": initial.tag },
        data: firstPayload,
      }),
      pageA.request.patch(`/api/platform/api/v2/crm/teams/${targetId}`, {
        headers: { ...authA, "X-SNAD-If-Match": initial.tag },
        data: secondPayload,
      }),
    ]);
    const raceStatuses = [firstMutation.status(), secondMutation.status()].sort((a, b) => a - b);
    expect(raceStatuses).toEqual([200, 412]);

    const missingIfMatchTeam = await readTeam(pageA.request, authA, createdTeamIds[1]);
    const missingIfMatch = await pageA.request.patch(
      `/api/platform/api/v2/crm/teams/${createdTeamIds[1]}`,
      {
        headers: authA,
        data: updatePayload(
          missingIfMatchTeam.team,
          `CRM-008R Missing If-Match ${runId}`,
        ),
      },
    );
    expect(missingIfMatch.status()).toBe(428);

    const isolatedTeamRead = await pageB.request.get(
      `/api/platform/api/v2/crm/teams/${targetId}`,
      { headers: authB },
    );
    expect(isolatedTeamRead.status()).toBe(404);

    for (const teamId of createdTeamIds) {
      await archiveTeam(pageA.request, authA, teamId);
    }

    writeFileSync(
      EVIDENCE_FILE,
      JSON.stringify(
        {
          schema: "snad.crm008r.production-closure.v1",
          result: "PASS",
          releaseSha: RELEASE_SHA,
          completedAt: new Date().toISOString(),
          tenantAId: loginA.user.tenantId,
          tenantBId: loginB.user.tenantId,
          createdAndArchivedTeamCount: createdTeamIds.length,
          checks: {
            authenticatedTwoTenantLogin: "PASS",
            boundedFirstAndNextPage: "PASS",
            crossTenantCursorRejected400: "PASS",
            filterMismatchCursorRejected400: "PASS",
            tamperedCursorRejected400: "PASS",
            sameEtagRaceExactlyOneWinner: "PASS",
            staleEtagRejected412: "PASS",
            missingIfMatchRejected428: "PASS",
            crossTenantEntityReadRejected404: "PASS",
            temporaryDataArchived: "PASS",
          },
        },
        null,
        2,
      ),
      "utf8",
    );
    createdTeamIds.length = 0;
  } finally {
    if (authA) {
      for (const teamId of createdTeamIds) {
        try {
          await archiveTeam(pageA.request, authA, teamId);
        } catch (cleanupError) {
          console.warn(`CRM-008R best-effort cleanup failed for ${teamId}`, cleanupError);
        }
      }
    }
    await contextA.close();
    await contextB.close();
  }
});
