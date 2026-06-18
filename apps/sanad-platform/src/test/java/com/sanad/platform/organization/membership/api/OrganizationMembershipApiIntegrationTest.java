package com.sanad.platform.organization.membership.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for the Organization Membership REST API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class OrganizationMembershipApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    private UUID tenantId;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        Tenant tenant = tenantRepository.save(
                new Tenant("Acme Corp", "acme-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        tenantId = tenant.getId();

        Organization org = new Organization(tenant, "Acme Riyadh", "desc", OrganizationStatus.ACTIVE);
        organizationId = organizationRepository.save(org).getId();
    }

    private String invitePayload(String email, String displayName) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "tenantId", tenantId.toString(),
                "organizationId", organizationId.toString(),
                "email", email,
                "displayName", displayName
        ));
    }

    private JsonNode inviteMember(String email, String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizationId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitePayload(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ============================================================
    // TEST 1: Invite member -> 201 and persisted row exists
    // ============================================================
    @Test
    @DisplayName("TEST 1: Invite member -> 201, row persisted in DB")
    void inviteMember_persistsRow() throws Exception {
        JsonNode response = inviteMember("alice@example.com", "Alice");

        UUID membershipId = UUID.fromString(response.get("id").asText());
        assertThat(response.get("email").asText()).isEqualTo("alice@example.com");
        assertThat(response.get("status").asText()).isEqualTo("INVITED");

        assertThat(membershipRepository.findById(membershipId)).isPresent();
    }

    // ============================================================
    // TEST 2: Duplicate invite in same organization -> 409
    // ============================================================
    @Test
    @DisplayName("TEST 2: Duplicate invite in same organization -> 409")
    void duplicateInvite_sameOrg_returns409() throws Exception {
        inviteMember("alice@example.com", "Alice");

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships", organizationId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitePayload("ALICE@example.com", "Alice 2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Organization membership already exists for this email"));
    }

    // ============================================================
    // TEST 3: Same email in different organization -> allowed
    // ============================================================
    @Test
    @DisplayName("TEST 3: Same email in different organization -> allowed (201)")
    void sameEmail_differentOrg_allowed() throws Exception {
        inviteMember("shared@example.com", "Shared");

        // Create a second organization under the same tenant
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        Organization org2 = organizationRepository.save(
                new Organization(tenant, "Acme Jeddah", "desc", OrganizationStatus.ACTIVE));
        UUID org2Id = org2.getId();

        String payload = objectMapper.writeValueAsString(Map.of(
                "tenantId", tenantId.toString(),
                "organizationId", org2Id.toString(),
                "email", "shared@example.com",
                "displayName", "Shared in Org2"
        ));

        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships", org2Id)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    // ============================================================
    // TEST 4: List memberships returns only same organization
    // ============================================================
    @Test
    @DisplayName("TEST 4: List memberships returns only same organization's members")
    void listMemberships_returnsOnlySameOrg() throws Exception {
        inviteMember("alice@example.com", "Alice");
        inviteMember("bob@example.com", "Bob");

        // Create a second org and add a member to it
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        Organization org2 = organizationRepository.save(
                new Organization(tenant, "Acme Jeddah", "desc", OrganizationStatus.ACTIVE));
        String payload = objectMapper.writeValueAsString(Map.of(
                "tenantId", tenantId.toString(),
                "organizationId", org2.getId().toString(),
                "email", "carol@example.com",
                "displayName", "Carol"
        ));
        mockMvc.perform(post("/api/v1/organizations/{organizationId}/memberships", org2.getId())
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        // List memberships for the first org — should only see alice and bob, NOT carol
        mockMvc.perform(get("/api/v1/organizations/{organizationId}/memberships", organizationId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].email").value(
                        org.hamcrest.Matchers.containsInAnyOrder("alice@example.com", "bob@example.com")))
                .andExpect(jsonPath("$[*].email").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("carol@example.com"))));
    }

    // ============================================================
    // TEST 5: Get membership by id -> 200
    // ============================================================
    @Test
    @DisplayName("TEST 5: Get membership by id -> 200 OK")
    void getMembership_byId_returns200() throws Exception {
        JsonNode created = inviteMember("alice@example.com", "Alice");

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizationId, created.get("id").asText())
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.status").value("INVITED"));
    }

    // ============================================================
    // TEST 6: Wrong tenantId cannot access membership -> 404
    // ============================================================
    @Test
    @DisplayName("TEST 6: Wrong tenantId cannot access membership -> 404")
    void wrongTenantId_returns404() throws Exception {
        JsonNode created = inviteMember("alice@example.com", "Alice");
        UUID wrongTenantId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        organizationId, created.get("id").asText())
                        .param("tenantId", wrongTenantId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Organization membership not found"));
    }

    // ============================================================
    // TEST 7: Wrong organizationId cannot access membership -> 404
    // ============================================================
    @Test
    @DisplayName("TEST 7: Wrong organizationId cannot access membership -> 404")
    void wrongOrganizationId_returns404() throws Exception {
        JsonNode created = inviteMember("alice@example.com", "Alice");
        UUID wrongOrgId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/organizations/{organizationId}/memberships/{membershipId}",
                        wrongOrgId, created.get("id").asText())
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ============================================================
    // TEST 8: Activate membership -> status ACTIVE
    // ============================================================
    @Test
    @DisplayName("TEST 8: Activate membership -> status ACTIVE")
    void activateMembership_statusActive() throws Exception {
        JsonNode created = inviteMember("alice@example.com", "Alice");
        UUID membershipId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}/activate",
                        organizationId, membershipId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(membershipRepository.findById(membershipId).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.ACTIVE);
    }

    // ============================================================
    // TEST 9: Deactivate membership -> status INACTIVE
    // ============================================================
    @Test
    @DisplayName("TEST 9: Deactivate membership -> status INACTIVE")
    void deactivateMembership_statusInactive() throws Exception {
        JsonNode created = inviteMember("alice@example.com", "Alice");
        UUID membershipId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}/deactivate",
                        organizationId, membershipId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        assertThat(membershipRepository.findById(membershipId).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.INACTIVE);
    }

    // ============================================================
    // TEST 10: Remove membership -> status REMOVED, row still exists
    // ============================================================
    @Test
    @DisplayName("TEST 10: Remove membership -> status REMOVED, row still exists (no hard delete)")
    void removeMembership_statusRemoved_rowStillExists() throws Exception {
        JsonNode created = inviteMember("alice@example.com", "Alice");
        UUID membershipId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(patch("/api/v1/organizations/{organizationId}/memberships/{membershipId}/remove",
                        organizationId, membershipId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));

        // Verify row still exists in DB (soft delete)
        assertThat(membershipRepository.findById(membershipId)).isPresent();
        assertThat(membershipRepository.findById(membershipId).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.REMOVED);
    }
}
