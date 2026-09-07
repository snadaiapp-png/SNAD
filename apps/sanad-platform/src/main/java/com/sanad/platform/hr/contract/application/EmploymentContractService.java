package com.sanad.platform.hr.contract.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.hr.compliance.application.ComplianceEngine;
import com.sanad.platform.hr.compliance.application.CountryPolicyResolver;
import com.sanad.platform.hr.compliance.domain.ComplianceDecision;
import com.sanad.platform.hr.compliance.domain.ComplianceDecisionType;
import com.sanad.platform.hr.compliance.domain.ComplianceOperationType;
import com.sanad.platform.hr.compliance.domain.ComplianceResource;
import com.sanad.platform.hr.compliance.domain.CountryOperatingMode;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.compliance.domain.ResolvedCountryPolicy;
import com.sanad.platform.hr.contract.domain.ContractCommandResult;
import com.sanad.platform.hr.contract.domain.EmploymentContract;
import com.sanad.platform.hr.contract.domain.EmploymentContractRepository;
import com.sanad.platform.hr.contract.domain.EmploymentContractStatus;
import com.sanad.platform.hr.contract.domain.EmploymentContractVersion;
import com.sanad.platform.integration.events.DomainEventEnvelope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Employment contract service (WS6 Task 2).
 *
 * <p>Command order per the plan: CountryPolicyResolver →
 * CountryContractTermsValidator → ComplianceEngine → Scoped Authorization →
 * mutation with transactional audit/outbox. Contract amendment ALWAYS creates
 * a new version; historical effective terms are never overwritten. In Global
 * Mode, generic terms may be stored but the response exposes
 * {@code LOCAL_COMPLIANCE_UNVERIFIED} — statutory correctness is not claimed.
 * No hard-coded statutory formulas live here.</p>
 */
@Service
public class EmploymentContractService {

    public static final String OP_CREATE = "HRM.CONTRACT.CREATE";
    public static final String OP_AMEND = "HRM.CONTRACT.AMEND";
    public static final String OP_ACTIVATE = "HRM.CONTRACT.ACTIVATE";
    public static final String OP_TERMINATE = "HRM.CONTRACT.TERMINATE";

    public static final String EVENT_CREATED = "HRM.CONTRACT.CREATED.v1";
    public static final String EVENT_AMENDED = "HRM.CONTRACT.AMENDED.v1";
    public static final String EVENT_ACTIVATED = "HRM.CONTRACT.ACTIVATED.v1";
    public static final String EVENT_TERMINATED = "HRM.CONTRACT.TERMINATED.v1";

    private final EmploymentContractRepository repository;
    private final CountryPolicyResolver countryPolicyResolver;
    private final ComplianceEngine complianceEngine;
    private final CountryContractTermsValidator termsValidator;
    private final ContractAuthorizationPort authorizationPort;
    private final ObjectMapper objectMapper;

    @Autowired
    public EmploymentContractService(
            EmploymentContractRepository repository,
            CountryPolicyResolver countryPolicyResolver,
            ComplianceEngine complianceEngine,
            CountryContractTermsValidator termsValidator,
            ContractAuthorizationPort authorizationPort,
            ObjectMapper objectMapper) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.countryPolicyResolver = Objects.requireNonNull(countryPolicyResolver, "countryPolicyResolver");
        this.complianceEngine = Objects.requireNonNull(complianceEngine, "complianceEngine");
        this.termsValidator = Objects.requireNonNull(termsValidator, "termsValidator");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** Commands (WS6 Task 2 interfaces). */
    public record CreateContractCommand(
            UUID employmentId, String contractNumber, boolean isPrimary, String contractTermType,
            LocalDate contractStartDate, LocalDate contractEndDate, LocalDate effectiveDate,
            String documentReference, JsonNode countryTerms) {
    }

    public record AmendContractCommand(
            String contractTermType, LocalDate contractStartDate, LocalDate contractEndDate,
            LocalDate effectiveDate, String documentReference, JsonNode countryTerms, String reasonCode) {
    }

    public ContractCommandResult createDraft(HrCommandContext ctx, CreateContractCommand command) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(command, "command");
        ResolvedCountryPolicy policy = resolveAndValidate(ctx, command.effectiveDate(), command.countryTerms());
        ComplianceDecision decision = evaluate(ctx, OP_CREATE, command.effectiveDate(), command.employmentId());

        UUID contractId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        EmploymentContract contract = new EmploymentContract(contractId, ctx.tenantId(), command.employmentId(),
                command.contractNumber(), command.isPrimary(), null, Instant.now());
        EmploymentContractVersion version = new EmploymentContractVersion(versionId, ctx.tenantId(), contractId,
                command.employmentId(), 1, EmploymentContractStatus.DRAFT, command.isPrimary(),
                command.contractTermType(), command.contractStartDate(), command.contractEndDate(),
                command.effectiveDate(), null, command.documentReference(), command.countryTerms(),
                ctx.actorUserId(), Instant.now());

        String eventType = EVENT_CREATED;
        repository.createContractWithEvidence(contract, version,
                auditRecord(ctx, OP_CREATE, contractId, "CREATE", version),
                envelope(ctx, eventType, contractId, command.effectiveDate(), version, decision));
        return result(version, decision, "CONTRACT_CREATED");
    }

    public ContractCommandResult amend(HrCommandContext ctx, UUID contractId, AmendContractCommand command) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(command, "command");
        authorizationPort.requireManage(ctx, contractId);

        EmploymentContractVersion current = repository
                .findActivePrimaryVersion(ctx.tenantId(), contractEmploymentId(ctx.tenantId(), contractId), command.effectiveDate())
                .orElseThrow(() -> new IllegalStateException("HRM_CONTRACT_NO_ACTIVE_VERSION: contract " + contractId
                        + " has no active version at " + command.effectiveDate()));
        int nextNumber = repository.findVersions(ctx.tenantId(), contractId).size() + 1;

        ResolvedCountryPolicy policy = resolveAndValidate(ctx, command.effectiveDate(), command.countryTerms());
        ComplianceDecision decision = evaluate(ctx, OP_AMEND, command.effectiveDate(), current.employmentId());

        UUID versionId = UUID.randomUUID();
        EmploymentContractVersion newVersion = new EmploymentContractVersion(versionId, ctx.tenantId(), contractId,
                current.employmentId(), nextNumber, EmploymentContractStatus.DRAFT, current.isPrimary(),
                command.contractTermType(), command.contractStartDate(), command.contractEndDate(),
                command.effectiveDate(), null, command.documentReference(), command.countryTerms(),
                ctx.actorUserId(), Instant.now());

        repository.amendVersionWithEvidence(ctx.tenantId(), contractId, newVersion,
                command.effectiveDate().minusDays(1),
                auditRecord(ctx, OP_AMEND, contractId, command.reasonCode(), newVersion),
                envelope(ctx, EVENT_AMENDED, contractId, command.effectiveDate(), newVersion, decision));
        return result(newVersion, decision, "CONTRACT_AMENDED");
    }

    public ContractCommandResult activate(HrCommandContext ctx, UUID contractId, int versionNumber,
                                          LocalDate effectiveDate) {
        Objects.requireNonNull(ctx, "ctx");
        authorizationPort.requireManage(ctx, contractId);
        UUID employmentId = contractEmploymentId(ctx.tenantId(), contractId);
        ResolvedCountryPolicy policy = countryPolicyResolver.resolve(ctx.tenantId(), employmentId, effectiveDate);
        ComplianceDecision decision = evaluate(ctx, OP_ACTIVATE, effectiveDate, employmentId);

        EmploymentContractVersion version = repository.findVersionByNumber(ctx.tenantId(), contractId, versionNumber)
                .orElseThrow(() -> new IllegalStateException("HRM_CONTRACT_VERSION_NOT_FOUND: contract " + contractId
                        + " version " + versionNumber));
        termsValidator.validate(policy, version.countryTerms());

        repository.activateVersionWithEvidence(ctx.tenantId(), contractId, versionNumber, effectiveDate, null,
                auditRecord(ctx, OP_ACTIVATE, contractId, "ACTIVATE", version),
                envelope(ctx, EVENT_ACTIVATED, contractId, effectiveDate, version, decision));
        EmploymentContractVersion activated = toStatus(version, EmploymentContractStatus.ACTIVE, effectiveDate);
        return result(activated, decision, "CONTRACT_ACTIVATED");
    }

    public ContractCommandResult terminate(HrCommandContext ctx, UUID contractId, LocalDate effectiveDate,
                                           String reasonCode) {
        Objects.requireNonNull(ctx, "ctx");
        authorizationPort.requireManage(ctx, contractId);
        UUID employmentId = contractEmploymentId(ctx.tenantId(), contractId);
        ComplianceDecision decision = evaluate(ctx, OP_TERMINATE, effectiveDate, employmentId);

        EmploymentContractVersion version = repository
                .findActivePrimaryVersion(ctx.tenantId(), employmentId, effectiveDate)
                .orElseThrow(() -> new IllegalStateException("HRM_CONTRACT_NO_ACTIVE_VERSION: contract " + contractId
                        + " has no active version at " + effectiveDate));

        repository.terminateVersionWithEvidence(ctx.tenantId(), contractId, effectiveDate,
                auditRecord(ctx, OP_TERMINATE, contractId, reasonCode, version),
                envelope(ctx, EVENT_TERMINATED, contractId, effectiveDate, version, decision));
        EmploymentContractVersion terminated = toStatus(version, EmploymentContractStatus.TERMINATED, effectiveDate);
        return result(terminated, decision, "CONTRACT_TERMINATED");
    }

    // ==================== helpers ====================

    private ResolvedCountryPolicy resolveAndValidate(HrCommandContext ctx, LocalDate effectiveDate,
                                                     JsonNode countryTerms) {
        ResolvedCountryPolicy policy = countryPolicyResolver.resolve(ctx.tenantId(), ctx.employmentId(), effectiveDate);
        termsValidator.validate(policy, countryTerms);
        return policy;
    }

    private ComplianceDecision evaluate(HrCommandContext ctx, String operationCode, LocalDate effectiveDate,
                                        UUID employmentId) {
        HrCommandContext scoped = new HrCommandContext(ctx.tenantId(), employmentId, ctx.actorUserId(),
                ctx.correlationId());
        return complianceEngine.evaluate(scoped, operationCode, ComplianceOperationType.GENERIC_HR,
                effectiveDate, new ComplianceResource("HR_EMPLOYMENT_CONTRACT", ctx.employmentId() != null
                        ? ctx.employmentId() : employmentId));
    }

    private UUID contractEmploymentId(UUID tenantId, UUID contractId) {
        return repository.findContract(tenantId, contractId)
                .orElseThrow(() -> new IllegalStateException("HRM_CONTRACT_NOT_FOUND: " + contractId))
                .employmentId();
    }

    private HrAuditRecord auditRecord(HrCommandContext ctx, String action, UUID contractId,
                                      String reason, EmploymentContractVersion version) {
        ObjectNode after = objectMapper.createObjectNode();
        after.put("contractId", contractId.toString());
        after.put("versionNumber", version.versionNumber());
        after.put("status", version.status().name());
        after.put("effectiveFrom", version.effectiveFrom().toString());
        // No compensation amounts, no restricted PII — identifiers only.
        return new HrAuditRecord(ctx.tenantId(), ctx.actorUserId(), action, "HR_EMPLOYMENT_CONTRACT",
                contractId, null, null, "OPERATIONAL", reason, null, after, "SUCCESS",
                ctx.correlationId(), null, Instant.now());
    }

    private DomainEventEnvelope envelope(HrCommandContext ctx, String eventType, UUID contractId,
                                         LocalDate effectiveDate, EmploymentContractVersion version,
                                         ComplianceDecision decision) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("contractId", contractId.toString());
        payload.put("employmentId", version.employmentId().toString());
        payload.put("versionNumber", version.versionNumber());
        payload.put("status", version.status().name());
        payload.put("effectiveDate", effectiveDate.toString());
        if (decision != null && decision.packCode() != null) {
            payload.put("packCode", decision.packCode());
            payload.put("packVersion", decision.packVersion());
        }
        UUID eventId = UUID.nameUUIDFromBytes(
                (eventType + ":" + contractId + ":" + version.versionNumber() + ":" + effectiveDate)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new DomainEventEnvelope(eventId, eventType, 1, "HR_EMPLOYMENT_CONTRACT", contractId,
                ctx.tenantId(), null, ctx.actorUserId(), Instant.now(), ctx.correlationId(), null,
                eventType + ":" + contractId + ":" + version.versionNumber(), "OPERATIONAL", payload);
    }

    private EmploymentContractVersion toStatus(EmploymentContractVersion version, EmploymentContractStatus status,
                                               LocalDate effectiveFrom) {
        return new EmploymentContractVersion(version.id(), version.tenantId(), version.contractId(),
                version.employmentId(), version.versionNumber(), status, version.isPrimary(),
                version.contractTermType(), version.contractStartDate(), version.contractEndDate(),
                effectiveFrom, version.effectiveTo(), version.documentReference(), version.countryTerms(),
                version.createdBy(), version.createdAt());
    }

    private ContractCommandResult result(EmploymentContractVersion version, ComplianceDecision decision,
                                         String reasonCode) {
        if (decision != null && decision.type() == ComplianceDecisionType.GLOBAL_MODE_ALLOWED) {
            return ContractCommandResult.global(version, reasonCode);
        }
        if (decision != null && decision.packCode() != null) {
            return ContractCommandResult.localized(version, decision.packCode(), decision.packVersion(), reasonCode);
        }
        return ContractCommandResult.global(version, reasonCode);
    }
}
