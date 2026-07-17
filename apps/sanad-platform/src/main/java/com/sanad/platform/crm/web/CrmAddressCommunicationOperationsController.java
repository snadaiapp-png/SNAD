package com.sanad.platform.crm.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.party.application.AddressCommunicationOperationsService;
import com.sanad.platform.crm.party.application.AddressCommunicationOperationsService.AddressImportRow;
import com.sanad.platform.crm.party.application.AddressCommunicationOperationsService.CommunicationImportRow;
import com.sanad.platform.crm.party.application.AddressCommunicationOperationsService.ImportResult;
import com.sanad.platform.crm.party.application.AddressCommunicationUseCases;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.AddressRecord;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.CommunicationMethodRecord;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.CreateAddressCommand;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.CreateCommunicationMethodCommand;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Operational search, import and export endpoints required by CRM-007. */
@RestController
@Validated
@RequestMapping("/api/v2/crm")
public class CrmAddressCommunicationOperationsController {
    private final AddressCommunicationOperationsService operations;
    private final AddressCommunicationUseCases useCases;
    private final CrmIdempotencyHttpSupport idempotency;

    public CrmAddressCommunicationOperationsController(
            AddressCommunicationOperationsService operations,
            AddressCommunicationUseCases useCases,
            CrmIdempotencyHttpSupport idempotency) {
        this.operations = operations;
        this.useCases = useCases;
        this.idempotency = idempotency;
    }

    @RequireCapability("CRM.ADDRESS.READ")
    @GetMapping("/addresses/search")
    public ListResponse<AddressSearchResponse> searchAddresses(
            Authentication authentication,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String addressType,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            HttpServletRequest request) {
        List<AddressSearchResponse> data = operations.searchAddresses(
                        tenantId(authentication), q, ownerType, addressType, countryCode, status, limit)
                .stream().map(CrmAddressCommunicationOperationsController::address).toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(limit), requestId(request));
    }

    @RequireCapability("CRM.COMMUNICATION.READ")
    @GetMapping("/communication-methods/search")
    public ListResponse<CommunicationSearchResponse> searchCommunicationMethods(
            Authentication authentication,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String methodType,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            HttpServletRequest request) {
        boolean expose = hasCapability(authentication, "CRM.COMMUNICATION.SENSITIVE.READ");
        List<CommunicationSearchResponse> data = operations.searchCommunicationMethods(
                        tenantId(authentication), q, ownerType, methodType, verificationStatus, status, limit)
                .stream().map(value -> useCases.masked(value, expose))
                .map(CrmAddressCommunicationOperationsController::communication).toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(limit), requestId(request));
    }

    @RequireCapability("CRM.ADDRESS.EXPORT")
    @GetMapping(value = "/addresses/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportAddresses(
            Authentication authentication,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String addressType,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String status) {
        byte[] csv = operations.exportAddresses(
                tenantId(authentication), q, ownerType, addressType, countryCode, status);
        return csv(csv, "crm-addresses.csv");
    }

    @RequireCapability("CRM.COMMUNICATION.EXPORT")
    @GetMapping(value = "/communication-methods/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportCommunicationMethods(
            Authentication authentication,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String methodType,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) String status) {
        byte[] csv = operations.exportCommunicationMethods(
                tenantId(authentication), q, ownerType, methodType, verificationStatus, status,
                hasCapability(authentication, "CRM.COMMUNICATION.SENSITIVE.READ"));
        return csv(csv, "crm-communication-methods.csv");
    }

    @RequireCapability("CRM.IMPORT.WRITE")
    @PostMapping("/addresses/import")
    public ResponseEntity<SingleResponse<ImportResult>> importAddresses(
            Authentication authentication,
            @Valid @RequestBody AddressImportRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        CrmIdempotencyHttpSupport.Guard guard = idempotency.begin(
                authentication, "CRM007_IMPORT_ADDRESSES", idempotencyKey, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, ImportResult.class);
        try {
            List<AddressImportRow> rows = body.rows().stream().map(row -> new AddressImportRow(
                    row.ownerType(), row.ownerId(), command(row.address()))).toList();
            ImportResult result = operations.importAddresses(tenantId(authentication), userId(authentication), rows);
            return idempotency.complete(guard, result, "import-job", 0, HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    @RequireCapability("CRM.IMPORT.WRITE")
    @PostMapping("/communication-methods/import")
    public ResponseEntity<SingleResponse<ImportResult>> importCommunicationMethods(
            Authentication authentication,
            @Valid @RequestBody CommunicationImportRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        CrmIdempotencyHttpSupport.Guard guard = idempotency.begin(
                authentication, "CRM007_IMPORT_COMMUNICATION_METHODS", idempotencyKey, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, ImportResult.class);
        try {
            List<CommunicationImportRow> rows = body.rows().stream().map(row -> new CommunicationImportRow(
                    row.ownerType(), row.ownerId(), row.communication().countryHint(),
                    command(row.communication()))).toList();
            ImportResult result = operations.importCommunicationMethods(
                    tenantId(authentication), userId(authentication), rows);
            return idempotency.complete(guard, result, "import-job", 0, HttpStatus.CREATED);
        } catch (RuntimeException exception) {
            idempotency.fail(guard);
            throw exception;
        }
    }

    private static CreateAddressCommand command(
            CrmAddressCommunicationController.CreateAddressRequest value) {
        return new CreateAddressCommand(value.addressType(), value.label(), value.rawFormattedAddress(),
                value.line1(), value.line2(), value.line3(), value.district(), value.city(), value.stateRegion(),
                value.postalCode(), value.countryCode(), json(value.countryExtension()), value.latitude(), value.longitude(),
                value.primaryAddress(), value.verified(), value.verificationSource(), value.validFrom(), value.validTo());
    }

    private static CreateCommunicationMethodCommand command(
            CrmAddressCommunicationController.CreateCommunicationMethodRequest value) {
        return new CreateCommunicationMethodCommand(value.methodType(), value.rawValue(), null, value.displayValue(),
                value.label(), value.preferred(), value.privacyClassification(), value.consentStateReference(),
                value.usagePurpose(), value.validFrom(), value.validTo());
    }

    private static AddressSearchResponse address(AddressRecord value) {
        return new AddressSearchResponse(value.id(), value.version(), value.ownerType(), value.ownerId(),
                value.addressType(), value.label(), value.rawFormattedAddress(), value.line1(), value.line2(),
                value.district(), value.city(), value.stateRegion(), value.postalCode(), value.countryCode(),
                value.primaryAddress(), value.verified(), value.status(), value.updatedAt());
    }

    private static CommunicationSearchResponse communication(CommunicationMethodRecord value) {
        return new CommunicationSearchResponse(value.id(), value.version(), value.ownerType(), value.ownerId(),
                value.methodType(), value.displayValue(), value.label(), value.preferred(), value.verified(),
                value.verificationStatus(), value.privacyClassification(), value.consentStateReference(),
                value.usagePurpose(), value.status(), value.updatedAt());
    }

    private static ResponseEntity<byte[]> csv(byte[] body, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private static String json(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }

    private static UUID requestId(HttpServletRequest request) {
        if (request != null) {
            String value = request.getHeader("X-Request-ID");
            if (value != null && !value.isBlank()) {
                try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { }
            }
        }
        return UUID.randomUUID();
    }

    private static UUID tenantId(Authentication authentication) { return contextId(authentication, "tenant_id"); }
    private static UUID userId(Authentication authentication) { return contextId(authentication, "user_id"); }

    private static UUID contextId(Authentication authentication, String key) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details) || details.get(key) == null) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Authenticated CRM context is required.");
        }
        try { return UUID.fromString(details.get(key).toString()); }
        catch (IllegalArgumentException exception) {
            throw new CrmContractException(CrmErrorCode.UNAUTHORIZED, "Invalid authenticated CRM context.");
        }
    }

    private static boolean hasCapability(Authentication authentication, String capability) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> capability.equalsIgnoreCase(authority.getAuthority())
                        || ("CAPABILITY_" + capability).equalsIgnoreCase(authority.getAuthority()));
    }

    public record AddressSearchResponse(
            UUID id,
            long version,
            String ownerType,
            UUID ownerId,
            String addressType,
            String label,
            String rawFormattedAddress,
            String line1,
            String line2,
            String district,
            String city,
            String stateRegion,
            String postalCode,
            String countryCode,
            boolean primaryAddress,
            boolean verified,
            String status,
            Instant updatedAt) {}

    public record CommunicationSearchResponse(
            UUID id,
            long version,
            String ownerType,
            UUID ownerId,
            String methodType,
            String displayValue,
            String label,
            boolean preferred,
            boolean verified,
            String verificationStatus,
            String privacyClassification,
            String consentStateReference,
            String usagePurpose,
            String status,
            Instant updatedAt) {}

    public record AddressImportItem(
            String ownerType,
            UUID ownerId,
            @Valid CrmAddressCommunicationController.CreateAddressRequest address) {}

    public record AddressImportRequest(
            @NotEmpty @Size(max = 500) List<@Valid AddressImportItem> rows) {}

    public record CommunicationImportItem(
            String ownerType,
            UUID ownerId,
            @Valid CrmAddressCommunicationController.CreateCommunicationMethodRequest communication) {}

    public record CommunicationImportRequest(
            @NotEmpty @Size(max = 500) List<@Valid CommunicationImportItem> rows) {}
}
