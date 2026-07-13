package com.sanad.platform.crm.contract;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.idempotency.IdempotencyService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRM-G2 Contract Test — Idempotency (AC-06, AC-07, AC-08).
 * <p>
 * Verifies:
 *   - AC-06: same key + same payload replays the same response (no duplicate record).
 *   - AC-07: same key + different payload yields 409 CRM_IDEMPOTENCY_CONFLICT.
 *   - AC-08: lead conversion replay returns the same logical result.
 *   - Cross-tenant: the same key used by Tenant A and Tenant B is independent.
 *   - Cross-principal: the same key used by two users in the same tenant is independent.
 *   - Cross-endpoint: the same key used for POST /accounts and POST /contacts is independent.
 *   - Fail-then-retry: a failed operation can be retried with the same key.
 * <p>
 * Uses {@link IdempotencyService.InMemoryIdempotencyService} so no DB is
 * required.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
class CrmIdempotencyContractTest {

    private final IdempotencyService service = new IdempotencyService.InMemoryIdempotencyService();
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID principalA = UUID.randomUUID();
    private final UUID principalB = UUID.randomUUID();

    @Test
    void ac06_sameKeySamePayloadReplaysSameResult() {
        String endpoint = "POST:/api/v2/crm/accounts";
        String key = "client-key-001";
        String fingerprint = IdempotencyService.fingerprint("POST", "/api/v2/crm/accounts", "{\"displayName\":\"Acme\"}");

        IdempotencyService.Replay first = service.begin(tenantA, principalA, endpoint, key, fingerprint);
        assertTrue(first instanceof IdempotencyService.Replay.ReplayMiss, "first call must be a miss");
        UUID operationId = ((IdempotencyService.Replay.ReplayMiss) first).operationId();
        service.complete(operationId, 201, "{\"id\":\"abc\",\"version\":0}", null, "application/json");

        // Second call with the same key + payload must replay the cached response.
        IdempotencyService.Replay second = service.begin(tenantA, principalA, endpoint, key, fingerprint);
        assertInstanceOf(IdempotencyService.Replay.ReplayHit.class, second,
                "second call with same key+payload must be a replay hit");
        IdempotencyService.Replay.ReplayHit hit = (IdempotencyService.Replay.ReplayHit) second;
        assertEquals(201, hit.record().responseStatus());
        assertEquals("{\"id\":\"abc\",\"version\":0}", hit.record().responseBodyJson());
    }

    @Test
    void ac07_sameKeyDifferentPayloadYieldsConflict() {
        String endpoint = "POST:/api/v2/crm/accounts";
        String key = "client-key-002";
        String fingerprint1 = IdempotencyService.fingerprint("POST", "/api/v2/crm/accounts", "{\"displayName\":\"Acme\"}");
        String fingerprint2 = IdempotencyService.fingerprint("POST", "/api/v2/crm/accounts", "{\"displayName\":\"Other\"}");

        IdempotencyService.Replay first = service.begin(tenantA, principalA, endpoint, key, fingerprint1);
        service.complete(((IdempotencyService.Replay.ReplayMiss) first).operationId(), 201, "{}", null, "application/json");

        // Same key, different payload → 409 conflict.
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> service.begin(tenantA, principalA, endpoint, key, fingerprint2));
        assertEquals(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT, ex.code());
        assertEquals(409, CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT.httpStatus());
    }

    @Test
    void ac08_leadConversionReplayReturnsSameLogicalResult() {
        String endpoint = "POST:/api/v2/crm/leads/00000000-0000-4000-8000-000000000001/convert";
        String key = "lead-convert-key-001";
        String fingerprint = IdempotencyService.fingerprint("POST", endpoint, "{}");

        IdempotencyService.Replay first = service.begin(tenantA, principalA, endpoint, key, fingerprint);
        service.complete(((IdempotencyService.Replay.ReplayMiss) first).operationId(), 200,
                "{\"lead\":{\"id\":\"...\",\"status\":\"CONVERTED\"},\"idempotent\":false}",
                null, "application/json");

        // Replay with same key+payload — must return the same logical result
        // without creating a duplicate account/contact/opportunity.
        IdempotencyService.Replay second = service.begin(tenantA, principalA, endpoint, key, fingerprint);
        assertInstanceOf(IdempotencyService.Replay.ReplayHit.class, second);
        IdempotencyService.Replay.ReplayHit hit = (IdempotencyService.Replay.ReplayHit) second;
        assertEquals(200, hit.record().responseStatus());
        assertTrue(hit.record().responseBodyJson().contains("CONVERTED"),
                "replay must return the original conversion result");
    }

    @Test
    void sameKeyIsIndependentAcrossTenants() {
        String endpoint = "POST:/api/v2/crm/accounts";
        String key = "shared-key-001";
        String fpA = IdempotencyService.fingerprint("POST", endpoint, "{\"displayName\":\"Tenant A Account\"}");
        String fpB = IdempotencyService.fingerprint("POST", endpoint, "{\"displayName\":\"Tenant B Account\"}");

        // Tenant A begins an operation with the shared key.
        IdempotencyService.Replay a = service.begin(tenantA, principalA, endpoint, key, fpA);
        assertInstanceOf(IdempotencyService.Replay.ReplayMiss.class, a);

        // Tenant B can use the SAME key without conflict — idempotency is
        // tenant-scoped.
        IdempotencyService.Replay b = service.begin(tenantB, principalB, endpoint, key, fpB);
        assertInstanceOf(IdempotencyService.Replay.ReplayMiss.class, b,
                "Tenant B must be able to reuse the same Idempotency-Key as Tenant A");
    }

    @Test
    void sameKeyIsIndependentAcrossPrincipalsInSameTenant() {
        String endpoint = "POST:/api/v2/crm/accounts";
        String key = "shared-key-002";
        String fp = IdempotencyService.fingerprint("POST", endpoint, "{}");

        IdempotencyService.Replay a = service.begin(tenantA, principalA, endpoint, key, fp);
        IdempotencyService.Replay b = service.begin(tenantA, principalB, endpoint, key, fp);
        assertInstanceOf(IdempotencyService.Replay.ReplayMiss.class, a);
        assertInstanceOf(IdempotencyService.Replay.ReplayMiss.class, b,
                "Two principals in the same tenant must be able to reuse the same Idempotency-Key");
    }

    @Test
    void sameKeyIsIndependentAcrossEndpoints() {
        String key = "shared-key-003";
        String fp = IdempotencyService.fingerprint("POST", "/api/v2/crm/accounts", "{}");

        IdempotencyService.Replay a = service.begin(tenantA, principalA, "POST:/api/v2/crm/accounts", key, fp);
        IdempotencyService.Replay b = service.begin(tenantA, principalA, "POST:/api/v2/crm/contacts", key, fp);
        assertInstanceOf(IdempotencyService.Replay.ReplayMiss.class, a);
        assertInstanceOf(IdempotencyService.Replay.ReplayMiss.class, b,
                "The same Idempotency-Key must be reusable across different endpoints");
    }

    @Test
    void failedOperationCanRetriedWithSameKey() {
        String endpoint = "POST:/api/v2/crm/accounts";
        String key = "retry-key-001";
        String fp = IdempotencyService.fingerprint("POST", endpoint, "{}");

        // First attempt fails before commit.
        IdempotencyService.Replay first = service.begin(tenantA, principalA, endpoint, key, fp);
        UUID operationId = ((IdempotencyService.Replay.ReplayMiss) first).operationId();
        service.fail(operationId);

        // Retry with the same key must succeed as a fresh miss.
        IdempotencyService.Replay retry = service.begin(tenantA, principalA, endpoint, key, fp);
        assertInstanceOf(IdempotencyService.Replay.ReplayMiss.class, retry,
                "After fail(), retrying with the same key must start a fresh operation");
        UUID retryId = ((IdempotencyService.Replay.ReplayMiss) retry).operationId();
        assertNotEquals(operationId, retryId, "retry must produce a new operation id");
    }

    @Test
    void inFlightOperationBlocksConcurrentRetryWithSameKey() {
        String endpoint = "POST:/api/v2/crm/accounts";
        String key = "inflight-key-001";
        String fp = IdempotencyService.fingerprint("POST", endpoint, "{}");

        // First call starts the operation but does NOT complete it.
        service.begin(tenantA, principalA, endpoint, key, fp);

        // A concurrent retry with the same key+payload must yield a conflict
        // (not a duplicate operation).
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> service.begin(tenantA, principalA, endpoint, key, fp));
        assertEquals(CrmErrorCode.CRM_IDEMPOTENCY_CONFLICT, ex.code());
        assertTrue(ex.userMessage().contains("in progress"),
                "concurrent retry must be told the operation is in progress");
    }

    @Test
    void fingerprintIsDeterministic() {
        String fp1 = IdempotencyService.fingerprint("POST", "/api/v2/crm/accounts", "{\"a\":1}");
        String fp2 = IdempotencyService.fingerprint("POST", "/api/v2/crm/accounts", "{\"a\":1}");
        assertEquals(fp1, fp2, "fingerprint must be deterministic for the same input");
    }

    @Test
    void fingerprintChangesWhenPayloadChanges() {
        String fp1 = IdempotencyService.fingerprint("POST", "/api/v2/crm/accounts", "{\"a\":1}");
        String fp2 = IdempotencyService.fingerprint("POST", "/api/v2/crm/accounts", "{\"a\":2}");
        assertNotEquals(fp1, fp2, "fingerprint must change when the payload changes");
    }

    @Test
    void fingerprintChangesWhenMethodChanges() {
        String fp1 = IdempotencyService.fingerprint("POST", "/api/v2/crm/accounts", "{}");
        String fp2 = IdempotencyService.fingerprint("PUT", "/api/v2/crm/accounts", "{}");
        assertNotEquals(fp1, fp2, "fingerprint must change when the method changes");
    }
}
