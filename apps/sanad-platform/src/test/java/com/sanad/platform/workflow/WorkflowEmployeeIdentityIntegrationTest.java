package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowEmployeeIdentityIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, 'Workflow Identity Test', ?, 'ACTIVE', ?, ?)",
                tenantId, "wf-id-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'Workflow Identity User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "wf-id-" + userId.toString().substring(0, 8) + "@test", now, now);
    }

    @Test
    void oneUserCannotLinkToTwoEmployeesInSameTenant() {
        insertEmployee("E-100");

        assertThatThrownBy(() -> insertEmployee("E-101"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertEmployee(String employeeNumber) {
        jdbc.update("""
                INSERT INTO hr_employees (
                    id, tenant_id, user_id, employee_number, first_name, last_name,
                    display_name, employment_type, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Workflow', 'Employee', 'Workflow Employee',
                          'FULL_TIME', 'ACTIVE', ?, ?)
                """,
                UUID.randomUUID(), tenantId, userId, employeeNumber,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
    }
}
