package com.sanad.platform.hr.recruitment.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.recruitment.domain.HrCandidate;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrCandidateRepository;
import com.sanad.platform.security.crypto.BlindIndex;
import com.sanad.platform.security.crypto.EncryptedValue;
import com.sanad.platform.security.crypto.PlatformCryptographyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * HRM-G1 T4 — HrCandidate application service (design §5.1, §10).
 *
 * <p>Minimized-contact lifecycle: contacts are normalized, encrypted with the
 * G0 platform crypto authority (AES-256-GCM, tenant+purpose AAD) and indexed
 * with a tenant-scoped HMAC blind index. Duplicate contact matches emit a
 * WARNING audit row and NEVER merge rows. Read paths are capability-split:
 * VIEW returns masked contacts, MANAGE (full) returns decrypted values with a
 * sensitive-read audit trail. Raw PII never enters logs, evidence payloads,
 * or exception messages.</p>
 */
@Service
public class HrCandidateService {

    public static final String PURPOSE_CONTACT_EMAIL = "HRM.CANDIDATE.CONTACT.EMAIL";
    public static final String PURPOSE_CONTACT_PHONE = "HRM.CANDIDATE.CONTACT.PHONE";
    public static final String PURPOSE_COMPENSATION = "HRM.CANDIDATE.COMPENSATION";

    private final JdbcHrCandidateRepository repository;
    private final RecruitmentAuthorizationPort authorization;
    private final PlatformCryptographyService crypto;

    @Autowired
    public HrCandidateService(JdbcHrCandidateRepository repository,
                              RecruitmentAuthorizationPort authorization,
                              PlatformCryptographyService crypto) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
    }

    public UUID create(HrCommandContext ctx, String displayName, String email, String phone,
                       String compensationExpectations) {
        authorization.requireCandidateManage(ctx, null);
        HrCandidate.StoredContact emailContact = seal(ctx.tenantId(), PURPOSE_CONTACT_EMAIL, email);
        HrCandidate.StoredContact phoneContact = seal(ctx.tenantId(), PURPOSE_CONTACT_PHONE, phone);
        String compensationCipher = compensationExpectations == null ? null
                : crypto.encrypt(ctx.tenantId(), PURPOSE_COMPENSATION, compensationExpectations).ciphertext();

        HrCandidate candidate = new HrCandidate(UUID.randomUUID(), ctx.tenantId(), null,
                displayName, emailContact, phoneContact, compensationCipher,
                HrCandidate.STATE_ACTIVE, 0L);
        HrCandidate inserted = repository.insert(candidate, ctx.actorUserId(), ctx.correlationId());
        emitDuplicateWarnings(ctx, inserted, emailContact, phoneContact);
        return inserted.id();
    }

    /**
     * Candidate read. {@code fullDetails=true} requires CANDIDATE.MANAGE and
     * returns decrypted contacts (sensitive-read audited); the default VIEW
     * read returns masked contacts only.
     */
    public CandidateView read(HrCommandContext ctx, UUID candidateId, boolean fullDetails) {
        authorization.requireCandidateView(ctx, candidateId);
        HrCandidate candidate = repository.find(ctx.tenantId(), candidateId)
                .orElseThrow(() -> new IllegalStateException(
                        "HRM_CANDIDATE_NOT_FOUND: " + candidateId + " is not visible to this tenant context"));

        boolean reveal = false;
        if (fullDetails) {
            authorization.requireCandidateManage(ctx, candidateId);
            reveal = true;
        }
        repository.auditRead(ctx.tenantId(), candidateId, ctx.actorUserId(), ctx.correlationId(), reveal);

        return new CandidateView(
                candidate.id(),
                candidate.candidateNumber(),
                candidate.displayName(),
                candidate.poolState(),
                candidate.email() == null ? null
                        : (reveal ? unseal(ctx.tenantId(), PURPOSE_CONTACT_EMAIL, candidate.email())
                        : maskEmail(candidate.email())),
                candidate.phone() == null ? null
                        : (reveal ? unseal(ctx.tenantId(), PURPOSE_CONTACT_PHONE, candidate.phone())
                        : maskPhone(candidate.phone())));
    }

    public void archive(HrCommandContext ctx, UUID candidateId) {
        authorization.requireCandidateManage(ctx, candidateId);
        HrCandidate candidate = repository.find(ctx.tenantId(), candidateId)
                .orElseThrow(() -> new IllegalStateException(
                        "HRM_CANDIDATE_NOT_FOUND: " + candidateId + " is not visible to this tenant context"));
        if (!HrCandidate.canArchive(candidate.poolState())) {
            throw new IllegalStateException("HRM_CANDIDATE_STATE_CONFLICT: pool_state " + candidate.poolState()
                    + " cannot be archived (terminal or retained-as-history)");
        }
        repository.archive(ctx.tenantId(), candidateId, candidate.poolState(),
                ctx.actorUserId(), ctx.correlationId());
    }

    // --- internals ---

    private void emitDuplicateWarnings(HrCommandContext ctx, HrCandidate inserted,
                                       HrCandidate.StoredContact email, HrCandidate.StoredContact phone) {
        if (email != null && repository.contactHashExists(ctx.tenantId(), "contact_email_hash", email.hash(),
                inserted.id())) {
            repository.auditDuplicate(ctx.tenantId(), inserted.id(), ctx.actorUserId(),
                    ctx.correlationId(), "contact_email_hash");
        }
        if (phone != null && repository.contactHashExists(ctx.tenantId(), "contact_phone_hash", phone.hash(),
                inserted.id())) {
            repository.auditDuplicate(ctx.tenantId(), inserted.id(), ctx.actorUserId(),
                    ctx.correlationId(), "contact_phone_hash");
        }
    }

    private HrCandidate.StoredContact seal(UUID tenantId, String purpose, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = normalize(purpose, raw);
        BlindIndex index = crypto.blindIndex(tenantId, purpose, normalized);
        EncryptedValue encrypted = crypto.encrypt(tenantId, purpose, normalized);
        return new HrCandidate.StoredContact(encrypted.ciphertext(), index.value());
    }

    private String unseal(UUID tenantId, String purpose, HrCandidate.StoredContact contact) {
        String[] parts = contact.ciphertext().split(":", 3);
        return crypto.decrypt(tenantId, purpose,
                new EncryptedValue(contact.ciphertext(), parts.length > 1 ? parts[1] : "v1", "AES-256-GCM"));
    }

    private String normalize(String purpose, String raw) {
        String trimmed = raw.trim();
        // Email dedup is case-insensitive; phones keep their literal form.
        return PURPOSE_CONTACT_EMAIL.equals(purpose) ? trimmed.toLowerCase() : trimmed;
    }

    private static String maskEmail(HrCandidate.StoredContact contact) {
        String[] parts = contact.ciphertext().split(":", 3);
        String keyVersion = parts.length > 1 ? parts[1] : "v1";
        return "•••(email@" + keyVersion + ")•••";
    }

    private static String maskPhone(HrCandidate.StoredContact contact) {
        String[] parts = contact.ciphertext().split(":", 3);
        String keyVersion = parts.length > 1 ? parts[1] : "v1";
        return "•••(phone@" + keyVersion + ")•••";
    }

    /** Read DTO — contact values masked or full per capability (§10.1 row 537). */
    public record CandidateView(
            UUID id,
            String candidateNumber,
            String displayName,
            String poolState,
            String email,
            String phone) {
    }
}
