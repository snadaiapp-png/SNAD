package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.CreateRelationshipCommand;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.UpdateContactProfileCommand;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.party.domain.ContactRepository.CreateContactCommand;
import com.sanad.platform.crm.party.domain.OwnerValidationPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Typed row-oriented importer for people and account relationships.
 * Each row is committed independently so one invalid relationship does not
 * discard successful rows. A personKey is an explicit batch-scoped linkage;
 * email is never used as an automatic merge key.
 */
@Service
public class ContactRelationshipImportService {

    private static final int MAX_ROWS = 10_000;

    private final ContactUseCases contacts;
    private final ContactRelationshipUseCases relationships;
    private final OwnerValidationPort owners;
    private final AuditPort audit;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    public ContactRelationshipImportService(
            ContactUseCases contacts,
            ContactRelationshipUseCases relationships,
            OwnerValidationPort owners,
            AuditPort audit,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.contacts = contacts;
        this.relationships = relationships;
        this.owners = owners;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ImportResult importRows(
            UUID tenantId,
            UUID actorId,
            UUID importId,
            List<ImportRow> rows) {
        if (tenantId == null || actorId == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED);
        }
        if (rows == null || rows.isEmpty()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "At least one import row is required.");
        }
        if (rows.size() > MAX_ROWS) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Contact relationship import exceeds the 10,000 row limit.");
        }

        UUID effectiveImportId = importId == null ? UUID.randomUUID() : importId;
        Map<String, UUID> personKeys = new LinkedHashMap<>();
        List<RowResult> results = new ArrayList<>(rows.size());
        int succeeded = 0;
        int failed = 0;

        for (int index = 0; index < rows.size(); index++) {
            ImportRow row = rows.get(index);
            try {
                RowResult result = requiresNew.execute(status ->
                        processRow(tenantId, actorId, effectiveImportId, index + 1, row, personKeys));
                if (result == null) {
                    throw new IllegalStateException("Import row transaction returned no result.");
                }
                results.add(result);
                succeeded++;
                if (row.personKey() != null && !row.personKey().isBlank()) {
                    personKeys.putIfAbsent(normalizeKey(row.personKey()), result.contactId());
                }
            } catch (RuntimeException exception) {
                failed++;
                results.add(failure(index + 1, row, exception));
            }
        }

        return new ImportResult(
                effectiveImportId,
                rows.size(),
                succeeded,
                failed,
                List.copyOf(results));
    }

    private RowResult processRow(
            UUID tenantId,
            UUID actorId,
            UUID importId,
            int rowNumber,
            ImportRow row,
            Map<String, UUID> personKeys) {
        if (row == null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Import row cannot be null.");
        }
        if (row.accountId() == null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "accountId is required.");
        }
        validateOwner(tenantId, row.personOwnerUserId());
        validateOwner(tenantId, row.relationshipOwnerUserId());

        UUID contactId = resolveContactId(tenantId, actorId, row, personKeys);
        RelationshipRecord created = relationships.createRelationship(
                tenantId,
                actorId,
                contactId,
                new CreateRelationshipCommand(
                        row.accountId(),
                        row.roleCode(),
                        row.customRoleId(),
                        row.primaryRelationship(),
                        row.validFrom(),
                        row.validTo(),
                        row.jobTitle(),
                        row.department(),
                        row.decisionAuthority(),
                        row.relationshipOwnerUserId()));

        Instant now = Instant.now();
        audit.record(
                tenantId,
                actorId,
                "IMPORT_RELATIONSHIP_CREATE",
                "CONTACT_ACCOUNT_RELATIONSHIP",
                created.id(),
                new AuditChange(null, objectMapper.valueToTree(Map.of(
                        "importId", importId,
                        "rowNumber", rowNumber,
                        "contactId", contactId,
                        "accountId", row.accountId(),
                        "relationshipId", created.id()))),
                now);

        return new RowResult(
                rowNumber,
                row.personKey(),
                "SUCCEEDED",
                contactId,
                created.id(),
                null,
                null);
    }

    private UUID resolveContactId(
            UUID tenantId,
            UUID actorId,
            ImportRow row,
            Map<String, UUID> personKeys) {
        if (row.contactId() != null) {
            relationships.profile(tenantId, row.contactId());
            return row.contactId();
        }
        if (row.personKey() != null && !row.personKey().isBlank()) {
            UUID existing = personKeys.get(normalizeKey(row.personKey()));
            if (existing != null) {
                relationships.profile(tenantId, existing);
                return existing;
            }
        }
        String givenName = requireText(row.givenName(), "givenName");
        ContactRecord created = contacts.create(
                tenantId,
                actorId,
                new CreateContactCommand(
                        null,
                        givenName,
                        emptyToNull(row.familyName()),
                        emptyToNull(row.primaryEmail()),
                        emptyToNull(row.primaryPhone()),
                        defaultText(row.preferredLocale(), "ar-SA"),
                        defaultText(row.timeZone(), "Asia/Riyadh"),
                        row.personOwnerUserId(),
                        defaultText(row.consentSummary(), "UNKNOWN").toUpperCase(Locale.ROOT)));

        boolean extendedProfile = hasText(row.legalName())
                || hasText(row.preferredName())
                || hasText(row.middleName())
                || hasText(row.pronouns())
                || hasText(row.source());
        if (extendedProfile) {
            relationships.updateProfile(
                    tenantId,
                    actorId,
                    created.id(),
                    new UpdateContactProfileCommand(
                            emptyToNull(row.legalName()),
                            emptyToNull(row.preferredName()),
                            givenName,
                            emptyToNull(row.middleName()),
                            emptyToNull(row.familyName()),
                            emptyToNull(row.primaryEmail()),
                            emptyToNull(row.primaryPhone()),
                            defaultText(row.preferredLocale(), "ar-SA"),
                            defaultText(row.timeZone(), "Asia/Riyadh"),
                            emptyToNull(row.pronouns()),
                            row.personOwnerUserId(),
                            defaultText(row.source(), "CRM_RELATIONSHIP_IMPORT"),
                            null),
                    created.version());
        }

        audit.record(
                tenantId,
                actorId,
                "IMPORT_PERSON_CREATE",
                "CONTACT",
                created.id(),
                new AuditChange(null, objectMapper.valueToTree(Map.of(
                        "personKey", row.personKey() == null ? "" : row.personKey(),
                        "contactId", created.id()))),
                Instant.now());
        return created.id();
    }

    private void validateOwner(UUID tenantId, UUID ownerId) {
        if (ownerId != null && !owners.isValidOwner(tenantId, ownerId)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Owner does not belong to the authenticated tenant.");
        }
    }

    private static RowResult failure(int rowNumber, ImportRow row, RuntimeException exception) {
        String code = exception instanceof CrmContractException contract
                ? contract.code().name()
                : "IMPORT_ROW_FAILED";
        String message = exception instanceof CrmContractException contract
                ? contract.userMessage()
                : safeMessage(exception);
        return new RowResult(
                rowNumber,
                row == null ? null : row.personKey(),
                "FAILED",
                row == null ? null : row.contactId(),
                null,
                code,
                message);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) return "Import row failed.";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    field + " is required when contactId is not supplied.");
        }
        return value.trim();
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record ImportRow(
            String personKey,
            UUID contactId,
            String legalName,
            String preferredName,
            String givenName,
            String middleName,
            String familyName,
            String primaryEmail,
            String primaryPhone,
            String preferredLocale,
            String timeZone,
            String pronouns,
            UUID personOwnerUserId,
            String source,
            String consentSummary,
            UUID accountId,
            String roleCode,
            UUID customRoleId,
            boolean primaryRelationship,
            LocalDate validFrom,
            LocalDate validTo,
            String jobTitle,
            String department,
            String decisionAuthority,
            UUID relationshipOwnerUserId) {}

    public record RowResult(
            int rowNumber,
            String personKey,
            String status,
            UUID contactId,
            UUID relationshipId,
            String errorCode,
            String errorMessage) {}

    public record ImportResult(
            UUID importId,
            int totalRows,
            int succeededRows,
            int failedRows,
            List<RowResult> rows) {}
}
