package com.sanad.platform.organization.legalentity;

import com.sanad.platform.security.SecurityContextUtils;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only Organization-scoped Legal Entity eligibility surface.
 *
 * <p>The authenticated tenant is authoritative. A caller-supplied tenantId is
 * retained for explicit API scoping and must match the authenticated tenant;
 * mismatches fail closed before repository access. No mutation is exposed by
 * this controller.</p>
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/legal-entities")
public class LegalEntityEligibilityController {

    private final LegalEntityService legalEntityService;

    public LegalEntityEligibilityController(LegalEntityService legalEntityService) {
        this.legalEntityService = legalEntityService;
    }

    @GetMapping("/eligible")
    @RequireCapability("ORGANIZATION.READ")
    public List<EligibleLegalEntityResponse> listEligible(
            Authentication authentication,
            @PathVariable UUID organizationId,
            @RequestParam UUID tenantId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate) {

        UUID authenticatedTenantId = SecurityContextUtils.tenantId(authentication);
        if (!tenantId.equals(authenticatedTenantId)) {
            throw new AccessDeniedException("Tenant scope mismatch");
        }

        return legalEntityService
                .listActiveEligible(authenticatedTenantId, organizationId, effectiveDate)
                .stream()
                .map(EligibleLegalEntityResponse::from)
                .toList();
    }
}
