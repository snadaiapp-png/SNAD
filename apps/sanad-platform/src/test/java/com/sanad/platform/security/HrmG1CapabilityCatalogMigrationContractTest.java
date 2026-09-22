package com.sanad.platform.security;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HrmG1CapabilityCatalogMigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V20260922_1__hr_g1_operator_capabilities_and_admin_scopes.sql";

    @Test
    void migrationSeedsCanonicalG1CapabilitiesAndAdminTenantScopes() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION)) {
            assertNotNull(input, "HRM-G1 capability migration must exist");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            List<String> expected = List.of(
                    "HRM.RECRUITMENT.OPENING.VIEW",
                    "HRM.RECRUITMENT.OPENING.MANAGE",
                    "HRM.RECRUITMENT.OPENING.PUBLISH",
                    "HRM.RECRUITMENT.CANDIDATE.VIEW",
                    "HRM.RECRUITMENT.CANDIDATE.MANAGE",
                    "HRM.RECRUITMENT.APPLICATION.MANAGE",
                    "HRM.RECRUITMENT.APPLICATION.SUBMIT",
                    "HRM.RECRUITMENT.APPLICATION.WITHDRAW",
                    "HRM.RECRUITMENT.APPLICATION.ADVANCE",
                    "HRM.RECRUITMENT.APPLICATION.REJECT",
                    "HRM.RECRUITMENT.INTERVIEW.MANAGE",
                    "HRM.RECRUITMENT.INTERVIEW.SCHEDULE",
                    "HRM.RECRUITMENT.INTERVIEW.RECORD_OUTCOME",
                    "HRM.RECRUITMENT.OFFER.MANAGE",
                    "HRM.RECRUITMENT.OFFER.ACCEPT",
                    "HRM.RECRUITMENT.OFFER.DECLINE",
                    "HRM.RECRUITMENT.OFFER.EXTEND",
                    "HRM.RECRUITMENT.OFFER.APPROVE",
                    "HRM.RECRUITMENT.HIRE.CONVERT",
                    "HRM.ONBOARDING.PLAN.MANAGE",
                    "HRM.ONBOARDING.TASK.COMPLETE",
                    "HRM.ONBOARDING.TASK.WAIVE"
            );

            expected.forEach(code -> assertTrue(sql.contains("'" + code + "'"), code));
            assertFalse(sql.contains("HRM.ONBOARDING.PLAN.VIEW"),
                    "Non-canonical PLAN.VIEW must not be introduced");
            assertTrue(sql.contains("INSERT INTO role_capabilities"));
            assertTrue(sql.contains("INSERT INTO access_scope_grants"));
            assertTrue(sql.contains("code = 'ADMIN'"));
            assertTrue(sql.contains("scope_type = 'TENANT'"));
            assertFalse(sql.contains("HR_MANAGER"),
                    "G1 capability backfill must not silently widen HR_MANAGER");
        }
    }
}
