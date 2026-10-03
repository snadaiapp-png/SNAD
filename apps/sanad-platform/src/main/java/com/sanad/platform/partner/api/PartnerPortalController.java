package com.sanad.platform.partner.api;

import com.sanad.platform.partner.delegation.PartnerDelegationDecision;
import com.sanad.platform.partner.delegation.PartnerDelegationGate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Partner portal authorization boundary.
 *
 * partnerId is never authoritative when supplied by the caller. The signed JWT
 * partner_id claim is resolved first and any mismatching parameter fails closed
 * with PARTNER_SCOPE_MISMATCH.
 */
@RestController
@RequestMapping("/api/v1/partner")
public class PartnerPortalController {

    private final PartnerClaimResolver claimResolver;
    private final PartnerPortalService portalService;
    private final PartnerDelegationGate delegationGate;

    public PartnerPortalController(
            PartnerClaimResolver claimResolver,
            PartnerPortalService portalService,
            PartnerDelegationGate delegationGate) {
        this.claimResolver = claimResolver;
        this.portalService = portalService;
        this.delegationGate = delegationGate;
    }

    @GetMapping("/me")
    public ResponseEntity<PartnerPortalService.PartnerSummary> me() {
        PartnerClaimResolver.PartnerPrincipal principal = claimResolver.resolveRequired();
        return ResponseEntity.ok(
                portalService.requireActiveMembership(principal.userId(), principal.partnerId()));
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<PartnerPortalService.PartnerTenantBindingView>> tenants(
            @RequestParam(required = false) UUID partnerId) {
        PartnerClaimResolver.PartnerPrincipal principal = claimResolver.resolveRequired();
        claimResolver.assertRequestedPartnerMatches(partnerId, principal.partnerId());
        return ResponseEntity.ok(
                portalService.listOwnTenants(principal.userId(), principal.partnerId()));
    }

    @GetMapping("/delegations")
    public ResponseEntity<List<PartnerPortalService.PartnerDelegationView>> delegations(
            @RequestParam(required = false) UUID partnerId) {
        PartnerClaimResolver.PartnerPrincipal principal = claimResolver.resolveRequired();
        claimResolver.assertRequestedPartnerMatches(partnerId, principal.partnerId());
        return ResponseEntity.ok(
                portalService.listOwnDelegations(principal.userId(), principal.partnerId()));
    }

    /**
     * Explicit authorization probe used by partner surfaces before a delegated
     * action. T6 does not mutate tenant business state; mutation adapters remain
     * later tasks and must consume the same gate.
     */
    @GetMapping("/tenants/{tenantId}/authorization")
    public ResponseEntity<PartnerDelegationDecision> authorize(
            @PathVariable UUID tenantId,
            @RequestParam String capability,
            @RequestParam(required = false) UUID partnerId) {
        PartnerClaimResolver.PartnerPrincipal principal = claimResolver.resolveRequired();
        claimResolver.assertRequestedPartnerMatches(partnerId, principal.partnerId());
        portalService.requireActiveMembership(principal.userId(), principal.partnerId());

        PartnerDelegationDecision decision =
                delegationGate.check(principal.partnerId(), tenantId, capability);
        if (!decision.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, decision.reason());
        }
        return ResponseEntity.ok(decision);
    }
}
