import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";

/**
 * Unit tests for the Subscription Control Plane API surface — endpoint
 * construction, query serialization and pagination contract decoding.
 */

const getMock = vi.fn();
const postMock = vi.fn();

vi.mock("./client", () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
  },
}));

import { scpApi, type AuditEntry } from "./scp-api";

describe("scpApi — endpoint construction", () => {
  beforeEach(() => {
    getMock.mockReset();
    getMock.mockResolvedValue({});
    postMock.mockReset();
    postMock.mockResolvedValue({});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("builds the overview route", async () => {
    await scpApi.overview();
    expect(getMock).toHaveBeenCalledWith("/api/v1/executive/overview");
  });

  it("serializes tenant search filters and pagination", async () => {
    await scpApi.tenants({ search: "acme", status: "ACTIVE", page: 2, size: 20 });
    expect(getMock).toHaveBeenCalledWith(
      "/api/v1/executive/tenants/v2?search=acme&status=ACTIVE&page=2&size=20",
    );
  });

  it("omits empty query parameters", async () => {
    await scpApi.tenants({ search: "", status: undefined, page: 0, size: 20 });
    expect(getMock).toHaveBeenCalledWith("/api/v1/executive/tenants/v2?page=0&size=20");
  });

  it("builds the subscriptions v2 grid route with trial filter", async () => {
    await scpApi.subscriptions({ trialOnly: true, search: "erp" });
    expect(getMock).toHaveBeenCalledWith(
      "/api/v1/executive/subscriptions/v2?trialOnly=true&search=erp",
    );
  });

  it("builds usage and additive v2 audit routes tenant-scoped", async () => {
    await scpApi.usage("tenant-1");
    await scpApi.audit({ page: 1, size: 20, direction: "DESC" });
    expect(getMock).toHaveBeenNthCalledWith(1, "/api/v1/executive/usage?tenantId=tenant-1");
    expect(getMock).toHaveBeenNthCalledWith(
      2,
      "/api/v1/executive/audit/v2?page=1&size=20&direction=DESC",
    );
  });

  it("posts lifecycle commands with a reason body", async () => {
    await scpApi.lifecycleCommand("sub-1", "SUSPEND", "policy violation");
    expect(postMock).toHaveBeenCalledWith(
      "/api/v1/executive/subscriptions/sub-1/lifecycle/SUSPEND",
      { reason: "policy violation" },
    );
  });

  it("posts change previews to the change-preview route", async () => {
    await scpApi.previewChange("sub-1", "version-9", "SA");
    expect(postMock).toHaveBeenCalledWith("/api/v1/executive/subscriptions/sub-1/change-preview", {
      targetPlanVersionId: "version-9",
      countryCode: "SA",
    });
  });
});

describe("scpApi AuditEntry — backend AuditEntryResponse contract", () => {
  /**
   * R0C12 Phase C audit contract gate: the frontend AuditEntry projection
   * served from /api/v1/executive/audit/v2 must truthfully model every
   * field of the backend authority AdminDtos.AuditEntryResponse (11 fields:
   * id, actorTenantId, actorUserId, targetTenantId, action, resourceType,
   * resourceId, reason, result, correlationId, createdAt).
   *
   * The annotated literals below fail `tsc --noEmit` while the projection
   * omits any backend field (excess-property check), so the contract stays
   * enforced by the typecheck quality gate; the runtime key assertion adds
   * a second, type-erasure-proof layer to the same gate.
   */
  const BACKEND_AUDIT_ENTRY_RESPONSE_FIELDS = [
    "id",
    "actorTenantId",
    "actorUserId",
    "targetTenantId",
    "action",
    "resourceType",
    "resourceId",
    "reason",
    "result",
    "correlationId",
    "createdAt",
  ] as const;

  it("models the full backend projection (all 11 fields)", () => {
    const governedRow: AuditEntry = {
      id: "0b6f2d0e-0000-0000-0000-000000000001",
      actorTenantId: "0b6f2d0e-0000-0000-0000-000000000002",
      actorUserId: "0b6f2d0e-0000-0000-0000-000000000003",
      targetTenantId: "0b6f2d0e-0000-0000-0000-000000000004",
      action: "TENANT_UPDATED",
      resourceType: "TENANT",
      resourceId: "tenant-7",
      reason: "governed R0C12 update",
      result: "SUCCESS",
      correlationId: "corr-r0c12-1",
      createdAt: "2026-09-10T00:00:00Z",
    };

    expect(Object.keys(governedRow).sort()).toEqual(
      [...BACKEND_AUDIT_ENTRY_RESPONSE_FIELDS].sort(),
    );
  });

  it("accepts null actor/target/correlation context for system-initiated audit rows", () => {
    // PlatformAuditService.mapAudit maps actor_tenant_id / actor_user_id /
    // target_tenant_id via getObject(UUID.class) and correlation_id via
    // getString — all nullable when audit rows are system-initiated.
    const systemRow: AuditEntry = {
      id: "0b6f2d0e-0000-0000-0000-000000000009",
      actorTenantId: null,
      actorUserId: null,
      targetTenantId: null,
      action: "SUBSCRIPTION_SUSPENDED",
      resourceType: "SUBSCRIPTION",
      resourceId: "sub-3",
      reason: null,
      result: "SUCCESS",
      correlationId: null,
      createdAt: "2026-09-10T00:00:00Z",
    };

    expect(systemRow.actorTenantId).toBeNull();
    expect(systemRow.actorUserId).toBeNull();
    expect(systemRow.targetTenantId).toBeNull();
    expect(systemRow.correlationId).toBeNull();
  });
});
