package com.sanad.platform.organization.membership.api;

import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
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

    public OrganizationMembershipAssignmentController(
            OrganizationMembershipUserLinkService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PatchMapping("/{membershipId}/assign/{userId}")
    public ResponseEntity<OrganizationMembershipResponse> assignUser(
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {
        return ResponseEntity.ok(assignmentService.linkUser(
                tenantId, organizationId, membershipId, userId));
    }

    @PatchMapping("/{membershipId}/unassign")
    public ResponseEntity<OrganizationMembershipResponse> unassignUser(
            @PathVariable UUID organizationId,
            @PathVariable UUID membershipId,
            @RequestParam UUID tenantId) {
        return ResponseEntity.ok(assignmentService.unlinkUser(
                tenantId, organizationId, membershipId));
    }
}
