package com.sanad.platform.user.api;

import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.service.OrganizationMembershipUserLinkService;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.shared.api.PageRequestParams;
import com.sanad.platform.shared.api.PageResponse;
import com.sanad.platform.shared.api.PageResponseBuilder;
import com.sanad.platform.shared.api.SortAllowlist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST adapter for listing organization memberships of a given user within a tenant.
 *
 * <p>Stage 03A: the {@code listMemberships} endpoint is now paginated
 * ({@code ?page=0&size=20&sort=createdAt,desc}).</p>
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
    public ResponseEntity<PageResponse<OrganizationMembershipResponse>> listMemberships(
            @PathVariable UUID userId,
            @Valid PageRequestParams params) {

        UUID tenantId = extractTenantIdFromSecurityContext();
        Set<String> allowedSortFields = Set.of(
                "id", "organizationId", "userId", "email", "status", "createdAt", "updatedAt");
        Pageable pageable = SortAllowlist.toPageable(params, allowedSortFields);
        Page<OrganizationMembershipResponse> page =
                assignmentService.listMembershipsByUser(tenantId, userId, pageable);
        return ResponseEntity.ok(PageResponseBuilder.from(page, page.getContent()));
    }

    /**
     * Extracts the tenant ID from the authenticated security context (JWT claims).
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
