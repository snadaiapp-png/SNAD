package com.sanad.platform.audit.service;

import com.sanad.platform.audit.domain.AuditChainHead;
import com.sanad.platform.audit.domain.AuditEvent;
import com.sanad.platform.audit.repository.AuditChainHeadRepository;
import com.sanad.platform.audit.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Stage 05A.1 §9 — Verifies the integrity of a tenant's audit hash chain.
 *
 * <p>Walks the chain ordered by {@code sequence_number ASC}, verifying:
 * <ul>
 *   <li>Each event's {@code previous_hash} equals the preceding event's {@code event_hash}</li>
 *   <li>Each event's {@code event_hash} matches a recomputed hash</li>
 *   <li>{@code sequence_number} increments by exactly 1</li>
 * </ul>
 *
 * <p>Returns {@code calculatedHeadHash} (recomputed from the last event)
 * and {@code storedHeadHash} (from {@code audit_chain_heads}). A mismatch
 * indicates tampering or a chain-head update failure.</p>
 */
@Service
public class AuditIntegrityVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AuditIntegrityVerificationService.class);

    private final AuditEventRepository repository;
    private final AuditChainHeadRepository chainHeadRepository;
    private final AuditHashChainService hashChainService;

    public AuditIntegrityVerificationService(AuditEventRepository repository,
                                              AuditChainHeadRepository chainHeadRepository,
                                              AuditHashChainService hashChainService) {
        this.repository = repository;
        this.chainHeadRepository = chainHeadRepository;
        this.hashChainService = hashChainService;
    }

    @Transactional(readOnly = true)
    public VerificationResult verifyChain(UUID tenantId) {
        List<AuditEvent> events = repository.findAllByTenantIdOrderedForVerification(tenantId);

        AuditChainHead chainHead = chainHeadRepository.findByTenantId(tenantId).orElse(null);
        String storedHeadHash = chainHead != null ? chainHead.getHeadHash() : null;
        long storedHeadSequence = chainHead != null && chainHead.getHeadSequence() != null
                ? chainHead.getHeadSequence() : 0;

        if (events.isEmpty()) {
            return new VerificationResult(true, null, 0,
                    hashChainService.getGenesisHash(), storedHeadHash, storedHeadSequence);
        }

        String expectedPreviousHash = hashChainService.getGenesisHash();
        long expectedSequence = 1;
        UUID firstBrokenEventId = null;
        String calculatedHeadHash = hashChainService.getGenesisHash();
        int checked = 0;

        for (AuditEvent event : events) {
            checked++;

            // Check sequence increments by exactly 1
            if (event.getSequenceNumber() == null || event.getSequenceNumber() != expectedSequence) {
                log.warn("Audit integrity: sequence mismatch tenant={} eventId={} expected={} actual={}",
                        tenantId, event.getId(), expectedSequence, event.getSequenceNumber());
                if (firstBrokenEventId == null) firstBrokenEventId = event.getId();
            }

            // Check previousHash linkage
            if (!nullSafeEquals(event.getPreviousHash(), expectedPreviousHash)) {
                log.warn("Audit integrity: previousHash mismatch tenant={} eventId={} expected={} actual={}",
                        tenantId, event.getId(), expectedPreviousHash, event.getPreviousHash());
                if (firstBrokenEventId == null) firstBrokenEventId = event.getId();
            }

            // Recompute event hash
            String recomputed = hashChainService.computeEventHash(event, expectedPreviousHash);
            if (!recomputed.equals(event.getEventHash())) {
                log.warn("Audit integrity: hash mismatch tenant={} eventId={} stored={} recomputed={}",
                        tenantId, event.getId(), event.getEventHash(), recomputed);
                if (firstBrokenEventId == null) firstBrokenEventId = event.getId();
            }

            // Advance
            expectedPreviousHash = event.getEventHash();
            calculatedHeadHash = event.getEventHash();
            expectedSequence++;
        }

        boolean valid = firstBrokenEventId == null
                && nullSafeEquals(calculatedHeadHash, storedHeadHash)
                && (events.size() == storedHeadSequence);
        return new VerificationResult(valid, firstBrokenEventId, checked,
                calculatedHeadHash, storedHeadHash, storedHeadSequence);
    }

    private static boolean nullSafeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Result of a chain verification.
     *
     * @param valid true if every event verified and head matches
     * @param firstBrokenEventId first event with a verification failure, or null
     * @param eventsChecked total events checked
     * @param calculatedHeadHash recomputed hash of the last event
     * @param storedHeadHash hash from audit_chain_heads
     * @param storedHeadSequence sequence from audit_chain_heads
     */
    public record VerificationResult(
            boolean valid,
            UUID firstBrokenEventId,
            int eventsChecked,
            String calculatedHeadHash,
            String storedHeadHash,
            long storedHeadSequence
    ) {}
}
