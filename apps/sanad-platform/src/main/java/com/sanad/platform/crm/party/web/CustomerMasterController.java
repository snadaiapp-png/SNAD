package com.sanad.platform.crm.party.web;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.application.CustomerMasterUseCases;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.*;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final CustomerMasterUseCases useCases;

    public CustomerMasterController(CustomerMasterUseCases useCases) {
        this.useCases = useCases;
    }

    @RequireCapability("CRM.ACCOUNT.READ")
    @GetMapping("/{accountId}/master")
    public CustomerMasterProfile getMaster(Authentication authentication, @PathVariable UUID accountId) {
        return useCases.getProfile(tenantId(authentication), accountId);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @PatchMapping("/{accountId}/master")
    public CustomerMasterProfile updateMaster(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateMasterRequest request) {
        return useCases.updateProfile(tenantId(authentication), userId(authentication), accountId,
                new UpdateCustomerMasterCommand(request.legalName(), request.tradingName(),
                        request.registrationNumber(), request.taxNumber(), request.industryCode(),
                        request.customerSegment(), request.customerTier(), request.website(),
                        request.primaryEmail(), request.primaryPhone(), request.countryCode(),
                        request.riskRating(), request.creditLimit(), request.paymentTermsDays()),
                request.expectedVersion());
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
            @Valid @RequestBody CreateAddressRequest request) {
        AccountAddress created = useCases.addAddress(tenantId(authentication), userId(authentication), accountId,
                new CreateAddressCommand(request.addressType(), request.label(), request.line1(), request.line2(),
                        request.city(), request.stateRegion(), request.postalCode(), request.countryCode(),
                        Boolean.TRUE.equals(request.primaryAddress())));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @RequireCapability("CRM.ACCOUNT.WRITE")
    @DeleteMapping("/{accountId}/addresses/{addressId}")
    public ResponseEntity<Void> deactivateAddress(
            Authentication authentication,
            @PathVariable UUID accountId,
            @PathVariable UUID addressId) {
        useCases.deactivateAddress(tenantId(authentication), userId(authentication), accountId, addressId);
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
            @Valid @RequestBody CreateIdentifierRequest request) {
        AccountIdentifier created = useCases.addIdentifier(tenantId(authentication), userId(authentication), accountId,
                new CreateIdentifierCommand(request.identifierType(), request.identifierValue(),
                        request.issuerCountryCode(), Boolean.TRUE.equals(request.primaryIdentifier()),
                        Boolean.TRUE.equals(request.verified())));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
            @Valid @RequestBody CreateRelationshipRequest request) {
        AccountRelationship created = useCases.addRelationship(tenantId(authentication), userId(authentication), accountId,
                new CreateRelationshipCommand(request.targetAccountId(), request.relationshipType(),
                        request.effectiveFrom(), request.effectiveTo(), request.notes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
    public MergeResult merge(
            Authentication authentication,
            @PathVariable UUID sourceAccountId,
            @PathVariable UUID targetAccountId,
            @Valid @RequestBody MergeRequest request) {
        return useCases.merge(tenantId(authentication), userId(authentication), sourceAccountId, targetAccountId,
                request.expectedSourceVersion(), request.expectedTargetVersion(), request.reason());
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
            @Min(0) long expectedVersion,
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
            @DecimalMin("0.0") BigDecimal creditLimit,
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
            @Min(0) long expectedSourceVersion,
            @Min(0) long expectedTargetVersion,
            @Size(max = 500) String reason) {}
}
