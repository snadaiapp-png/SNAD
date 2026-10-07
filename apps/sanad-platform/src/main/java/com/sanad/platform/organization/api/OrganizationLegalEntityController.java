package com.sanad.platform.organization.api;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only organization employer-context resolution.
 *
 * <p>Tenant identity is derived from the authenticated security context. The
 * resolver fails closed unless exactly one ACTIVE Legal Entity is eligible for
 * the Organization on the requested effective date.</p>
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationLegalEntityController {

    private final LegalEntityService legalEntityService;

    public OrganizationLegalEntityController(LegalEntityService legalEntityService) {
        this.legalEntityService = legalEntityService;
    }

    @RequireCapability("ORGANIZATION.READ")
    @GetMapping("/{organizationId}/legal-entity")
    public ResponseEntity<?> resolveLegalEntity(
            Authentication auth,
            @PathVariable UUID organizationId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate effectiveDate) {

        UUID tenantId = SecurityContextUtils.tenantId(auth);
        try {
            LegalEntity legalEntity =
                    legalEntityService.resolveSingleActiveForOrganization(tenantId, organizationId, effectiveDate);
            return ResponseEntity.ok(Map.of(
                    "organizationId", organizationId,
                    "legalEntityId", legalEntity.id(),
                    "code", legalEntity.code(),
                    "name", legalEntity.name()));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "EMPLOYER_CONTEXT_AMBIGUOUS",
                    "message", ex.getMessage()));
        }
    }
}
