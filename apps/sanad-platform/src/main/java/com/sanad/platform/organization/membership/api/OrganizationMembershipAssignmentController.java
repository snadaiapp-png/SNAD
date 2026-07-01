package com.sanad.platform.organization.membership.api;

import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.security.tenant.TenantResolver;
import com.sanad.platform.security.authorization.RequireCapability;
import com.sanad.platform.organization.membership.service.OrganizationMembershipUserLinkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/memberships")
public class OrganizationMembershipAssignmentController {

    private final OrganizationMembershipUserLinkService assignmentService;
    private final com.sanad.platform.security.tenant.TenantResolver tenantResolver;

    public OrganizationMembershipAssignmentController(
            OrganizationMembershipUserLinkService assignmentService,
            com.sanad.platform.security.tenant.TenantResolver tenantResolver) {
        this.assignmentService = assignmentService;
        this.tenantResolver = tenantResolver;
    }

    @RequireCapability("MEMBERSHIP.WRITE")
    @PatchMapping("/{membershipId}/assign/{userId}")
    public ResponseEntity<OrganizationMembershipResponse> assignUser(
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {
        UUID verifiedTenantId = tenantResolver.validateClientSelector(tenantId);
        return ResponseEntity.ok(assignmentService.linkUser(
                verifiedTenantId, organizationId, membershipId, userId));
    }

    @RequireCapability("MEMBERSHIP.WRITE")
    @PatchMapping("/{membershipId}/unassign")
    public ResponseEntity<OrganizationMembershipResponse> unassignUser(
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @RequestParam UUID tenantId) {
        UUID verifiedTenantId = tenantResolver.validateClientSelector(tenantId);
        return ResponseEntity.ok(assignmentService.unlinkUser(
                verifiedTenantId, organizationId, membershipId));
    }
}
