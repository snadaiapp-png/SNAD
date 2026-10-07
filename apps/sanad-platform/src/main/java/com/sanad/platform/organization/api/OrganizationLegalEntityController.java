package com.sanad.platform.organization.api;

import com.sanad.platform.organization.legalentity.EmployerContextBootstrapService;
import com.sanad.platform.organization.legalentity.LegalEntity;
import com.sanad.platform.organization.legalentity.LegalEntityService;
import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final EmployerContextBootstrapService bootstrapService;

    public OrganizationLegalEntityController(
            LegalEntityService legalEntityService,
            EmployerContextBootstrapService bootstrapService) {
        this.legalEntityService = legalEntityService;
        this.bootstrapService = bootstrapService;
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
    @RequireCapability("ORGANIZATION.WRITE")
    @PostMapping("/{organizationId}/legal-entity/bootstrap")
    public ResponseEntity<?> bootstrapLegalEntity(
            Authentication authentication,
            @PathVariable UUID organizationId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate effectiveDate) {

        UUID tenantId = SecurityContextUtils.tenantId(authentication);
        try {
            EmployerContextBootstrapService.BootstrapResult result =
                    bootstrapService.ensureSingleActiveEmployerContext(
                            tenantId, organizationId, effectiveDate);
            LegalEntity legalEntity = result.legalEntity();
            return ResponseEntity.ok(Map.of(
                    "organizationId", organizationId,
                    "legalEntityId", legalEntity.id(),
                    "code", legalEntity.code(),
                    "name", legalEntity.name(),
                    "effectiveDate", effectiveDate,
                    "bootstrapped", result.bootstrapped()));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "EMPLOYER_CONTEXT_BOOTSTRAP_BLOCKED",
                    "message", ex.getMessage()));
        }
    }
}
