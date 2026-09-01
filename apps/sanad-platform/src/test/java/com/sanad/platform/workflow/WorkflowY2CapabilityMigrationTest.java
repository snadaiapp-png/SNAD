package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 0 / Task 3 — Y2 fine-grained Workflow capability catalog.
 *
 * <p>Verifies the additive capability seed in
 * {@code V20260830_1__workflow_y2_identity_and_capabilities.sql}:
 * the thirteen new WORKFLOW.* capability codes exist after Flyway migration,
 * the legacy coarse capabilities remain, and the ADMIN compatibility mapping
 * grants every new capability to every tenant's ADMIN role.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowY2CapabilityMigrationTest {

    private static final List<String> Y2_CAPABILITY_CODES = List.of(
            "WORKFLOW.DESIGN", "WORKFLOW.VALIDATE", "WORKFLOW.PUBLISH",
            "WORKFLOW.START", "WORKFLOW.TASK_EXECUTE", "WORKFLOW.REASSIGN",
            "WORKFLOW.DELEGATE", "WORKFLOW.CANCEL", "WORKFLOW.INCIDENT_MANAGE",
            "WORKFLOW.MONITOR", "WORKFLOW.AUDIT_VIEW", "WORKFLOW.BREAK_GLASS",
            "WORKFLOW.SELF_APPROVAL_OVERRIDE");

    private static final List<String> LEGACY_CAPABILITY_CODES = List.of(
            "WORKFLOW.VIEW", "WORKFLOW.WRITE", "WORKFLOW.ADMIN", "WORKFLOW.APPROVE");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void y2CapabilitiesExistAfterFlywayMigration() {
        List<String> codes = jdbc.queryForList(
                "SELECT code FROM access_capabilities WHERE code LIKE 'WORKFLOW.%'",
                String.class);
        assertThat(codes).contains(Y2_CAPABILITY_CODES.toArray(new String[0]));
    }

    @Test
    void legacyWorkflowCapabilitiesRemainActiveDuringMigration() {
        List<String> codes = jdbc.queryForList(
                "SELECT code FROM access_capabilities "
                        + "WHERE code IN ('WORKFLOW.VIEW','WORKFLOW.WRITE','WORKFLOW.ADMIN','WORKFLOW.APPROVE') "
                        + "AND status = 'ACTIVE'",
                String.class);
        assertThat(codes).containsExactlyInAnyOrderElementsOf(LEGACY_CAPABILITY_CODES);
    }

    @Test
    void adminCompatibilityMappingCoversEveryY2Capability() {
        // Platform invariant "ADMIN gets all active capabilities": the Y2 seed
        // must bind each new capability to every tenant's ADMIN role.
        String placeholders = String.join(", ", java.util.Collections.nCopies(Y2_CAPABILITY_CODES.size(), "?"));
        String sql = """
                SELECT t.id AS tenant_id, ac.code AS capability_code
                FROM tenants t
                JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
                JOIN access_capabilities ac ON ac.status = 'ACTIVE'
                    AND ac.code IN ("""
                + placeholders
                + """
                )
                WHERE NOT EXISTS (
                    SELECT 1 FROM role_capabilities rc
                    WHERE rc.tenant_id = t.id
                      AND rc.role_id = r.id
                      AND rc.capability_id = ac.id
                )
                """;
        List<Map<String, Object>> missing = jdbc.queryForList(
                sql, Y2_CAPABILITY_CODES.toArray());
        assertThat(missing)
                .as("Every tenant ADMIN role must hold every Y2 Workflow capability")
                .isEmpty();
    }
}
