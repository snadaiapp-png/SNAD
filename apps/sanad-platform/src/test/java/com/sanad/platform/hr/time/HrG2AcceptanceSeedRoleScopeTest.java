package com.sanad.platform.hr.time;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the G2 authenticated-acceptance fixture.
 *
 * <p>G2 HTTP capability checks are evaluated without an organization id, so
 * acceptance role grants must be tenant-wide (organization_id IS NULL).
 * Organization membership is seeded separately and must not be conflated with
 * the authorization grant scope.</p>
 */
class HrG2AcceptanceSeedRoleScopeTest {

    private static final String SEED = "/sql/g2-acceptance-seed.sql";

    @Test
    void g2AcceptanceRoleAssignmentsAreTenantWide() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(SEED)) {
            assertThat(stream).as("G2 acceptance seed must exist").isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .as("acceptance roles must be granted tenant-wide because @RequireCapability evaluates these G2 routes without organizationId")
                .contains("-- G2 acceptance authorization grants are tenant-wide")
                .contains("'33333333-3333-4333-8333-333333333381',\n    '33333333-3333-4333-8333-333333333331',\n    '33333333-3333-4333-8333-333333333341',\n    '33333333-3333-4333-8333-333333333371',\n    NULL,")
                .contains("'33333333-3333-4333-8333-333333333382',\n    '33333333-3333-4333-8333-333333333331',\n    '33333333-3333-4333-8333-333333333342',\n    '33333333-3333-4333-8333-333333333372',\n    NULL,")
                .contains("'33333333-3333-4333-8333-333333333383',\n    '33333333-3333-4333-8333-333333333331',\n    '33333333-3333-4333-8333-333333333343',\n    '33333333-3333-4333-8333-333333333373',\n    NULL,");
    }
}
