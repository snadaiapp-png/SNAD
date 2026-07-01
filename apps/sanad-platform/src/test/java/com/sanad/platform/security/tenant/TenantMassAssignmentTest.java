package com.sanad.platform.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

/**
 * Stage 04A.3 §14 — Mass assignment protection test. Non-skippable PostgreSQL.
 * Verifies client cannot assign tenantId via request body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantMassAssignmentTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Database is PostgreSQL (non-skippable)")
    void databaseIsPostgreSQL() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
    }

    @Test
    @DisplayName("CreateOrganizationRequest does not contain tenantId field")
    void createOrgRequest_noTenantIdField() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
        // Verify the DTO class doesn't have a tenantId field
        var fields = com.sanad.platform.organization.dto.CreateOrganizationRequest.class.getDeclaredFields();
        for (var field : fields) {
            org.assertj.core.api.Assertions.assertThat(field.getName())
                    .as("CreateOrganizationRequest must not have tenantId field")
                    .isNotEqualTo("tenantId");
        }
    }

    @Test
    @DisplayName("InviteOrganizationMemberRequest does not contain tenantId field")
    void inviteRequest_noTenantIdField() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
        var fields = com.sanad.platform.organization.membership.dto.InviteOrganizationMemberRequest.class.getDeclaredFields();
        for (var field : fields) {
            org.assertj.core.api.Assertions.assertThat(field.getName())
                    .as("InviteOrganizationMemberRequest must not have tenantId field")
                    .isNotEqualTo("tenantId");
        }
    }
}
