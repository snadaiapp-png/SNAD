package com.sanad.platform.hr.recruitment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sanad.platform.hr.assignment.application.HrAssignmentService;
import com.sanad.platform.hr.assignment.domain.AssignmentType;
import com.sanad.platform.hr.assignment.domain.OccupancyMode;
import com.sanad.platform.hr.compensation.application.CompensationService;
import com.sanad.platform.hr.compensation.domain.CompensationComponent;
import com.sanad.platform.hr.compensation.domain.CompensationComponentType;
import com.sanad.platform.hr.compliance.application.ComplianceEngine;
import com.sanad.platform.hr.compliance.application.CountryPolicyResolver;
import com.sanad.platform.hr.compliance.application.WorkerClassificationResolver;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.contract.application.EmploymentContractService;
import com.sanad.platform.hr.contract.domain.ContractCommandResult;
import com.sanad.platform.hr.employment.HrEmploymentV2Service;
import com.sanad.platform.hr.identity.HrPersonService;
import com.sanad.platform.hr.identity.PersonIdentifier;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrHireConversionRepository;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrHireConversionRepository.HireConversionLedgerRow;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrHireConversionRepository.HireOfferRow;
import com.sanad.platform.hr.recruitment.infrastructure.JdbcHrHireConversionRepository.HireOpeningRow;
import com.sanad.platform.security.crypto.PlatformCryptographyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * HRM-G1 T8 — {@code HrHireConversionService}: the ATOMIC, IDEMPOTENT,
 * AUDITABLE, TENANT_SCOPED, RETRY_SAFE, G0-AUTHORITY-PRESERVING
 * candidate → hire boundary (design §7; directive T8.1–T8.14).
 *
 * <p>ONE governed transaction (READ COMMITTED, explicit locks, no async, no
 * nested transactions) executes the §7.1 sequence:</p>
 * <ol>
 *   <li>lock + load the ACCEPTED offer (tenant-scoped FOR UPDATE);</li>
 *   <li>ledger check (COMPLETE ⇒ original result, replayed=true);</li>
 *   <li>idempotency admission (same key + different payload ⇒ conflict);</li>
 *   <li>person resolution via blind-index claims — link / create / FAIL
 *       CLOSED on ambiguity (email-only duplicates are NEVER merged);</li>
 *   <li>canonical employment through the G0 write path;</li>
 *   <li>assignment with the IN-TRANSACTION position occupancy re-check;</li>
 *   <li>versioned contract draft from the offer terms (G0 services);</li>
 *   <li>compensation mirror (G0 services);</li>
 *   <li>onboarding plan + checklist + tasks (template failures abort);</li>
 *   <li>ledger row COMPLETE (the UNIQUE (tenant, offer) key serializes
 *       concurrent conversions: the loser replays the winner's result);</li>
 *   <li>application → HIRED (conditional) + opening headcount derived
 *       increment (guarded);</li>
 *   <li>audit + HIRE_COMPLETED + PLAN_CREATED outbox, same transaction;</li>
 *   <li>COMMIT — no IAM call inside the boundary (§7.2).</li>
 * </ol>
 *
 * <p>G0 authority is preserved: canonical Person/Employment/Assignment/
 * Contract/Compensation writes flow EXCLUSIVELY through the G0 services'
 * connection-scoped variants; G1 conversion code contains no canonical SQL
 * (pinned by {@code HrHireConversionArchitectureBoundaryTest}).</p>
 */
@Service
public class HrHireConversionService {

    private static final String APPLICATION_STATE_OFFER = "OFFER";
    private static final String OPERATION_CODE = "HRM.RECRUITMENT.HIRE_CONVERT";

    private final JdbcHrHireConversionRepository repository;
    private final RecruitmentAuthorizationPort authorization;
    private final HireApprovalWorkflowPort hireApprovalWorkflow;
    private final HrPersonService personService;
    private final HrEmploymentV2Service employmentService;
    private final HrAssignmentService assignmentService;
    private final EmploymentContractService contractService;
    private final CompensationService compensationService;
    private final PlatformCryptographyService crypto;
    private final WorkerClassificationResolver workerClassificationResolver;
    private final DataSource dataSource;

    @Autowired
    public HrHireConversionService(JdbcHrHireConversionRepository repository,
                                   RecruitmentAuthorizationPort authorization,
                                   HireApprovalWorkflowPort hireApprovalWorkflow,
                                   HrPersonService personService,
                                   HrEmploymentV2Service employmentService,
                                   HrAssignmentService assignmentService,
                                   EmploymentContractService contractService,
                                   CompensationService compensationService,
                                   PlatformCryptographyService crypto,
                                   WorkerClassificationResolver workerClassificationResolver,
                                   DataSource dataSource) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.hireApprovalWorkflow = Objects.requireNonNull(hireApprovalWorkflow, "hireApprovalWorkflow");
        this.personService = Objects.requireNonNull(personService, "personService");
        this.employmentService = Objects.requireNonNull(employmentService, "employmentService");
        this.assignmentService = Objects.requireNonNull(assignmentService, "assignmentService");
        this.contractService = Objects.requireNonNull(contractService, "contractService");
        this.compensationService = Objects.requireNonNull(compensationService, "compensationService");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.workerClassificationResolver = Objects.requireNonNull(workerClassificationResolver,
                "workerClassificationResolver");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    // ==================== the governed command ====================

    public HireConversionResult convert(HrCommandContext ctx, UUID offerId, HireConversionCommand command) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(command, "command");
        // §T8.12 — HIRE.CONVERT gates the command itself.
        authorization.requireHireConvert(ctx, offerId);

        // §T8.8 — the optional hire approval is a PRECONDITION, resolved from
        // the authoritative tenant policy; a missing policy row stays OFF.
        if (repository.isHireApprovalPolicyEnabled(ctx.tenantId())) {
            hireApprovalGate(ctx, offerId);
        }

        // §7.1 step 2 — the fast replay path reads the committed ledger
        // before opening the governed transaction (no writes on this path).
        Optional<HireConversionLedgerRow> committed = repository.findLedger(ctx.tenantId(), offerId);
        if (committed.isPresent()) {
            return replayResult(ctx, offerId, committed.get());
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, ctx.tenantId());

                // §7.1 step 1 — lock + load the offer.
                HireOfferRow offer = repository.lockOfferWithinTransaction(connection, ctx.tenantId(), offerId)
                        .orElseThrow(() -> new IllegalStateException("HRM_TENANT_CONTEXT_MISMATCH: offer "
                                + offerId + " is not visible to this tenant context (§7.3 case 9 — "
                                + "fail-closed before any write)"));
                if (offer.state() != com.sanad.platform.hr.recruitment.domain.HrOfferState.ACCEPTED) {
                    throw new IllegalStateException("HRM_OFFER_STATE_CONFLICT: conversion requires the offer "
                            + "to be ACCEPTED (was " + offer.state() + ")");
                }

                // §7.1 step 2 — in-transaction ledger check (post-lock).
                Optional<HireConversionLedgerRow> ledger =
                        repository.findLedgerWithinTransaction(connection, ctx.tenantId(), offerId);
                if (ledger.isPresent()) {
                    HireConversionResult replay = replayResult(ctx, offerId, ledger.get());
                    connection.rollback(); // read-only path: nothing to commit
                    return replay;
                }

                // §7.1 step 3 — idempotency admission.
                String fingerprint = fingerprint(command);
                admitIdempotencyWithinTransaction(connection, ctx, command, fingerprint);

                // §7.1 step 1b — application consistency (OFFER, not yet HIRED).
                String applicationState = repository.applicationStateWithinTransaction(
                        connection, ctx.tenantId(), offer.applicationId());
                if (!APPLICATION_STATE_OFFER.equals(applicationState)) {
                    throw new IllegalStateException("HRM_APPLICATION_STAGE_CONFLICT: conversion requires the "
                            + "application in stage OFFER (was " + applicationState + ")");
                }

                // §7.1 step 4 — person resolution (link / create / fail closed).
                PersonResolution person = resolvePerson(connection, ctx, offer.candidateId(),
                        command.identityClaims());

                // §7.1 step 5 — canonical employment (G0 write path).
                String employeeNumber = "EMP-" + UUID.randomUUID().toString()
                        .replace("-", "").substring(0, 12).toUpperCase();
                var employment = employmentService.createWithinTransaction(connection, ctx.tenantId(),
                        new com.sanad.platform.hr.api.v2.dto.CreateEmploymentRequest(
                                person.personId(), command.legalEntityId(), employeeNumber,
                                command.employmentStartDate(), command.laborJurisdictionCode(),
                                command.workerClassificationCode()));

                // §7.1 step 6 — assignment with in-transaction occupancy re-check.
                HireOpeningRow opening = repository.resolveOpeningWithinTransaction(
                        connection, ctx.tenantId(), openingIdOf(connection, ctx.tenantId(), offer.applicationId()));
                UUID positionId = command.positionId() != null
                        ? command.positionId()
                        : (opening != null ? opening.positionId() : null);
                UUID organizationId = opening != null ? opening.organizationId() : null;
                if (organizationId == null) {
                    throw new IllegalStateException("HRM_HIRE_OPENING_UNRESOLVABLE: the offer's opening could "
                            + "not be resolved in this tenant");
                }
                BigDecimal allocation = command.allocationPercent() == null
                        ? new BigDecimal("100") : command.allocationPercent();
                com.sanad.platform.hr.assignment.domain.HrAssignment assignment;
                try {
                    assignment = assignmentService.createAssignmentWithinTransaction(
                            connection, ctx.tenantId(), employment.id(), organizationId, null,
                            positionId, null, null,
                            AssignmentType.PRIMARY,
                            positionId != null ? OccupancyMode.OCCUPYING : OccupancyMode.NON_OCCUPYING,
                            allocation, command.employmentStartDate(), null);
                } catch (IllegalStateException occupancyConflict) {
                    String occupancyMessage = occupancyConflict.getMessage() == null
                            ? "" : occupancyConflict.getMessage();
                    if (occupancyMessage.contains("occupancy") || occupancyMessage.contains("ex_hr_assignments")) {
                        // §T8.5/§15: in-transaction over-occupancy — the whole
                        // hire rolls back (fail-closed, no stale snapshot).
                        throw new IllegalStateException(
                                "HRM_CONVERSION_POSITION_OVER_OCCUPANCY: the position is already occupied "
                                        + "for the requested effective period; over-occupancy rolls back "
                                        + "the entire conversion");
                    }
                    throw occupancyConflict;
                }
                if (positionId != null) {
                    // §T8.5: the G0 occupancy validation already re-checked the
                    // position inside this transaction; over-occupancy raised
                    // CONVERSION_POSITION_OVER_OCCUPANCY and rolls everything back.
                }

                // §7.1 step 7 — versioned contract draft from the offer terms.
                OfferTerms terms = readOfferTerms(connection, ctx.tenantId(), offer.currentVersionId());
                var contract = contractService.createDraftWithinTransaction(
                        scopedContractContext(ctx, employment.id()),
                        new EmploymentContractService.CreateContractCommand(
                                employment.id(), resolveContractNumber(command),
                                true, terms.contractTermType(), terms.contractStartDate(),
                                terms.contractEndDate(), command.employmentStartDate(),
                                terms.documentReference(), null),
                        txPolicyResolver(connection), txComplianceEngine(connection),
                        connection);

                // §7.1 step 8 — compensation mirror.
                var package_ = compensationService.createPackageWithinTransaction(
                        scopedContractContext(ctx, employment.id()),
                        new CompensationService.CreateCompensationCommand(
                                employment.id(), terms.currency(), terms.payFrequency(),
                                command.employmentStartDate(), terms.components()),
                        connection);

                // §7.1 step 9 — onboarding plan + checklist + tasks (mandatory).
                UUID planId = repository.materializeOnboardingPlanWithinTransaction(
                        connection, ctx.tenantId(), employment.id(), ctx.actorUserId(), ctx.correlationId());

                // §7.1 step 10 — ledger row COMPLETE. The unique key serializes
                // the concurrent race: the loser rolls back and replays the winner.
                UUID ledgerId;
                try {
                    ledgerId = repository.insertLedgerWithinTransaction(connection, ctx.tenantId(), offerId,
                            offer.applicationId(), person.personId(), person.personReused(), employeeNumber,
                            employment.id(), assignment.id(), contractResultId(contract),
                            packageResultId(package_), planId);
                } catch (JdbcHrHireConversionRepository.HireConversionLedgerConflict conflict) {
                    connection.rollback();
                    return replayCommittedWinner(ctx, offerId);
                }
                Objects.requireNonNull(ledgerId);

                // §7.1 step 11 — application → HIRED + derived headcount.
                repository.markApplicationHiredWithinTransaction(connection, ctx.tenantId(),
                        offer.applicationId(), ctx.actorUserId(), ctx.correlationId());
                if (opening != null) {
                    repository.incrementOpeningHeadcountWithinTransaction(connection, ctx.tenantId(),
                            opening.id(), ctx.actorUserId(), ctx.correlationId());
                }

                // §7.1 step 12 — conversion audit + HIRE_COMPLETED outbox.
                repository.writeHireCompletedWithinTransaction(connection, ctx.tenantId(), offerId,
                        offer.applicationId(), person.personId(), person.personReused(), employeeNumber,
                        employment.id(), assignment.id(), contractResultId(contract),
                        packageResultId(package_), planId, ctx.actorUserId(), ctx.correlationId());

                // §7.1 step 3 — persist the idempotency admission (same tx).
                completeIdempotencyWithinTransaction(connection, ctx, command, fingerprint,
                        employment.id(), planId);

                connection.commit();
                return new HireConversionResult(offerId, offer.applicationId(), person.personId(),
                        person.personReused(), employeeNumber, employment.id(), assignment.id(),
                        contractResultId(contract), packageResultId(package_), planId, false);
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException rb) {
                    e.addSuppressed(rb);
                }
                throw e instanceof RuntimeException re ? re : new IllegalStateException(
                        "HRM_HIRE_CONVERSION_FAILED: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_HIRE_CONVERSION_FAILED: " + e.getMessage(), e);
        }
    }

    // ==================== §7.1 step 4 — person resolution ====================

    private record PersonResolution(UUID personId, boolean personReused) {
    }

    /**
     * §7.1 step 4 / §T8.4 — blind-index resolution with the fail-closed
     * ambiguity rule: strong verified claims may LINK an existing Person;
     * an EMAIL match alone (or conflicting claims) NEVER merges identities.
     */
    private PersonResolution resolvePerson(Connection connection, HrCommandContext ctx, UUID candidateId,
                                           List<HireConversionCommand.IdentityClaim> claims) throws SQLException {
        if (candidateId == null) {
            throw new IllegalStateException("HRM_APPLICATION_STAGE_CONFLICT: the offer does not correlate "
                    + "a candidate; conversion requires a recruitment-sourced offer");
        }
        CandidateRow candidate = loadCandidate(connection, ctx.tenantId(), candidateId);

        UUID strongMatch = null;
        boolean emailOnlyClaimPresent = false;
        for (HireConversionCommand.IdentityClaim claim : claims) {
            String type = claim.identifierType().trim().toUpperCase();
            if (HireConversionCommand.EMAIL_IDENTIFIER_TYPE.equals(type)) {
                // §T8.4 case C / §7.3 case 2: email is NEVER a linking key.
                // The G0 identity store does not accept EMAIL as a verified
                // identifier (DB CHECK) — an email-only resolution request is
                // refused for human review instead of silently merging.
                emailOnlyClaimPresent = true;
                continue;
            }
            Optional<PersonIdentifier> match = personService.findExactIdentifierMatchWithinTransaction(
                    connection, ctx.tenantId(), type, claim.issuingCountryCode(), claim.value());
            if (match.isEmpty()) {
                continue;
            }
            if (HireConversionCommand.STRONG_IDENTIFIER_TYPES.contains(type)) {
                UUID matchedPerson = match.get().personId();
                if (strongMatch != null && !strongMatch.equals(matchedPerson)) {
                    throw ambiguous("conflicting verified identity claims resolve to different Persons");
                }
                strongMatch = matchedPerson;
            }
        }

        // §T8.4 case C: an email-only duplicate is NOT an automatic link.
        if (strongMatch == null && emailOnlyClaimPresent) {
            throw ambiguous("an email claim alone can never resolve a Person; identity ambiguity "
                    + "requires human review (no silent merge)");
        }

        if (strongMatch != null) {
            return new PersonResolution(strongMatch, true); // §T8.4 case A: reuse
        }

        // §T8.4 case B: create through the G0 person authority.
        String displayName = candidate.displayName() == null ? "Hire" : candidate.displayName().trim();
        String firstName = firstToken(displayName);
        String lastName = lastToken(displayName);
        var created = personService.createPersonWithinTransaction(connection, ctx.tenantId(),
                firstName, null, lastName);
        storeIdentityClaims(connection, ctx.tenantId(), created.id(), claims,
                candidate.emailCiphertext());
        return new PersonResolution(created.id(), false);
    }

    private void storeIdentityClaims(Connection connection, UUID tenantId, UUID personId,
                                     List<HireConversionCommand.IdentityClaim> claims,
                                     String candidateEmailCiphertext) {
        for (HireConversionCommand.IdentityClaim claim : claims) {
            String type = claim.identifierType().trim().toUpperCase();
            if (HireConversionCommand.EMAIL_IDENTIFIER_TYPE.equals(type)
                    || !HireConversionCommand.STRONG_IDENTIFIER_TYPES.contains(type)) {
                // The G0 identity store only accepts verified government
                // identifier types (DB CHECK) — email/other claims are never
                // persisted as person identifiers.
                continue;
            }
            personService.addIdentifierWithinTransaction(connection, tenantId, personId,
                    claim.identifierType(), claim.issuingCountryCode(), claim.value());
        }
    }

    private String decryptCandidateEmail(UUID tenantId, String storedCiphertext) {
        String[] parts = storedCiphertext.split(":", 3);
        String keyVersion = parts.length > 1 ? parts[1] : "v1";
        return crypto.decrypt(tenantId,
                com.sanad.platform.hr.recruitment.application.HrCandidateService.PURPOSE_CONTACT_EMAIL,
                new com.sanad.platform.security.crypto.EncryptedValue(storedCiphertext, keyVersion,
                        "AES-256-GCM"));
    }

    private IllegalStateException ambiguous(String detail) {
        return new IllegalStateException("HRM_CONVERSION_IDENTITY_AMBIGUOUS: " + detail);
    }

    private record CandidateRow(UUID id, String displayName, String emailCiphertext) {
    }

    private CandidateRow loadCandidate(Connection connection, UUID tenantId, UUID candidateId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, display_name, contact_email_ciphertext FROM hr_candidates "
                        + "WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, candidateId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("HRM_CANDIDATE_NOT_FOUND: " + candidateId
                            + " is not visible to this tenant context");
                }
                return new CandidateRow(rs.getObject("id", UUID.class),
                        rs.getString("display_name"),
                        rs.getString("contact_email_ciphertext"));
            }
        }
    }

    // ==================== offer terms (§7.1 steps 7/8) ====================

    private record OfferTerms(String contractTermType, LocalDate contractStartDate, LocalDate contractEndDate,
                              String documentReference, String currency, String payFrequency,
                              List<CompensationComponent> components) {
    }

    private OfferTerms readOfferTerms(Connection connection, UUID tenantId, UUID versionId) throws SQLException {
        if (versionId == null) {
            throw new IllegalStateException("HRM_OFFER_NO_TERMS: the accepted offer has no version");
        }
        String contractTerms;
        String compensation;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT contract_terms::text, compensation::text FROM hr_offer_versions "
                        + "WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, versionId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("HRM_OFFER_NO_TERMS: the offer version is not visible");
                }
                contractTerms = rs.getString(1);
                compensation = rs.getString(2);
            }
        }
        return parseOfferTerms(contractTerms, compensation, tenantId);
    }

    private OfferTerms parseOfferTerms(String contractTerms, String compensation, UUID tenantId) {
        try {
            JsonNode terms = contractTerms == null || contractTerms.isBlank()
                    ? JSON.createObjectNode() : JSON.readTree(contractTerms);
            JsonNode comp = compensation == null || compensation.isBlank()
                    ? JSON.createObjectNode() : JSON.readTree(compensation);

            String termType = terms.path("contractTermType").asText(null);
            String startDateText = terms.path("contractStartDate").asText(null);
            String endDateText = terms.path("contractEndDate").asText(null);
            String documentReference = terms.path("documentReference").asText(null);

            String currency = comp.path("currency").asText(null);
            String payFrequency = comp.path("payFrequency").asText("MONTHLY");
            JsonNode components = comp.path("components");

            List<String> problems = new ArrayList<>();
            if (termType == null || termType.isBlank()) {
                problems.add("contractTermType");
            }
            if (currency == null || currency.isBlank()) {
                problems.add("currency");
            }
            if (!components.isArray() || components.isEmpty()) {
                problems.add("components");
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException("HRM_CONVERSION_OFFER_TERMS_INCOMPLETE: the accepted offer "
                        + "terms lack hire-mandatory fields: " + String.join(", ", problems)
                        + " (§7.1 steps 7/8 — hire-eligible offers carry structured terms)");
            }

            List<CompensationComponent> parsed = new ArrayList<>();
            for (JsonNode c : components) {
                String type = c.path("type").asText(null);
                CompensationComponentType componentType = type == null ? null
                        : safeValueOf(type);
                if (componentType == null) {
                    throw new IllegalStateException("HRM_CONVERSION_OFFER_TERMS_INCOMPLETE: unknown "
                            + "compensation component type " + type);
                }
                BigDecimal amount = c.path("amount").isNumber()
                        ? c.path("amount").decimalValue() : null;
                BigDecimal percentage = c.path("percentage").isNumber()
                        ? c.path("percentage").decimalValue() : null;
                // tenant/package ids are rebound by the G0 newPackage binder;
                // the component constructor requires them non-null.
                parsed.add(new CompensationComponent(UUID.randomUUID(), tenantId, UUID.randomUUID(),
                        componentType, c.path("code").asText(componentType.name()), amount, percentage));
            }

            return new OfferTerms(termType,
                    startDateText == null ? null : LocalDate.parse(startDateText),
                    endDateText == null ? null : LocalDate.parse(endDateText),
                    documentReference, currency, payFrequency, parsed);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("HRM_CONVERSION_OFFER_TERMS_INCOMPLETE: the offer terms are not "
                    + "valid JSON (" + e.getMessage() + ")", e);
        }
    }

    private CompensationComponentType safeValueOf(String type) {
        try {
            return CompensationComponentType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ==================== idempotency admission (§7.1 step 3) ====================

    private void admitIdempotencyWithinTransaction(Connection connection, HrCommandContext ctx,
                                                   HireConversionCommand command, String fingerprint)
            throws SQLException {
        String idempotencyKey = truncate(command.idempotencyKey());
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT request_fingerprint FROM hr_idempotency_records "
                        + "WHERE tenant_id = ? AND principal_id = ? AND operation_code = ? "
                        + "AND idempotency_key = ?")) {
            ps.setObject(1, ctx.tenantId());
            ps.setObject(2, ctx.actorUserId());
            ps.setString(3, OPERATION_CODE);
            ps.setString(4, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && !rs.getString(1).equals(fingerprint)) {
                    throw new IllegalStateException("HRM_IDEMPOTENCY_CONFLICT: the idempotency key was "
                            + "already used with a different payload");
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO hr_idempotency_records (id, tenant_id, principal_id, operation_code, "
                        + "idempotency_key, request_fingerprint) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, ctx.tenantId());
            ps.setObject(3, ctx.actorUserId());
            ps.setString(4, OPERATION_CODE);
            ps.setString(5, idempotencyKey);
            ps.setString(6, fingerprint);
            ps.executeUpdate();
        }
    }

    private void completeIdempotencyWithinTransaction(Connection connection, HrCommandContext ctx,
                                                      HireConversionCommand command, String fingerprint,
                                                      UUID employmentId, UUID planId)
            throws SQLException {
        ObjectNode body = JSON.createObjectNode();
        body.put("employment_id", employmentId.toString());
        body.put("onboarding_plan_id", planId.toString());
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE hr_idempotency_records SET response_status = 200, response_body = ?::jsonb "
                        + "WHERE tenant_id = ? AND principal_id = ? AND operation_code = ? "
                        + "AND idempotency_key = ?")) {
            try {
                ps.setString(1, JSON.writeValueAsString(body));
            } catch (com.fasterxml.jackson.core.JsonProcessingException je) {
                throw new SQLException("idempotency response serialization failed", je);
            }
            ps.setObject(2, ctx.tenantId());
            ps.setObject(3, ctx.actorUserId());
            ps.setString(4, OPERATION_CODE);
            ps.setString(5, truncate(command.idempotencyKey()));
            ps.executeUpdate();
        }
    }

    private String fingerprint(HireConversionCommand command) throws SQLException {
        try {
            ObjectNode node = JSON.createObjectNode();
            node.put("legalEntityId", command.legalEntityId() == null ? null
                    : command.legalEntityId().toString());
            node.put("workerClassificationCode", command.workerClassificationCode());
            node.put("laborJurisdictionCode", command.laborJurisdictionCode());
            node.put("employmentStartDate", command.employmentStartDate() == null ? null
                    : command.employmentStartDate().toString());
            node.put("allocationPercent", command.allocationPercent() == null ? null
                    : command.allocationPercent().toPlainString());
            node.put("positionId", command.positionId() == null ? null : command.positionId().toString());
            node.put("contractNumber", command.contractNumber());
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(JSON.writeValueAsBytes(node));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new SQLException("fingerprint computation failed", e);
        }
    }

    private static String truncate(String value) {
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    // ==================== replay (§7.3 cases 3/4) ====================

    private HireConversionResult replayResult(HrCommandContext ctx, UUID offerId, HireConversionLedgerRow row) {
        if (!"COMPLETE".equals(row.conversionState())) {
            throw new IllegalStateException("HRM_HIRE_CONVERSION_STATE_CONFLICT: the ledger row is "
                    + row.conversionState() + "; a non-COMPLETE conversion cannot be replayed");
        }
        // §7.3 case 3: replay access is audited (own connection — audit-only write).
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                setTenantLocal(connection, ctx.tenantId());
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO hr_audit_ledger (id, tenant_id, actor_user_id, action, resource_type, "
                                + "resource_id, data_classification, reason, before_state, after_state, result, "
                                + "correlation_id, occurred_at) "
                                + "VALUES (?, ?, ?, 'HRM.RECRUITMENT.HIRE_CONVERT_REPLAYED', 'HR_OFFER', ?, "
                                + "'OPERATIONAL', 'IDEMPOTENT_REPLAY', NULL, NULL, 'SUCCESS', ?, NOW())")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, ctx.tenantId());
                    ps.setObject(3, ctx.actorUserId());
                    ps.setObject(4, offerId);
                    ps.setObject(5, ctx.correlationId());
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("HRM_HIRE_REPLAY_AUDIT_FAILED: " + e.getMessage(), e);
        }
        return new HireConversionResult(offerId, row.applicationId(), row.personId(), row.personReused(),
                row.employeeNumber(), row.employmentId(), row.assignmentId(), row.contractId(),
                row.compensationPackageId(), row.onboardingPlanId(), true);
    }

    /** §7.3 case 8 — the racing loser re-reads the committed winner. */
    private HireConversionResult replayCommittedWinner(HrCommandContext ctx, UUID offerId) {
        Optional<HireConversionLedgerRow> winner = repository.findLedger(ctx.tenantId(), offerId);
        if (winner.isEmpty() || !"COMPLETE".equals(winner.get().conversionState())) {
            throw new IllegalStateException("HRM_HIRE_CONVERSION_CONFLICT: a concurrent conversion of offer "
                    + offerId + " is still committing; retry to resolve to the original result");
        }
        return replayResult(ctx, offerId, winner.get());
    }

    // ==================== §T8.8 approval gate ====================

    private void hireApprovalGate(HrCommandContext ctx, UUID offerId) {
        // Delegates to HrHireApprovalLinkService semantics inline to keep the
        // conversion constructor surface minimal: the port's authoritative
        // outcome is the ONLY thing that can authorize a policy-ON conversion.
        java.util.Optional<UUID> latest = hireApprovalWorkflow.findLatestApproval(ctx.tenantId(), offerId);
        if (latest.isEmpty()) {
            UUID instanceId = hireApprovalWorkflow.startHireApproval(ctx.tenantId(), offerId, ctx.actorUserId());
            HireApprovalWorkflowPort.ApprovalSnapshot snapshot =
                    hireApprovalWorkflow.loadHireApprovalOutcome(ctx.tenantId(), instanceId);
            if (snapshot == null
                    || !HireApprovalWorkflowPort.BUSINESS_ENTITY_TYPE.equals(snapshot.businessEntityType())
                    || !offerId.equals(snapshot.businessEntityId())) {
                throw new IllegalStateException("HRM_HIRE_APPROVAL_LINK_INVALID: the started approval does "
                        + "not correlate this tenant and offer");
            }
            throw new IllegalStateException("HRM_HIRE_APPROVAL_REQUIRED: hire approval is enabled for this "
                    + "tenant; the authoritative Workflow Y2 approval must reach APPROVED before the "
                    + "conversion can run");
        }
        HireApprovalWorkflowPort.ApprovalSnapshot snapshot =
                hireApprovalWorkflow.loadHireApprovalOutcome(ctx.tenantId(), latest.get());
        if (snapshot == null
                || !HireApprovalWorkflowPort.BUSINESS_ENTITY_TYPE.equals(snapshot.businessEntityType())
                || !offerId.equals(snapshot.businessEntityId())) {
            throw new IllegalStateException("HRM_HIRE_APPROVAL_LINK_INVALID: the stored approval does not "
                    + "correlate this tenant and offer");
        }
        if (snapshot.outcome() == HireApprovalWorkflowPort.ApprovalOutcome.APPROVED) {
            return;
        }
        if ("COMPLETED".equals(snapshot.status())) {
            throw new IllegalStateException("HRM_HIRE_APPROVAL_NOT_APPROVED: the authoritative hire approval "
                    + "resolved to " + snapshot.outcome() + "; the conversion is refused fail-closed");
        }
        throw new IllegalStateException("HRM_HIRE_APPROVAL_PENDING: the authoritative hire approval is "
                + snapshot.status() + "; no conversion without a final APPROVED outcome");
    }

    // ==================== tx-scoped G0 compliance chain ====================

    private CountryPolicyResolver txPolicyResolver(Connection connection) {
        return new CountryPolicyResolver(txJdbc(connection),
                new WorkerClassificationResolver(txJdbc(connection)));
    }

    private ComplianceEngine txComplianceEngine(Connection connection) {
        JdbcTemplate txJdbc = txJdbc(connection);
        return new ComplianceEngine(
                new CountryPolicyResolver(txJdbc, new WorkerClassificationResolver(txJdbc)),
                List.of(), new com.sanad.platform.hr.compliance.infrastructure.JdbcComplianceDecisionRepository(
                txJdbc));
    }

    private JdbcTemplate txJdbc(Connection connection) {
        SingleConnectionDataSource txDs = new SingleConnectionDataSource(connection, true);
        txDs.setAutoCommit(false);
        txDs.setSuppressClose(true);
        return new JdbcTemplate(txDs);
    }

    private HrCommandContext scopedContractContext(HrCommandContext ctx, UUID employmentId) {
        return new HrCommandContext(ctx.tenantId(), employmentId, ctx.actorUserId(), ctx.correlationId());
    }

    private UUID contractResultId(ContractCommandResult result) {
        return result.version() == null ? null : result.version().contractId();
    }

    private UUID packageResultId(com.sanad.platform.hr.compensation.domain.CompensationPackage pkg) {
        return pkg == null ? null : pkg.id();
    }

    private UUID openingIdOf(Connection connection, UUID tenantId, UUID applicationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT job_opening_id FROM hr_applications WHERE id = ? AND tenant_id = ?")) {
            ps.setObject(1, applicationId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getObject(1) == null) {
                    throw new IllegalStateException("HRM_HIRE_OPENING_UNRESOLVABLE: the application does not "
                            + "correlate a job opening");
                }
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private String resolveContractNumber(HireConversionCommand command) {
        if (command.contractNumber() != null && !command.contractNumber().isBlank()) {
            return command.contractNumber();
        }
        return "CTR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String firstToken(String displayName) {
        String[] parts = displayName.split("\\s+");
        return parts.length == 0 || parts[0].isBlank() ? "Hire" : parts[0];
    }

    private static String lastToken(String displayName) {
        String[] parts = displayName.split("\\s+");
        return parts.length < 2 ? "Member" : parts[parts.length - 1];
    }

    private static void setTenantLocal(Connection connection, UUID tenantId) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();
}
