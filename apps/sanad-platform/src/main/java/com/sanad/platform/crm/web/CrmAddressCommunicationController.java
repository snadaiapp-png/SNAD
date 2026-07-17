package com.sanad.platform.crm.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.pagination.CursorCodec;
import com.sanad.platform.crm.party.application.AddressCommunicationUseCases;
import com.sanad.platform.crm.party.domain.AddressCommunicationRepository.*;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CRM-007 typed APIs for normalized addresses and communication methods. */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmAddressCommunicationController {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AddressCommunicationUseCases useCases;
    private final ETagService etags;
    private final CursorCodec cursors;

    public CrmAddressCommunicationController(
            AddressCommunicationUseCases useCases,
            ETagService etags,
            CursorCodec cursors) {
        this.useCases = useCases;
        this.etags = etags;
        this.cursors = cursors;
    }

    @RequireCapability("CRM.ADDRESS.READ")
    @GetMapping("/accounts/{accountId}/addresses")
    public ListResponse<AddressResponse> accountAddresses(
            Authentication authentication,
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        return addressPage(authentication, "ACCOUNT", accountId, includeArchived, limit, cursor, request);
    }

    @RequireCapability("CRM.ADDRESS.READ")
    @GetMapping("/contacts/{contactId}/addresses")
    public ListResponse<AddressResponse> contactAddresses(
            Authentication authentication,
            @PathVariable UUID contactId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        return addressPage(authentication, "PERSON", contactId, includeArchived, limit, cursor, request);
    }

    @RequireCapability("CRM.ADDRESS.READ")
    @GetMapping("/addresses/{addressId}")
    public ResponseEntity<SingleResponse<AddressResponse>> address(
            Authentication authentication,
            @PathVariable UUID addressId,
            HttpServletRequest request) {
        AddressResponse response = addressResponse(useCases.address(tenantId(authentication), addressId));
        return single(response, "address", addressId, response.version(), request);
    }

    @RequireCapability("CRM.ADDRESS.WRITE")
    @PostMapping("/accounts/{accountId}/addresses")
    public ResponseEntity<SingleResponse<AddressResponse>> createAccountAddress(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateAddressRequest body,
            HttpServletRequest request) {
        return createAddress(authentication, "ACCOUNT", accountId, body, request);
    }

    @RequireCapability("CRM.ADDRESS.WRITE")
    @PostMapping("/contacts/{contactId}/addresses")
    public ResponseEntity<SingleResponse<AddressResponse>> createContactAddress(
            Authentication authentication,
            @PathVariable UUID contactId,
            @Valid @RequestBody CreateAddressRequest body,
            HttpServletRequest request) {
        return createAddress(authentication, "PERSON", contactId, body, request);
    }

    @RequireCapability("CRM.ADDRESS.WRITE")
    @PatchMapping("/addresses/{addressId}")
    public ResponseEntity<SingleResponse<AddressResponse>> updateAddress(
            Authentication authentication,
            @PathVariable UUID addressId,
            @Valid @RequestBody UpdateAddressRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        AddressRecord current = useCases.address(tenantId, addressId);
        etags.validateIfMatch(ifMatch, "address", addressId, current.version());
        AddressRecord updated = useCases.updateAddress(tenantId, userId(authentication), addressId,
                updateAddressCommand(body), current.version());
        AddressResponse response = addressResponse(updated);
        return single(response, "address", addressId, response.version(), request);
    }

    @RequireCapability("CRM.ADDRESS.ADMIN")
    @PatchMapping("/addresses/{addressId}/primary")
    public ResponseEntity<SingleResponse<AddressResponse>> setPrimaryAddress(
            Authentication authentication,
            @PathVariable UUID addressId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        AddressRecord current = useCases.address(tenantId, addressId);
        etags.validateIfMatch(ifMatch, "address", addressId, current.version());
        AddressResponse response = addressResponse(useCases.setPrimaryAddress(
                tenantId, userId(authentication), addressId, current.version()));
        return single(response, "address", addressId, response.version(), request);
    }

    @RequireCapability("CRM.ADDRESS.ADMIN")
    @PatchMapping("/addresses/{addressId}/archive")
    public ResponseEntity<SingleResponse<AddressResponse>> archiveAddress(
            Authentication authentication,
            @PathVariable UUID addressId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        return addressStatus(authentication, addressId, "ARCHIVED", ifMatch, request);
    }

    @RequireCapability("CRM.ADDRESS.ADMIN")
    @PatchMapping("/addresses/{addressId}/reactivate")
    public ResponseEntity<SingleResponse<AddressResponse>> reactivateAddress(
            Authentication authentication,
            @PathVariable UUID addressId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        return addressStatus(authentication, addressId, "ACTIVE", ifMatch, request);
    }

    @RequireCapability("CRM.ADDRESS.READ")
    @GetMapping("/addresses/{addressId}/history")
    public ListResponse<AddressHistoryResponse> addressHistory(
            Authentication authentication,
            @PathVariable UUID addressId,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        List<AddressHistoryResponse> data = useCases.addressHistory(tenantId(authentication), addressId, pageLimit(limit))
                .stream().map(CrmAddressCommunicationController::addressHistoryResponse).toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(pageLimit(limit)), requestId(request));
    }

    @RequireCapability("CRM.COMMUNICATION.READ")
    @GetMapping("/accounts/{accountId}/communication-methods")
    public ListResponse<CommunicationMethodResponse> accountCommunicationMethods(
            Authentication authentication,
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String methodType,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        return communicationPage(authentication, "ACCOUNT", accountId, includeArchived,
                methodType, verificationStatus, limit, cursor, request);
    }

    @RequireCapability("CRM.COMMUNICATION.READ")
    @GetMapping("/contacts/{contactId}/communication-methods")
    public ListResponse<CommunicationMethodResponse> contactCommunicationMethods(
            Authentication authentication,
            @PathVariable UUID contactId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) String methodType,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        return communicationPage(authentication, "PERSON", contactId, includeArchived,
                methodType, verificationStatus, limit, cursor, request);
    }

    @RequireCapability("CRM.COMMUNICATION.READ")
    @GetMapping("/communication-methods/{communicationMethodId}")
    public ResponseEntity<SingleResponse<CommunicationMethodResponse>> communicationMethod(
            Authentication authentication,
            @PathVariable UUID communicationMethodId,
            HttpServletRequest request) {
        CommunicationMethodRecord record = useCases.communicationMethod(tenantId(authentication), communicationMethodId);
        CommunicationMethodResponse response = communicationResponse(
                useCases.masked(record, hasCapability(authentication, "CRM.COMMUNICATION.SENSITIVE.READ")));
        return single(response, "communication-method", communicationMethodId, response.version(), request);
    }

    @RequireCapability("CRM.COMMUNICATION.WRITE")
    @PostMapping("/accounts/{accountId}/communication-methods")
    public ResponseEntity<SingleResponse<CommunicationMethodResponse>> createAccountCommunicationMethod(
            Authentication authentication,
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateCommunicationMethodRequest body,
            HttpServletRequest request) {
        return createCommunicationMethod(authentication, "ACCOUNT", accountId, body, request);
    }

    @RequireCapability("CRM.COMMUNICATION.WRITE")
    @PostMapping("/contacts/{contactId}/communication-methods")
    public ResponseEntity<SingleResponse<CommunicationMethodResponse>> createContactCommunicationMethod(
            Authentication authentication,
            @PathVariable UUID contactId,
            @Valid @RequestBody CreateCommunicationMethodRequest body,
            HttpServletRequest request) {
        return createCommunicationMethod(authentication, "PERSON", contactId, body, request);
    }

    @RequireCapability("CRM.COMMUNICATION.WRITE")
    @PatchMapping("/communication-methods/{communicationMethodId}")
    public ResponseEntity<SingleResponse<CommunicationMethodResponse>> updateCommunicationMethod(
            Authentication authentication,
            @PathVariable UUID communicationMethodId,
            @Valid @RequestBody UpdateCommunicationMethodRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        CommunicationMethodRecord current = useCases.communicationMethod(tenantId, communicationMethodId);
        etags.validateIfMatch(ifMatch, "communication-method", communicationMethodId, current.version());
        CommunicationMethodRecord updated = useCases.updateCommunicationMethod(
                tenantId, userId(authentication), communicationMethodId,
                new UpdateCommunicationMethodCommand(body.rawValue(), null, body.displayValue(), body.label(),
                        body.privacyClassification(), body.consentStateReference(), body.usagePurpose(),
                        body.validFrom(), body.validTo()), body.countryHint(), current.version());
        CommunicationMethodResponse response = communicationResponse(useCases.masked(updated, true));
        return single(response, "communication-method", communicationMethodId, response.version(), request);
    }

    @RequireCapability("CRM.COMMUNICATION.ADMIN")
    @PatchMapping("/communication-methods/{communicationMethodId}/preferred")
    public ResponseEntity<SingleResponse<CommunicationMethodResponse>> setPreferredCommunicationMethod(
            Authentication authentication,
            @PathVariable UUID communicationMethodId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        CommunicationMethodRecord current = useCases.communicationMethod(tenantId, communicationMethodId);
        etags.validateIfMatch(ifMatch, "communication-method", communicationMethodId, current.version());
        CommunicationMethodResponse response = communicationResponse(useCases.setPreferred(
                tenantId, userId(authentication), communicationMethodId, current.version()));
        return single(response, "communication-method", communicationMethodId, response.version(), request);
    }

    @RequireCapability("CRM.COMMUNICATION.ADMIN")
    @PatchMapping("/communication-methods/{communicationMethodId}/verification")
    public ResponseEntity<SingleResponse<CommunicationMethodResponse>> changeVerification(
            Authentication authentication,
            @PathVariable UUID communicationMethodId,
            @Valid @RequestBody VerificationRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        CommunicationMethodRecord current = useCases.communicationMethod(tenantId, communicationMethodId);
        etags.validateIfMatch(ifMatch, "communication-method", communicationMethodId, current.version());
        CommunicationMethodResponse response = communicationResponse(useCases.changeVerification(
                tenantId, userId(authentication), communicationMethodId, body.verificationStatus(), current.version()));
        return single(response, "communication-method", communicationMethodId, response.version(), request);
    }

    @RequireCapability("CRM.COMMUNICATION.ADMIN")
    @PatchMapping("/communication-methods/{communicationMethodId}/archive")
    public ResponseEntity<SingleResponse<CommunicationMethodResponse>> archiveCommunicationMethod(
            Authentication authentication,
            @PathVariable UUID communicationMethodId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        return communicationStatus(authentication, communicationMethodId, "ARCHIVED", ifMatch, request);
    }

    @RequireCapability("CRM.COMMUNICATION.ADMIN")
    @PatchMapping("/communication-methods/{communicationMethodId}/reactivate")
    public ResponseEntity<SingleResponse<CommunicationMethodResponse>> reactivateCommunicationMethod(
            Authentication authentication,
            @PathVariable UUID communicationMethodId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        return communicationStatus(authentication, communicationMethodId, "ACTIVE", ifMatch, request);
    }

    @RequireCapability("CRM.COMMUNICATION.READ")
    @GetMapping("/communication-methods/{communicationMethodId}/history")
    public ListResponse<CommunicationHistoryResponse> communicationHistory(
            Authentication authentication,
            @PathVariable UUID communicationMethodId,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        List<CommunicationHistoryResponse> data = useCases.communicationHistory(
                        tenantId(authentication), communicationMethodId, pageLimit(limit))
                .stream().map(CrmAddressCommunicationController::communicationHistoryResponse).toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(pageLimit(limit)), requestId(request));
    }

    private ResponseEntity<SingleResponse<AddressResponse>> createAddress(
            Authentication authentication, String ownerType, UUID ownerId,
            CreateAddressRequest body, HttpServletRequest request) {
        AddressRecord created = useCases.createAddress(tenantId(authentication), userId(authentication), ownerType, ownerId,
                createAddressCommand(body));
        AddressResponse response = addressResponse(created);
        return ResponseEntity.status(201).eTag(etags.etag("address", created.id(), created.version()))
                .body(SingleResponse.of(response, requestId(request)));
    }

    private ResponseEntity<SingleResponse<CommunicationMethodResponse>> createCommunicationMethod(
            Authentication authentication, String ownerType, UUID ownerId,
            CreateCommunicationMethodRequest body, HttpServletRequest request) {
        CommunicationMethodRecord created = useCases.createCommunicationMethod(
                tenantId(authentication), userId(authentication), ownerType, ownerId,
                new CreateCommunicationMethodCommand(body.methodType(), body.rawValue(), null, body.displayValue(),
                        body.label(), body.preferred(), body.privacyClassification(),
                        body.consentStateReference(), body.usagePurpose(), body.validFrom(), body.validTo()),
                body.countryHint());
        CommunicationMethodResponse response = communicationResponse(created);
        return ResponseEntity.status(201).eTag(etags.etag("communication-method", created.id(), created.version()))
                .body(SingleResponse.of(response, requestId(request)));
    }

    private ResponseEntity<SingleResponse<AddressResponse>> addressStatus(
            Authentication authentication, UUID addressId, String status,
            String ifMatch, HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        AddressRecord current = useCases.address(tenantId, addressId);
        etags.validateIfMatch(ifMatch, "address", addressId, current.version());
        AddressResponse response = addressResponse(useCases.changeAddressStatus(
                tenantId, userId(authentication), addressId, status, current.version()));
        return single(response, "address", addressId, response.version(), request);
    }

    private ResponseEntity<SingleResponse<CommunicationMethodResponse>> communicationStatus(
            Authentication authentication, UUID methodId, String status,
            String ifMatch, HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        CommunicationMethodRecord current = useCases.communicationMethod(tenantId, methodId);
        etags.validateIfMatch(ifMatch, "communication-method", methodId, current.version());
        CommunicationMethodResponse response = communicationResponse(useCases.changeCommunicationStatus(
                tenantId, userId(authentication), methodId, status, current.version()));
        return single(response, "communication-method", methodId, response.version(), request);
    }

    private ListResponse<AddressResponse> addressPage(
            Authentication authentication, String ownerType, UUID ownerId, boolean includeArchived,
            Integer requestedLimit, String cursor, HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        int limit = pageLimit(requestedLimit);
        CursorValues values = cursor(cursor, tenantId);
        List<AddressRecord> rows = useCases.addresses(tenantId, ownerType, ownerId, includeArchived,
                limit + 1, values.beforeTime(), values.beforeId());
        boolean hasMore = rows.size() > limit;
        List<AddressRecord> page = hasMore ? rows.subList(0, limit) : rows;
        String next = pageCursor(tenantId, page.isEmpty() ? null : page.get(page.size() - 1).updatedAt(),
                page.isEmpty() ? null : page.get(page.size() - 1).id(), hasMore);
        return ListResponse.of(page.stream().map(CrmAddressCommunicationController::addressResponse).toList(),
                CrmEnvelopes.Page.of(next, hasMore, limit), requestId(request));
    }

    private ListResponse<CommunicationMethodResponse> communicationPage(
            Authentication authentication, String ownerType, UUID ownerId, boolean includeArchived,
            String methodType, String verificationStatus, Integer requestedLimit, String cursor,
            HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        int limit = pageLimit(requestedLimit);
        CursorValues values = cursor(cursor, tenantId);
        List<CommunicationMethodRecord> rows = useCases.communicationMethods(tenantId, ownerType, ownerId,
                includeArchived, methodType, verificationStatus, limit + 1, values.beforeTime(), values.beforeId());
        boolean hasMore = rows.size() > limit;
        List<CommunicationMethodRecord> page = hasMore ? rows.subList(0, limit) : rows;
        boolean expose = hasCapability(authentication, "CRM.COMMUNICATION.SENSITIVE.READ");
        String next = pageCursor(tenantId, page.isEmpty() ? null : page.get(page.size() - 1).updatedAt(),
                page.isEmpty() ? null : page.get(page.size() - 1).id(), hasMore);
        return ListResponse.of(page.stream().map(value -> useCases.masked(value, expose))
                        .map(CrmAddressCommunicationController::communicationResponse).toList(),
                CrmEnvelopes.Page.of(next, hasMore, limit), requestId(request));
    }

    private CursorValues cursor(String cursor, UUID tenantId) {
        if (cursor == null || cursor.isBlank()) return new CursorValues(null, null);
        CursorCodec.DecodedCursor decoded = cursors.decode(cursor, tenantId, "updatedAt", "desc");
        return new CursorValues(decoded.sortValue() == null ? null : Instant.parse(decoded.sortValue()),
                decoded.tieBreakerId());
    }

    private String pageCursor(UUID tenantId, Instant updatedAt, UUID id, boolean hasMore) {
        return hasMore && updatedAt != null && id != null
                ? cursors.encode(tenantId, "updatedAt", "desc", updatedAt.toString(), id) : null;
    }

    private <T> ResponseEntity<SingleResponse<T>> single(
            T body, String entityType, UUID id, long version, HttpServletRequest request) {
        return ResponseEntity.ok().eTag(etags.etag(entityType, id, version))
                .body(SingleResponse.of(body, requestId(request)));
    }

    private static CreateAddressCommand createAddressCommand(CreateAddressRequest body) {
        return new CreateAddressCommand(body.addressType(), body.label(), body.rawFormattedAddress(),
                body.line1(), body.line2(), body.line3(), body.district(), body.city(), body.stateRegion(),
                body.postalCode(), body.countryCode(), json(body.countryExtension()), body.latitude(), body.longitude(),
                body.primaryAddress(), body.verified(), body.verificationSource(), body.validFrom(), body.validTo());
    }

    private static UpdateAddressCommand updateAddressCommand(UpdateAddressRequest body) {
        return new UpdateAddressCommand(body.addressType(), body.label(), body.rawFormattedAddress(),
                body.line1(), body.line2(), body.line3(), body.district(), body.city(), body.stateRegion(),
                body.postalCode(), body.countryCode(), json(body.countryExtension()), body.latitude(), body.longitude(),
                body.verified(), body.verificationSource(), body.validFrom(), body.validTo());
    }

    private static String json(JsonNode node) { return node == null || node.isNull() ? null : node.toString(); }

    private static AddressResponse addressResponse(AddressRecord record) {
        return new AddressResponse(record.id(), record.version(), record.ownerType(), record.ownerId(),
                record.addressType(), record.label(), record.rawFormattedAddress(), record.line1(), record.line2(),
                record.line3(), record.district(), record.city(), record.stateRegion(), record.postalCode(),
                record.countryCode(), record.countryExtensionJson(), record.latitude(), record.longitude(),
                record.primaryAddress(), record.verified(), record.verificationSource(), record.status(),
                record.validFrom(), record.validTo(), record.createdAt(), record.updatedAt(), record.archivedAt());
    }

    private static CommunicationMethodResponse communicationResponse(CommunicationMethodRecord record) {
        return new CommunicationMethodResponse(record.id(), record.version(), record.ownerType(), record.ownerId(),
                record.methodType(), record.rawValue(), record.normalizedValue(), record.displayValue(), record.label(),
                record.preferred(), record.verified(), record.verificationStatus(), record.verifiedAt(),
                record.privacyClassification(), record.consentStateReference(), record.usagePurpose(), record.status(),
                record.validFrom(), record.validTo(), record.createdAt(), record.updatedAt(), record.archivedAt());
    }

    private static AddressHistoryResponse addressHistoryResponse(AddressHistoryRecord record) {
        return new AddressHistoryResponse(record.id(), record.addressId(), record.ownerType(), record.ownerId(),
                record.eventType(), record.previousVersion(), record.newVersion(), record.snapshot(),
                record.changedBy(), record.changedAt());
    }

    private static CommunicationHistoryResponse communicationHistoryResponse(CommunicationHistoryRecord record) {
        return new CommunicationHistoryResponse(record.id(), record.communicationMethodId(), record.ownerType(),
                record.ownerId(), record.eventType(), record.previousVersion(), record.newVersion(),
                record.snapshot(), record.changedBy(), record.changedAt());
    }

    private static int pageLimit(Integer value) {
        return value == null || value <= 0 ? DEFAULT_LIMIT : Math.min(value, MAX_LIMIT);
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

    private record CursorValues(Instant beforeTime, UUID beforeId) {}

    public record AddressResponse(
            UUID id, long version, String ownerType, UUID ownerId, String addressType, String label,
            String rawFormattedAddress, String line1, String line2, String line3, String district, String city,
            String stateRegion, String postalCode, String countryCode, String countryExtensionJson,
            BigDecimal latitude, BigDecimal longitude, boolean primaryAddress, boolean verified,
            String verificationSource, String status, LocalDate validFrom, LocalDate validTo,
            Instant createdAt, Instant updatedAt, Instant archivedAt) {}

    public record CreateAddressRequest(
            @NotBlank @Pattern(regexp = "REGISTERED|BILLING|SHIPPING|OFFICE|HOME|OTHER", flags = Pattern.Flag.CASE_INSENSITIVE)
            String addressType,
            @Size(max = 120) String label,
            @Size(max = 1200) String rawFormattedAddress,
            @NotBlank @Size(max = 240) String line1,
            @Size(max = 240) String line2,
            @Size(max = 240) String line3,
            @Size(max = 160) String district,
            @NotBlank @Size(max = 160) String city,
            @Size(max = 160) String stateRegion,
            @Size(max = 40) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            JsonNode countryExtension,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            boolean primaryAddress,
            boolean verified,
            @Size(max = 120) String verificationSource,
            LocalDate validFrom,
            LocalDate validTo) {}

    public record UpdateAddressRequest(
            @Pattern(regexp = "REGISTERED|BILLING|SHIPPING|OFFICE|HOME|OTHER", flags = Pattern.Flag.CASE_INSENSITIVE)
            String addressType,
            @Size(max = 120) String label,
            @Size(max = 1200) String rawFormattedAddress,
            @Size(max = 240) String line1,
            @Size(max = 240) String line2,
            @Size(max = 240) String line3,
            @Size(max = 160) String district,
            @Size(max = 160) String city,
            @Size(max = 160) String stateRegion,
            @Size(max = 40) String postalCode,
            @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            JsonNode countryExtension,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            Boolean verified,
            @Size(max = 120) String verificationSource,
            LocalDate validFrom,
            LocalDate validTo) {}

    public record AddressHistoryResponse(
            UUID id, UUID addressId, String ownerType, UUID ownerId, String eventType,
            Long previousVersion, long newVersion, String snapshot, UUID changedBy, Instant changedAt) {}

    public record CommunicationMethodResponse(
            UUID id, long version, String ownerType, UUID ownerId, String methodType,
            String rawValue, String normalizedValue, String displayValue, String label, boolean preferred,
            boolean verified, String verificationStatus, Instant verifiedAt, String privacyClassification,
            String consentStateReference, String usagePurpose, String status, LocalDate validFrom,
            LocalDate validTo, Instant createdAt, Instant updatedAt, Instant archivedAt) {}

    public record CreateCommunicationMethodRequest(
            @NotBlank @Pattern(
                    regexp = "EMAIL|PHONE|MOBILE|FAX|WHATSAPP|SMS|MESSAGING_HANDLE|WEBSITE|OTHER",
                    flags = Pattern.Flag.CASE_INSENSITIVE)
            String methodType,
            @NotBlank @Size(max = 1000) String rawValue,
            @Size(max = 1000) String displayValue,
            @Size(max = 120) String label,
            boolean preferred,
            @Pattern(regexp = "PUBLIC|INTERNAL|CONFIDENTIAL|RESTRICTED", flags = Pattern.Flag.CASE_INSENSITIVE)
            String privacyClassification,
            @Size(max = 120) String consentStateReference,
            @Size(max = 120) String usagePurpose,
            @Pattern(regexp = "[A-Za-z]{2}") String countryHint,
            LocalDate validFrom,
            LocalDate validTo) {}

    public record UpdateCommunicationMethodRequest(
            @Size(max = 1000) String rawValue,
            @Size(max = 1000) String displayValue,
            @Size(max = 120) String label,
            @Pattern(regexp = "PUBLIC|INTERNAL|CONFIDENTIAL|RESTRICTED", flags = Pattern.Flag.CASE_INSENSITIVE)
            String privacyClassification,
            @Size(max = 120) String consentStateReference,
            @Size(max = 120) String usagePurpose,
            @Pattern(regexp = "[A-Za-z]{2}") String countryHint,
            LocalDate validFrom,
            LocalDate validTo) {}

    public record VerificationRequest(
            @NotBlank @Pattern(regexp = "UNVERIFIED|PENDING|VERIFIED|FAILED|REVOKED", flags = Pattern.Flag.CASE_INSENSITIVE)
            String verificationStatus) {}

    public record CommunicationHistoryResponse(
            UUID id, UUID communicationMethodId, String ownerType, UUID ownerId, String eventType,
            Long previousVersion, long newVersion, String snapshot, UUID changedBy, Instant changedAt) {}
}
