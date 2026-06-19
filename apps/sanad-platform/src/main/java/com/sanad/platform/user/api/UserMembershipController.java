package com.sanad.platform.user.api;

import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.service.OrganizationMembershipUserLinkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/memberships")
public class UserMembershipController {

    private final OrganizationMembershipUserLinkService assignmentService;

    public UserMembershipController(
            OrganizationMembershipUserLinkService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public ResponseEntity<List<OrganizationMembershipResponse>> listMemberships(
            @PathVariable UUID userId,
            @RequestParam UUID tenantId) {
        return ResponseEntity.ok(
                assignmentService.listMembershipsByUser(tenantId, userId));
    }
}
