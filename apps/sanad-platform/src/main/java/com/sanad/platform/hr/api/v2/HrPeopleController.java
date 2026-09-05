package com.sanad.platform.hr.api.v2;

import com.sanad.platform.hr.api.v2.dto.AddIdentifierRequest;
import com.sanad.platform.hr.api.v2.dto.CreatePersonRequest;
import com.sanad.platform.hr.api.v2.dto.IdentifierMetadataResponse;
import com.sanad.platform.hr.api.v2.dto.LinkUserRequest;
import com.sanad.platform.hr.api.v2.dto.PatchPersonPrivateRequest;
import com.sanad.platform.hr.api.v2.dto.PatchPersonRequest;
import com.sanad.platform.hr.api.v2.dto.PersonLinkResponse;
import com.sanad.platform.hr.api.v2.dto.PersonPrivateMutationResponse;
import com.sanad.platform.hr.api.v2.dto.PersonPrivateResponse;
import com.sanad.platform.hr.api.v2.dto.PersonSummaryResponse;
import com.sanad.platform.hr.identity.HrPerson;
import com.sanad.platform.hr.identity.HrPersonPrivate;
import com.sanad.platform.hr.identity.HrPersonV2Service;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — canonical People v2 endpoints (9 operations).
 *
 * <p>Thin typed adapters over the identity application service:
 * coarse capability gate → canonical resource resolution → optimistic
 * concurrency check → idempotent command execution (critical POSTs) →
 * typed response. Capability boundaries are independent per operation:
 * directory reads/writes gate on HRM.EMPLOYEE.*, private PII on
 * HRM.PII.VIEW / HRM.PII.MANAGE, identity linking on HRM.USER_LINK.MANAGE.
 * Tenant identity comes exclusively from the security context; tenant
 * isolation is additionally enforced by fail-closed RLS underneath.
 *
 * <p>The private PII read is a RESTRICTED read: the application service
 * appends the immutable sensitive-read audit row in the same transaction
 * as the read (fail closed) before any PII can reach the client.
 */
@RestController
@RequestMapping("/api/v2/hr")
public class HrPeopleController {

    private static final String OPERATION_PREFIX = "hr.v2.people";

    private final HrPersonV2Service service;
    private final HrmIdempotentCommandExecutor idempotentCommands;

    public HrPeopleController(HrPersonV2Service service,
                              HrmIdempotentCommandExecutor idempotentCommands) {
        this.service = service;
        this.idempotentCommands = idempotentCommands;
    }

    // ==================== DIRECTORY (safe surface) ====================

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPeopleList")
    @GetMapping("/people")
    @RequireCapability("HRM.EMPLOYEE.VIEW")
    public List<PersonSummaryResponse> list(Authentication authentication) {
        return service.listPeople(SecurityContextUtils.tenantId(authentication)).stream()
                .map(PersonSummaryResponse::from)
                .toList();
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPeopleCreate")
    @PostMapping("/people")
    @RequireCapability("HRM.EMPLOYEE.CREATE")
    public ResponseEntity<PersonSummaryResponse> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePersonRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".create",
                String.valueOf(request));
        PersonSummaryResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".create", idempotencyKey, fingerprint,
                PersonSummaryResponse.class,
                () -> createSummary(tenantId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPeopleGet")
    @GetMapping("/people/{personId}")
    @RequireCapability("HRM.EMPLOYEE.VIEW")
    public PersonSummaryResponse get(Authentication authentication, @PathVariable UUID personId) {
        return PersonSummaryResponse.from(
                service.getPerson(SecurityContextUtils.tenantId(authentication), personId));
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPeoplePatch")
    @PatchMapping("/people/{personId}")
    @RequireCapability("HRM.EMPLOYEE.UPDATE")
    public PersonSummaryResponse patch(Authentication authentication, @PathVariable UUID personId,
                                       @Valid @RequestBody PatchPersonRequest request) {
        HrPerson updated = service.patchPersonNames(SecurityContextUtils.tenantId(authentication),
                personId, request.firstName(), request.middleName(), request.lastName(),
                request.expectedVersion());
        return PersonSummaryResponse.from(updated);
    }

    // ==================== PRIVATE PII (restricted, audited) ====================

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPeopleGetPrivate")
    @GetMapping("/people/{personId}/private")
    @RequireCapability("HRM.PII.VIEW")
    public PersonPrivateResponse getPrivate(Authentication authentication, @PathVariable UUID personId) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID actorUserId = SecurityContextUtils.userId(authentication);
        HrPersonPrivate profile = service.readPrivateWithAudit(tenantId, actorUserId,
                null, null, personId);
        return profile == null ? PersonPrivateResponse.empty(personId) : PersonPrivateResponse.from(profile);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPeoplePatchPrivate")
    @PatchMapping("/people/{personId}/private")
    @RequireCapability("HRM.PII.MANAGE")
    public PersonPrivateMutationResponse patchPrivate(Authentication authentication,
                                                      @PathVariable UUID personId,
                                                      @Valid @RequestBody PatchPersonPrivateRequest request) {
        HrPersonPrivate saved = service.patchPrivate(SecurityContextUtils.tenantId(authentication),
                personId, request.dateOfBirth(), request.nationalityCountryCode(),
                request.maritalStatus(), request.expectedVersion());
        // Metadata only — the audited private read is the single PII surface.
        return new PersonPrivateMutationResponse(saved.personId(), saved.version());
    }

    // ==================== IDENTITY DOCUMENTS (write-only values) ====================

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPeopleAddIdentifier")
    @PostMapping("/people/{personId}/identifiers")
    @RequireCapability("HRM.PII.MANAGE")
    public ResponseEntity<IdentifierMetadataResponse> addIdentifier(
            Authentication authentication, @PathVariable UUID personId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AddIdentifierRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".identifiers",
                personId + "|" + request);
        IdentifierMetadataResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".identifiers", idempotencyKey, fingerprint,
                IdentifierMetadataResponse.class,
                () -> IdentifierMetadataResponse.from(service.addIdentifier(tenantId, personId,
                        request.identifierType(), request.issuingCountryCode(), request.value())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ==================== USER LINK ====================

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPeopleLinkUser")
    @PostMapping("/people/{personId}/user-link")
    @RequireCapability("HRM.USER_LINK.MANAGE")
    public ResponseEntity<PersonLinkResponse> linkUser(
            Authentication authentication, @PathVariable UUID personId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LinkUserRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        UUID principalId = SecurityContextUtils.userId(authentication);
        String fingerprint = HrmIdempotentCommandExecutor.fingerprint(OPERATION_PREFIX + ".user-link",
                personId + "|" + request);
        PersonLinkResponse response = idempotentCommands.execute(
                tenantId, principalId, OPERATION_PREFIX + ".user-link", idempotencyKey, fingerprint,
                PersonLinkResponse.class,
                () -> {
                    service.linkUser(tenantId, personId, request.userId());
                    return new PersonLinkResponse(personId, request.userId(), true);
                });
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @io.swagger.v3.oas.annotations.Operation(operationId = "hrPeopleUnlinkUser")
    @DeleteMapping("/people/{personId}/user-link")
    @RequireCapability("HRM.USER_LINK.MANAGE")
    public ResponseEntity<PersonLinkResponse> unlinkUser(Authentication authentication,
                                                         @PathVariable UUID personId) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        service.unlinkUser(tenantId, personId);
        // DELETE is idempotent by nature: the resulting state has no link
        // whether one was removed or never existed.
        return ResponseEntity.ok(new PersonLinkResponse(personId, null, false));
    }

    private PersonSummaryResponse createSummary(UUID tenantId, CreatePersonRequest request) {
        HrPerson created = service.createPerson(tenantId, request.firstName(),
                request.middleName(), request.lastName());
        return PersonSummaryResponse.from(created);
    }
}
