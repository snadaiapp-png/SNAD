package com.sanad.platform.crm.pagination;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRM-G2 Contract Test — Cursor Pagination (AC-03 + AC-04).
 * <p>
 * Verifies:
 *   - Cursors are opaque (Base64-URL-safe encoded).
 *   - Cursors are tenant-bound: a Tenant A cursor is rejected for Tenant B.
 *   - Cursors are sort-bound: a cursor issued for sort=updatedAt is rejected
 *     if the next request uses sort=createdAt.
 *   - Cursors are direction-bound: a desc cursor is rejected for an asc request.
 *   - Malformed cursors are rejected with VALIDATION_ERROR.
 *   - Cursors do not contain the tenant ID in plain text.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
class CrmPaginationContractTest {

    private final CursorCodec codec = new CursorCodec();
    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();

    @Test
    void cursorIsOpaqueAndRoundTripsForSameTenant() {
        UUID tieBreaker = UUID.randomUUID();
        String cursor = codec.encode(tenantA, "updatedAt", "desc", "2026-07-13T10:00:00Z", tieBreaker);
        assertNotNull(cursor);
        // Opaque: does not contain the raw tenant UUID or raw timestamp.
        assertTrue(!cursor.contains(tenantA.toString()),
                "cursor must not contain the raw tenant UUID");
        assertTrue(!cursor.contains("2026-07-13T10:00:00Z"),
                "cursor must not contain the raw sort value in plain text");
        // Decodes cleanly for the same tenant + sort + direction.
        CursorCodec.DecodedCursor decoded = codec.decode(cursor, tenantA, "updatedAt", "desc");
        assertEquals("2026-07-13T10:00:00Z", decoded.sortValue());
        assertEquals(tieBreaker, decoded.tieBreakerId());
    }

    @Test
    void cursorFromTenantAIsRejectedForTenantB() {
        // AC-04 — Tenant B cannot use Tenant A's cursor.
        String cursor = codec.encode(tenantA, "updatedAt", "desc", "2026-07-13T10:00:00Z", UUID.randomUUID());
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> codec.decode(cursor, tenantB, "updatedAt", "desc"));
        assertEquals(CrmErrorCode.VALIDATION_ERROR, ex.code());
        // The error message MUST NOT disclose that the cursor belongs to Tenant A.
        assertTrue(!ex.userMessage().contains(tenantA.toString()),
                "error message must not disclose the cursor's tenant owner");
    }

    @Test
    void cursorIssuedForSortUpdatedAtIsRejectedForSortCreatedAt() {
        String cursor = codec.encode(tenantA, "updatedAt", "desc", "2026-07-13T10:00:00Z", UUID.randomUUID());
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> codec.decode(cursor, tenantA, "createdAt", "desc"));
        assertEquals(CrmErrorCode.VALIDATION_ERROR, ex.code());
    }

    @Test
    void cursorIssuedForDescIsRejectedForAsc() {
        String cursor = codec.encode(tenantA, "updatedAt", "desc", "2026-07-13T10:00:00Z", UUID.randomUUID());
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> codec.decode(cursor, tenantA, "updatedAt", "asc"));
        assertEquals(CrmErrorCode.VALIDATION_ERROR, ex.code());
    }

    @Test
    void malformedCursorIsRejected() {
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> codec.decode("not-a-valid-cursor!!!", tenantA, "updatedAt", "desc"));
        assertEquals(CrmErrorCode.VALIDATION_ERROR, ex.code());
    }

    @Test
    void emptyCursorIsRejected() {
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> codec.decode("", tenantA, "updatedAt", "desc"));
        assertEquals(CrmErrorCode.VALIDATION_ERROR, ex.code());
    }

    @Test
    void pageRequestClampsLimitToMaximum() {
        PageRequest page = new PageRequest(500, null, "updatedAt", "desc");
        assertEquals(200, page.limit(), "limit must be clamped to MAX_LIMIT=200");
    }

    @Test
    void pageRequestClampsLimitToMinimum() {
        PageRequest page = new PageRequest(0, null, "updatedAt", "desc");
        assertEquals(1, page.limit(), "limit must be at least 1");
    }

    @Test
    void pageRequestDefaultsLimitToFifty() {
        PageRequest page = new PageRequest(null, null, "updatedAt", "desc");
        assertEquals(50, page.limit(), "default limit must be 50");
    }

    @Test
    void pageRequestRejectsUnknownSortField() {
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> new PageRequest(50, null, "password", "desc"));
        assertEquals(CrmErrorCode.VALIDATION_ERROR, ex.code());
    }

    @Test
    void pageRequestRejectsInvalidDirection() {
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> new PageRequest(50, null, "updatedAt", "sideways"));
        assertEquals(CrmErrorCode.VALIDATION_ERROR, ex.code());
    }

    @Test
    void pageRequestRejectsSortFieldWithSpecialChars() {
        // SQL injection attempt — must be rejected.
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> new PageRequest(50, null, "updatedAt; DROP TABLE crm_accounts;--", "desc"));
        assertEquals(CrmErrorCode.VALIDATION_ERROR, ex.code());
    }

    @Test
    void stableOrderByClauseIncludesTieBreakerOnId() {
        PageRequest page = new PageRequest(50, null, "updatedAt", "desc");
        String clause = page.stableOrderByClause();
        assertTrue(clause.contains("ORDER BY updated_at DESC, id DESC"),
                "stable ORDER BY must include id DESC as tie-breaker; got: " + clause);
    }
}
