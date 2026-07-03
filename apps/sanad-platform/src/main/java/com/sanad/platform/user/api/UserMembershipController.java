package com.sanad.platform.user.api;

import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.service.OrganizationMembershipUserLinkService;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST adapter for listing organization memberships of a given user within a tenant.
 *
 * <p>DEFECT-021 remediation: the {@code listMemberships} endpoint now
 * requires the {@code MEMBERSHIP.READ} capability.</p>
 *
 * <p>EXEC-PROMPT-010R correction: the tenant scope is now obtained from
 * the authenticated security context (JWT claims set by
 * {@code JwtAuthenticationFilter}), NOT from a request-supplied query
 * parameter. The client cannot select another tenant by changing a
 * query parameter. The {@code tenantId} query parameter has been
 * removed entirely; if a caller supplies one, it is silently ignored
 * (the JWT-derived tenant always wins).</p>
 *
 * <p>The {@code JwtAuthenticationFilter} already enforces that any
 * caller-supplied {@code tenantId} query parameter must match the JWT
 * tenant (returns 403 on mismatch), so removing the parameter here is
 * defense-in-depth rather than a behavior change for legitimate
 * callers.</p>
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/memberships")
public class UserMembershipController {

    private final OrganizationMembershipUserLinkService assignmentService;

    public UserMembershipController(
            OrganizationMembershipUserLinkService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @RequireCapability("MEMBERSHIP.READ")
    @GetMapping
    public ResponseEntity<List<OrganizationMembershipResponse>> listMemberships(
            @PathVariable UUID userId) {

        UUID tenantId = extractTenantIdFromSecurityContext();

        return ResponseEntity.ok(
                assignmentService.listMembershipsByUser(tenantId, userId));
    }

    /**
     * Extracts the tenant ID from the authenticated security context.
     *
     * <p>The {@code JwtAuthenticationFilter} sets the JWT claims as
     * authentication details (a {@code Map<String, Object>}). The
     * {@code tenant_id} claim is the authoritative tenant scope and
     * cannot be overridden by the client.</p>
     *
     * @return the tenant UUID from the JWT
     * @throws IllegalStateException if the security context does not
     *         contain a valid tenant ID (this indicates a filter-chain
     *         misconfiguration and should never happen for a request
     *         that passed {@code JwtAuthenticationFilter})
     */
    private UUID extractTenantIdFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated principal in security context");
        }

        Object detailsObj = authentication.getDetails();
        if (!(detailsObj instanceof Map<?, ?>)) {
            throw new IllegalStateException(
                    "Authentication details are not a Map — JWT filter misconfiguration");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) detailsObj;
        Object tenantIdObj = details.get("tenant_id");
        if (tenantIdObj == null) {
            throw new IllegalStateException("Missing tenant_id in JWT claims");
        }

        return UUID.fromString((String) tenantIdObj);
    }
}
