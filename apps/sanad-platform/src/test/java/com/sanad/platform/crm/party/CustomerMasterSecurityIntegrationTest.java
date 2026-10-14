package com.sanad.platform.crm.party;

import com.sanad.platform.crm.concurrency.ETagService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.sanad.platform.crm.test.RlsTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class CustomerMasterSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired ETagService etags;
    @Autowired PlatformTransactionManager transactionManager;

    private UUID foreignTenantId;
    private UUID foreignUserId;
    private UUID foreignAccountId;

    // Seed the foreign record before Spring opens the test-managed transaction.
    // The test transaction must never change tenant identity after its first SQL.
    @BeforeTransaction
    void prepareForeignMergeTarget() {
        foreignTenantId = UUID.randomUUID();
        foreignUserId = UUID.randomUUID();
        foreignAccountId = UUID.randomUUID();
        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
        try {
            RlsTestSupport.setSecurityContext(foreignTenantId, foreignUserId);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Instant now = Instant.now();
                jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (:id,:name,:subdomain,'ACTIVE',:now,:now)",
                        p().addValue("id", foreignTenantId).addValue("name", "merge-foreign")
                                .addValue("subdomain", "merge-" + foreignTenantId.toString().substring(0, 8))
                                .addValue("now", java.sql.Timestamp.from(now)));
                jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (:id,:tenantId,:email,'CRM Security User','ACTIVE','dummy',:now,:now)",
                        p().addValue("id", foreignUserId).addValue("tenantId", foreignTenantId)
                                .addValue("email", "merge-" + foreignUserId.toString().substring(0, 8) + "@example.test")
                                .addValue("now", java.sql.Timestamp.from(now)));
                jdbc.update("INSERT INTO crm_accounts (id,tenant_id,version,display_name,normalized_name,account_type,lifecycle_status,primary_currency_code,preferred_locale,time_zone,source,owner_user_id,created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,0,'Foreign B','foreign b','BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh','CRM005_SECURITY_TEST',:owner,:owner,:owner,:now,:now)",
                        p().addValue("id", foreignAccountId).addValue("tenantId", foreignTenantId)
                                .addValue("owner", foreignUserId).addValue("now", java.sql.Timestamp.from(now)));
            });
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
        }
    }

    @AfterTransaction
    void removeForeignMergeTarget() {
        if (foreignTenantId == null) return;
        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
        try {
            RlsTestSupport.setSecurityContext(foreignTenantId, foreignUserId);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.update("DELETE FROM crm_accounts WHERE tenant_id=:t AND id=:id",
                        p().addValue("t", foreignTenantId).addValue("id", foreignAccountId));
                jdbc.update("DELETE FROM users WHERE tenant_id=:t AND id=:id",
                        p().addValue("t", foreignTenantId).addValue("id", foreignUserId));
                jdbc.update("DELETE FROM tenants WHERE id=:t", p().addValue("t", foreignTenantId));
            });
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
            foreignTenantId = null; foreignUserId = null; foreignAccountId = null;
        }
    }

    private String foreignAccountStatus() {
        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
        try {
            RlsTestSupport.setSecurityContext(foreignTenantId, foreignUserId);
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return tx.execute(status -> jdbc.queryForObject(
                    "SELECT lifecycle_status FROM crm_accounts WHERE tenant_id=:tenantId AND id=:id",
                    p().addValue("tenantId", foreignTenantId).addValue("id", foreignAccountId), String.class));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
        }
    }

    @Test
    void unauthenticatedCustomerMasterRequestReturns401() throws Exception {
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/crm/accounts/{id}/master", unknown))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutCapabilityReturns403() throws Exception {
        Fixture fixture = fixture("master-no-capability", false);
        UUID accountId = account(fixture, "Private Customer");
        mockMvc.perform(get("/api/v1/crm/accounts/{id}/master", accountId)
                        .with(authentication(auth(fixture))))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossTenantRelationshipAssignmentIsDenied() throws Exception {
        Fixture tenantA = fixture("relationship-a", true);
        Fixture tenantB = fixture("relationship-b", true);
        UUID source = account(tenantA, "Source A");
        UUID foreignTarget = account(tenantB, "Foreign B");

        mockMvc.perform(post("/api/v1/crm/accounts/{id}/relationships", source)
                        .with(authentication(auth(tenantA)))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"targetAccountId\":\"" + foreignTarget +
                                "\",\"relationshipType\":\"AFFILIATE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantMergeIsDeniedWithoutChangingEitherRecord() throws Exception {
        Fixture tenantA = fixture("merge-a", true);
        UUID source = account(tenantA, "Source A");
        UUID foreignTarget = foreignAccountId;

        mockMvc.perform(post("/api/v1/crm/accounts/{source}/merge/{target}", source, foreignTarget)
                        .with(authentication(auth(tenantA)))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .header(HttpHeaders.IF_MATCH, etags.etag("customer-master", source, 0))
                        .header("X-Target-If-Match", etags.etag("customer-master", foreignTarget, 0))
                        .contentType("application/json")
                        .content("{\"reason\":\"Cross-tenant isolation proof\"}"))
                .andExpect(status().isNotFound());

        String sourceStatus = jdbc.queryForObject(
                "SELECT lifecycle_status FROM crm_accounts WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", tenantA.tenantId()).addValue("id", source), String.class);
        String targetStatus = foreignAccountStatus();
        org.assertj.core.api.Assertions.assertThat(sourceStatus).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(targetStatus).isEqualTo("ACTIVE");
    }

    private Fixture fixture(String key, boolean grantCapabilities) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (:id,:name,:subdomain,'ACTIVE',:now,:now)",
                p().addValue("id", tenantId).addValue("name", key)
                        .addValue("subdomain", key + "-" + tenantId.toString().substring(0, 8)).addValue("now", java.sql.Timestamp.from(now)));
        // This @Transactional fixture must set the trusted tenant context on the
        // same transaction-bound JDBC connection before writing FORCE-RLS users.
        jdbc.getJdbcTemplate().queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)",
                String.class, tenantId.toString());
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:email,'CRM Security User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test")
                        .addValue("now", java.sql.Timestamp.from(now)));
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:code,'CRM Security Role','CRM-005 security tests','ACTIVE',:now,:now)",
                p().addValue("id", roleId).addValue("tenantId", tenantId)
                        .addValue("code", "CRM_SEC_" + key.toUpperCase().replace('-', '_')).addValue("now", java.sql.Timestamp.from(now)));
        jdbc.update("INSERT INTO user_role_assignments (id,tenant_id,user_id,role_id,organization_id,status,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:userId,:roleId,NULL,'ACTIVE',:now,:now)",
                p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                        .addValue("userId", userId).addValue("roleId", roleId).addValue("now", java.sql.Timestamp.from(now)));
        if (grantCapabilities) {
            List<UUID> capabilityIds = jdbc.query(
                    "SELECT id FROM access_capabilities WHERE code IN ('CRM.ACCOUNT.READ','CRM.ACCOUNT.WRITE')",
                    p(), (rs, row) -> rs.getObject("id", UUID.class));
            for (UUID capabilityId : capabilityIds) {
                jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) " +
                                "VALUES (:id,:tenantId,:roleId,:capabilityId,:now)",
                        p().addValue("id", UUID.randomUUID()).addValue("tenantId", tenantId)
                                .addValue("roleId", roleId).addValue("capabilityId", capabilityId).addValue("now", java.sql.Timestamp.from(now)));
            }
        }
        return new Fixture(tenantId, userId);
    }

    private UUID account(Fixture fixture, String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_accounts (id,tenant_id,version,display_name,normalized_name,account_type," +
                        "lifecycle_status,primary_currency_code,preferred_locale,time_zone,source,owner_user_id," +
                        "created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,0,:name,:normalized," +
                        "'BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh','CRM005_SECURITY_TEST',:owner,:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId()).addValue("name", name)
                        .addValue("normalized", name.toLowerCase()).addValue("owner", fixture.userId()).addValue("now", java.sql.Timestamp.from(now)));
        return id;
    }

    private Authentication auth(Fixture fixture) {
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", fixture.tenantId().toString());
        details.put("user_id", fixture.userId().toString());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(fixture.userId().toString(), null, List.of());
        authentication.setDetails(details);
        return authentication;
    }

    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private record Fixture(UUID tenantId, UUID userId) {}
}
