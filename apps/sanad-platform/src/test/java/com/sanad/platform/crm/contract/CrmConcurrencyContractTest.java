package com.sanad.platform.crm.contract;

import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRM-G2 Contract Test — Optimistic Concurrency (AC-05).
 * <p>
 * Verifies the ETag + If-Match contract:
 *   - ETags are deterministic for the same (entityType, id, version) triple.
 *   - ETags change when the version changes.
 *   - ETags include the entity type prefix so an Account ETag cannot be
 *     used to update an Opportunity.
 *   - Missing If-Match header → VALIDATION_ERROR.
 *   - Stale If-Match → CRM_CONCURRENCY_CONFLICT (HTTP 412).
 *   - Wildcard If-Match ("*") matches any version.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
class CrmConcurrencyContractTest {

    private final ETagService etags = new ETagService();
    private final UUID id = UUID.randomUUID();

    @Test
    void etagIsDeterministicForSameVersion() {
        String e1 = etags.etag("account", id, 4L);
        String e2 = etags.etag("account", id, 4L);
        assertEquals(e1, e2, "ETag must be deterministic for the same (entityType, id, version) triple");
    }

    @Test
    void etagChangesWhenVersionChanges() {
        String e1 = etags.etag("account", id, 4L);
        String e2 = etags.etag("account", id, 5L);
        assertNotEquals(e1, e2, "ETag must change when the version changes");
    }

    @Test
    void etagIncludesEntityTypePrefix() {
        String accountEtag = etags.etag("account", id, 4L);
        String opportunityEtag = etags.etag("opportunity", id, 4L);
        assertTrue(accountEtag.startsWith("\"account-"), "Account ETag must include the 'account-' prefix");
        assertTrue(opportunityEtag.startsWith("\"opportunity-"), "Opportunity ETag must include the 'opportunity-' prefix");
        assertNotEquals(accountEtag, opportunityEtag,
                "An Account ETag must NOT be valid for an Opportunity with the same id+version");
    }

    @Test
    void missingIfMatchHeaderIsRejected() {
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> etags.validateIfMatch(null, "account", id, 4L));
        assertEquals(CrmErrorCode.VALIDATION_ERROR, ex.code(),
                "Missing If-Match must yield VALIDATION_ERROR, not concurrency conflict");
    }

    @Test
    void blankIfMatchHeaderIsRejected() {
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> etags.validateIfMatch("  ", "account", id, 4L));
        assertEquals(CrmErrorCode.VALIDATION_ERROR, ex.code());
    }

    @Test
    void staleIfMatchYieldsConcurrencyConflict() {
        // AC-05 — Client B submits an update with the stale ETag from version 4
        // after Client A has already advanced the row to version 5.
        String staleEtag = etags.etag("account", id, 4L);
        long currentVersion = 5L;
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> etags.validateIfMatch(staleEtag, "account", id, currentVersion));
        assertEquals(CrmErrorCode.CRM_CONCURRENCY_CONFLICT, ex.code());
        assertEquals(412, CrmErrorCode.CRM_CONCURRENCY_CONFLICT.httpStatus());
    }

    @Test
    void currentIfMatchIsAccepted() {
        String currentEtag = etags.etag("account", id, 5L);
        // Should NOT throw.
        etags.validateIfMatch(currentEtag, "account", id, 5L);
    }

    @Test
    void wildcardIfMatchIsAccepted() {
        // RFC 7232 — "*" matches any version of the resource.
        etags.validateIfMatch("*", "account", id, 5L);
        etags.validateIfMatch("*", "account", id, 999L);
    }

    @Test
    void ifMatchListWithAtLeastOneMatchIsAccepted() {
        String staleEtag = etags.etag("account", id, 4L);
        String currentEtag = etags.etag("account", id, 5L);
        etags.validateIfMatch(staleEtag + ", " + currentEtag, "account", id, 5L);
    }

    @Test
    void ifMatchForDifferentEntityTypeIsRejected() {
        // An Opportunity ETag is NOT a valid If-Match for an Account PATCH,
        // even if the id and version happen to be the same.
        String opportunityEtag = etags.etag("opportunity", id, 5L);
        CrmContractException ex = assertThrows(CrmContractException.class,
                () -> etags.validateIfMatch(opportunityEtag, "account", id, 5L));
        assertEquals(CrmErrorCode.CRM_CONCURRENCY_CONFLICT, ex.code());
    }

    @Test
    void etagIsQuotedPerHttpSpec() {
        String e = etags.etag("account", id, 7L);
        assertNotNull(e);
        assertTrue(e.startsWith("\"") && e.endsWith("\""),
                "ETag must be a quoted string per RFC 7232; got: " + e);
    }
}
