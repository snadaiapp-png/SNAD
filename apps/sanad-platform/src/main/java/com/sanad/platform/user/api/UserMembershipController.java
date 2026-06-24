package com.sanad.platform.user.api;

import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.service.OrganizationMembershipUserLinkService;
import com.sanad.platform.security.authorization.RequireCapability;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST adapter for listing organization memberships of a given user within a tenant.
 *
 * <p>The tenant scope is supplied as a required query parameter and is
 * passed unchanged to the underlying service. No repository access or
 * business logic is permitted in this adapter.</p>
 *
 * <p>DEFECT-021 remediation: the {@code listMemberships} endpoint now
 * requires the {@code MEMBERSHIP.READ} capability. Previously any
 * authenticated user within the tenant could enumerate another user's
 * memberships, which constituted information disclosure (low severity
 * because the data is tenant-scoped, but still a violation of the
 * project's RBAC convention that every endpoint must declare a
 * capability).</p>
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
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {
        return ResponseEntity.ok(
                assignmentService.listMembershipsByUser(tenantId, userId));
    }
}
