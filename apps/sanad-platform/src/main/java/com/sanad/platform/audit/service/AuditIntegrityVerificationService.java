package com.sanad.platform.audit.service;

import com.sanad.platform.audit.domain.AuditEvent;
import com.sanad.platform.audit.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Stage 05 §11 — Verifies the integrity of a tenant's audit hash chain.
 *
 * <p>Walks the chain from oldest to newest, recomputing each event's
 * hash and comparing it against the stored hash. Returns the first
 * broken event ID (if any), the total events checked, and the
 * calculated vs. stored head hash.</p>
 */
@Service
public class AuditIntegrityVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AuditIntegrityVerificationService.class);

    private final AuditEventRepository repository;
    private final AuditHashChainService hashChainService;

    public AuditIntegrityVerificationService(AuditEventRepository repository,
                                              AuditHashChainService hashChainService) {
        this.repository = repository;
        this.hashChainService = hashChainService;
    }

    /**
     * Verifies the hash chain for a tenant.
     *
     * @param tenantId the tenant whose chain to verify
     * @return a result record with {@code valid}, {@code firstBrokenEventId},
     *         {@code eventsChecked}, {@code calculatedHeadHash}, {@code storedHeadHash}
     */
    @Transactional(readOnly = true)
    public VerificationResult verifyChain(UUID tenantId) {
        List<AuditEvent> events = repository.findAllByTenantIdOrderedForVerification(tenantId);

        if (events.isEmpty()) {
            return new VerificationResult(true, null, 0, hashChainService.getGenesisHash(), null);
        }

        String expectedPreviousHash = hashChainService.getGenesisHash();
        UUID firstBrokenEventId = null;
        int checked = 0;

        for (AuditEvent event : events) {
            checked++;
            String recomputed = hashChainService.computeEventHash(event, expectedPreviousHash);
            if (!recomputed.equals(event.getEventHash())) {
                log.warn("Audit integrity failure: tenant={} eventId={} storedHash={} recomputedHash={}",
                        tenantId, event.getId(), event.getEventHash(), recomputed);
                if (firstBrokenEventId == null) {
                    firstBrokenEventId = event.getId();
                }
                // Continue checking to report the full extent.
            }
            // For the next event, the expected previousHash is this event's stored hash.
            expectedPreviousHash = event.getEventHash();
        }

        AuditEvent head = events.get(events.size() - 1);
        boolean valid = firstBrokenEventId == null;
        return new VerificationResult(valid, firstBrokenEventId, checked,
                expectedPreviousHash, head.getEventHash());
    }

    /**
     * Result of a chain verification.
     *
     * @param valid true if every event's hash matched its recomputed value
     * @param firstBrokenEventId the ID of the first event whose hash did not
     *                           match, or null if all events verified
     * @param eventsChecked the total number of events in the chain
     * @param calculatedHeadHash the recomputed hash of the last event
     *                           (or genesis hash if the chain is empty)
     * @param storedHeadHash the stored hash of the last event
     *                       (or null if the chain is empty)
     */
    public record VerificationResult(
            boolean valid,
            UUID firstBrokenEventId,
            int eventsChecked,
            String calculatedHeadHash,
            String storedHeadHash
    ) {}
}
