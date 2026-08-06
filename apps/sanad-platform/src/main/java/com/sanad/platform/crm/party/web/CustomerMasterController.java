package com.sanad.platform.crm.party.web;

import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.application.CustomerMasterUseCases;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.*;
import com.sanad.platform.crm.web.CrmIdempotencyHttpSupport;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/accounts")
public class CustomerMasterController {
    private static final String CUSTOMER_MASTER_ETAG_TYPE = "customer-master";
    private static final String ADDRESS_ETAG_TYPE = "customer-master-address";

    private final CustomerMasterUseCases useCases;
    private final ETagService etags;
    private final CrmIdempotencyHttpSupport idempotency;

    public CustomerMasterController(
            CustomerMasterUseCases useCases,
            ETagService etags,
            CrmIdempotencyHttpSupport idempotency) {
        this.useCases = useCases;
        this.etags = etags;
        this.idempotency = idempotency;
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/{accountId}/master")
    public ResponseEntity<CustomerMasterProfile> getMaster(
            Authentication authentication,
            @PathVariable UUID accountId) {
        CustomerMasterProfile profile = useCases.getProfile(tenantId(authentication), accountId);
        return ResponseEntity.ok()
                .eTag(etags.etag(CUSTOMER_MASTER_ETAG_TYPE, accountId, profile.version()))
                .body(profile);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PatchMapping("/{accountId}/master")
    public ResponseEntity<CustomerMasterProfile> updateMaster(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateMasterRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        UUID tenantId = tenantId(authentication);
        CustomerMasterProfile current = useCases.getProfile(tenantId, accountId);
        etags.validateIfMatch(ifMatch, CUSTOMER_MASTER_ETAG_TYPE, accountId, current.version());
        CustomerMasterProfile updated = useCases.updateProfile(tenantId, userId(authentication), accountId,
                new UpdateCustomerMasterCommand(request.legalName(), request.tradingName(),
                        request.registrationNumber(), request.taxNumber(), request.industryCode(),
                        request.customerSegment(), request.customerTier(), request.website(),
                        request.primaryEmail(), request.primaryPhone(), request.countryCode(),
                        request.riskRating(), request.creditLimit(), request.paymentTermsDays()),
                current.version());
        return ResponseEntity.ok()
                .eTag(etags.etag(CUSTOMER_MASTER_ETAG_TYPE, accountId, updated.version()))
                .body(updated);
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/{accountId}/addresses")
    public List<AccountAddress> addresses(Authentication authentication, @PathVariable UUID accountId) {
        return useCases.listAddresses(tenantId(authentication), accountId);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PostMapping("/{accountId}/addresses")
    public ResponseEntity<AccountAddress> addAddress(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateAddressRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v1/crm/accounts/{accountId}/addresses", idempotencyKey, request, httpRequest);
        if (guard.isReplay()) return idempotency.replayRaw(guard, AccountAddress.class);
        try {
            AccountAddress created = useCases.addAddress(tenantId(authentication), userId(authentication), accountId,
                    new CreateAddressCommand(request.addressType(), request.label(), request.line1(), request.line2(),
                            request.city(), request.stateRegion(), request.postalCode(), request.countryCode(),
                            Boolean.TRUE.equals(request.primaryAddress())));
            return idempotency.completeRaw(guard, created, HttpStatus.CREATED,
                    etagHeaders(ADDRESS_ETAG_TYPE, created.id(), created.version()));
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @DeleteMapping("/{accountId}/addresses/{addressId}")
    public ResponseEntity<Void> deactivateAddress(
            Authentication authentication,
            @PathVariable UUID accountId,
            @PathVariable UUID addressId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        UUID tenantId = tenantId(authentication);
        AccountAddress current = useCases.getAddress(tenantId, accountId, addressId);
        etags.validateIfMatch(ifMatch, ADDRESS_ETAG_TYPE, addressId, current.version());
        useCases.deactivateAddress(
                tenantId, userId(authentication), accountId, addressId, current.version());
        return ResponseEntity.noContent().build();
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/{accountId}/identifiers")
    public List<AccountIdentifier> identifiers(Authentication authentication, @PathVariable UUID accountId) {
        return useCases.listIdentifiers(tenantId(authentication), accountId);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PostMapping("/{accountId}/identifiers")
    public ResponseEntity<AccountIdentifier> addIdentifier(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateIdentifierRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v1/crm/accounts/{accountId}/identifiers", idempotencyKey, request, httpRequest);
        if (guard.isReplay()) return idempotency.replayRaw(guard, AccountIdentifier.class);
        try {
            AccountIdentifier created = useCases.addIdentifier(
                    tenantId(authentication), userId(authentication), accountId,
                    new CreateIdentifierCommand(request.identifierType(), request.identifierValue(),
                            request.issuerCountryCode(), Boolean.TRUE.equals(request.primaryIdentifier()),
                            Boolean.TRUE.equals(request.verified())));
            return idempotency.completeRaw(guard, created, HttpStatus.CREATED, new HttpHeaders());
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/{accountId}/relationships")
    public List<AccountRelationship> relationships(Authentication authentication, @PathVariable UUID accountId) {
        return useCases.listRelationships(tenantId(authentication), accountId);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PostMapping("/{accountId}/relationships")
    public ResponseEntity<AccountRelationship> addRelationship(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateRelationshipRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v1/crm/accounts/{accountId}/relationships", idempotencyKey, request, httpRequest);
        if (guard.isReplay()) return idempotency.replayRaw(guard, AccountRelationship.class);
        try {
            AccountRelationship created = useCases.addRelationship(
                    tenantId(authentication), userId(authentication), accountId,
                    new CreateRelationshipCommand(request.targetAccountId(), request.relationshipType(),
                            request.effectiveFrom(), request.effectiveTo(), request.notes()));
            return idempotency.completeRaw(guard, created, HttpStatus.CREATED, new HttpHeaders());
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/{accountId}/duplicates")
    public List<DuplicateCandidate> duplicates(
            Authentication authentication,
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "20") int limit) {
        return useCases.duplicateCandidates(tenantId(authentication), accountId, limit);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PostMapping("/{sourceAccountId}/merge/{targetAccountId}")
    public ResponseEntity<MergeResult> merge(
            Authentication authentication,
            @PathVariable UUID sourceAccountId,
            @PathVariable UUID targetAccountId,
            @Valid @RequestBody MergeRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String sourceIfMatch,
            @RequestHeader(value = "X-Target-If-Match", required = false) String targetIfMatch,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        var guard = idempotency.begin(authentication,
                "POST:/api/v1/crm/accounts/{sourceAccountId}/merge/{targetAccountId}",
                idempotencyKey, request, httpRequest);
        if (guard.isReplay()) return idempotency.replayRaw(guard, MergeResult.class);
        try {
            UUID tenantId = tenantId(authentication);
            CustomerMasterProfile source = useCases.getProfile(tenantId, sourceAccountId);
            CustomerMasterProfile target = useCases.getProfile(tenantId, targetAccountId);
            etags.validateIfMatch(sourceIfMatch, CUSTOMER_MASTER_ETAG_TYPE, sourceAccountId, source.version());
            etags.validateIfMatch(targetIfMatch, CUSTOMER_MASTER_ETAG_TYPE, targetAccountId, target.version());
            MergeResult result = useCases.merge(
                    tenantId, userId(authentication), sourceAccountId, targetAccountId,
                    source.version(), target.version(), request.reason());
            return idempotency.completeRaw(guard, result, HttpStatus.OK,
                    etagHeaders(CUSTOMER_MASTER_ETAG_TYPE, targetAccountId, result.targetVersion()));
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    private HttpHeaders etagHeaders(String entityType, UUID id, long version) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(etags.etag(entityType, id, version));
        return headers;
    }

    private static UUID tenantId(Authentication authentication) { return context(authentication, "tenant_id"); }
    private static UUID userId(Authentication authentication) { return context(authentication, "user_id"); }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Authenticated CRM context is required.");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Invalid authenticated CRM context.");
        }
    }

    public record UpdateMasterRequest(
            Long expectedVersion,
            @Size(max = 240) String legalName,
            @Size(max = 240) String tradingName,
            @Size(max = 120) String registrationNumber,
            @Size(max = 120) String taxNumber,
            @Size(max = 80) String industryCode,
            @Size(max = 80) String customerSegment,
            @Pattern(regexp = "STANDARD|SILVER|GOLD|PLATINUM|STRATEGIC", flags = Pattern.Flag.CASE_INSENSITIVE)
            String customerTier,
            @Size(max = 500) String website,
            @Email @Size(max = 255) String primaryEmail,
            @Size(max = 64) String primaryPhone,
            @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @Pattern(regexp = "UNASSESSED|LOW|MEDIUM|HIGH|RESTRICTED", flags = Pattern.Flag.CASE_INSENSITIVE)
            String riskRating,
            @DecimalMin("0.0") @Digits(integer = 16, fraction = 2) BigDecimal creditLimit,
            @Min(0) @Max(365) Integer paymentTermsDays) {}

    public record CreateAddressRequest(
            @NotBlank @Pattern(regexp = "REGISTERED|BILLING|SHIPPING|OFFICE|OTHER", flags = Pattern.Flag.CASE_INSENSITIVE)
            String addressType,
            @Size(max = 120) String label,
            @NotBlank @Size(max = 240) String line1,
            @Size(max = 240) String line2,
            @NotBlank @Size(max = 120) String city,
            @Size(max = 120) String stateRegion,
            @Size(max = 32) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            Boolean primaryAddress) {}

    public record CreateIdentifierRequest(
            @NotBlank @Pattern(
                    regexp = "COMMERCIAL_REGISTRATION|TAX|VAT|NATIONAL_ID|DUNS|EXTERNAL|OTHER",
                    flags = Pattern.Flag.CASE_INSENSITIVE)
            String identifierType,
            @NotBlank @Size(max = 180) String identifierValue,
            @Pattern(regexp = "[A-Za-z]{2}") String issuerCountryCode,
            Boolean primaryIdentifier,
            Boolean verified) {}

    public record CreateRelationshipRequest(
            @NotNull UUID targetAccountId,
            @NotBlank @Pattern(
                    regexp = "PARENT|SUBSIDIARY|PARTNER|SUPPLIER|CUSTOMER|AFFILIATE|OTHER",
                    flags = Pattern.Flag.CASE_INSENSITIVE)
            String relationshipType,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 1000) String notes) {}

    public record MergeRequest(
            Long expectedSourceVersion,
            Long expectedTargetVersion,
            @NotBlank @Size(max = 500) String reason) {}
}
