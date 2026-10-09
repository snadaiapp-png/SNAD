package com.sanad.platform.organization.api;

import com.sanad.platform.organization.legalentity.LegalEntity;
import com.sanad.platform.organization.legalentity.LegalEntityService;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only employer-context resolution for an Organization.
 *
 * <p>The tenant is always derived from the authenticated security context.
 * Resolution succeeds only when exactly one ACTIVE Legal Entity is eligible
 * for the Organization on the requested effective date.</p>
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationLegalEntityController {

    private final LegalEntityService legalEntityService;

    public OrganizationLegalEntityController(LegalEntityService legalEntityService) {
        this.legalEntityService = legalEntityService;
    }

    @RequireCapability("ORGANIZATION.WRITE")
    @PostMapping("/{organizationId}/legal-entity/bootstrap")
    public ResponseEntity<?> bootstrapLegalEntity(
            Authentication authentication,
            @PathVariable UUID organizationId,
            @Valid @RequestBody BootstrapEmployerContextRequest request) {
        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        try {
            LegalEntity legalEntity = legalEntityService.bootstrapSingleActiveForOrganization(
                    tenantId,
                    organizationId,
                    request.effectiveDate(),
                    request.code(),
                    request.name(),
                    request.registeredCountryCode(),
                    request.statutoryCountryCode());
            return ResponseEntity.ok(Map.of(
                    "organizationId", organizationId,
                    "legalEntityId", legalEntity.id(),
                    "code", legalEntity.code(),
                    "name", legalEntity.name(),
                    "effectiveDate", request.effectiveDate(),
                    "status", "ACTIVE"));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "EMPLOYER_FOUNDATION_BOOTSTRAP_REJECTED",
                    "message", ex.getMessage()));
        }
    }

    public record BootstrapEmployerContextRequest(
            @NotBlank String code,
            @NotBlank String name,
            @Pattern(regexp = "^[A-Z]{2}$") String registeredCountryCode,
            @Pattern(regexp = "^[A-Z]{2}$") String statutoryCountryCode,
            @NotNull LocalDate effectiveDate) {
    }

    @RequireCapability("ORGANIZATION.READ")
    @GetMapping("/{organizationId}/legal-entity")
    public ResponseEntity<?> resolveLegalEntity(
            Authentication authentication,
            @PathVariable UUID organizationId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate effectiveDate) {

        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        try {
            LegalEntity legalEntity =
                    legalEntityService.resolveSingleActiveForOrganization(
                            tenantId, organizationId, effectiveDate);
            return ResponseEntity.ok(Map.of(
                    "organizationId", organizationId,
                    "legalEntityId", legalEntity.id(),
                    "code", legalEntity.code(),
                    "name", legalEntity.name(),
                    "effectiveDate", effectiveDate));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "EMPLOYER_CONTEXT_UNRESOLVED",
                    "message", ex.getMessage()));
        }
    }
}
