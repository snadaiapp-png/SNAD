package com.sanad.platform.hr.time;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guards for the G2 authenticated-acceptance fixture.
 *
 * <p>G2 HTTP capability checks are evaluated without an organization id, so
 * acceptance role grants must be tenant-wide (organization_id IS NULL).
 * Organization membership is seeded separately and must not be conflated with
 * the authorization grant scope.</p>
 *
 * <p>The G2 scope resolver is canonical-only: User -> Person -> Employment ->
 * PRIMARY Assignment. The acceptance fixture therefore must seed that graph;
 * legacy hr_employees.user_id / manager_id links are not sufficient.</p>
 */
class HrG2AcceptanceSeedRoleScopeTest {

    private static final String SEED = "/sql/g2-acceptance-seed.sql";

    @Test
    void g2AcceptanceRoleAssignmentsAreTenantWide() throws IOException {
        String sql = seedSql();

        assertThat(sql)
                .as("acceptance roles must be granted tenant-wide because @RequireCapability evaluates these G2 routes without organizationId")
                .contains("-- G2 acceptance authorization grants are tenant-wide")
                .contains("'33333333-3333-4333-8333-333333333381',\n    '33333333-3333-4333-8333-333333333331',\n    '33333333-3333-4333-8333-333333333341',\n    '33333333-3333-4333-8333-333333333371',\n    NULL,")
                .contains("'33333333-3333-4333-8333-333333333382',\n    '33333333-3333-4333-8333-333333333331',\n    '33333333-3333-4333-8333-333333333342',\n    '33333333-3333-4333-8333-333333333372',\n    NULL,")
                .contains("'33333333-3333-4333-8333-333333333383',\n    '33333333-3333-4333-8333-333333333331',\n    '33333333-3333-4333-8333-333333333343',\n    '33333333-3333-4333-8333-333333333373',\n    NULL,");
    }

    @Test
    void g2AcceptanceSeedBuildsCanonicalPersonEmploymentAssignmentGraph() throws IOException {
        String sql = seedSql();

        assertThat(sql)
                .as("G2 identities must be resolvable through canonical User -> Person -> Employment")
                .contains("INSERT INTO hr_people")
                .contains("person_id, legal_entity_id, worker_classification_code")
                .contains("'33333333-3333-4333-8333-333333333364'")
                .contains("'33333333-3333-4333-8333-333333333365'")
                .contains("'33333333-3333-4333-8333-333333333366'");

        assertThat(sql)
                .as("G2 team scope must be represented by canonical PRIMARY assignment reporting")
                .contains("INSERT INTO hr_employee_assignments")
                .contains("'33333333-3333-4333-8333-333333333367'")
                .contains("'33333333-3333-4333-8333-333333333368'")
                .contains("'33333333-3333-4333-8333-333333333369'")
                .contains("'33333333-3333-4333-8333-333333333368',\n    '33333333-3333-4333-8333-333333333331',\n    '33333333-3333-4333-8333-333333333361',\n    '33333333-3333-4333-8333-333333333335',\n    NULL,\n    NULL,\n    '33333333-3333-4333-8333-333333333367'");
    }

    private String seedSql() throws IOException {
        try (var stream = getClass().getResourceAsStream(SEED)) {
            assertThat(stream).as("G2 acceptance seed must exist").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
