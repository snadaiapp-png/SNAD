package com.sanad.platform.crm.party;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.domain.AccountRepository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Account V2 functional integration tests.
 * Tests the full path: AccountUseCases → JdbcAccountRepository → DB + Audit + Timeline.
 * Uses local profile (H2 PostgreSQL mode) for fast verification.
 * Testcontainers/PostgreSQL tests will run on CI runner.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AccountUseCasesIntegrationTest {

    @Autowired
    AccountUseCases accountUseCases;

    @Autowired
    com.sanad.platform.crm.party.domain.AccountRepository accountRepository;

    @Autowired
    org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;

    private UUID seedTenantAndOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) VALUES (:id, 'Test Tenant', 'test-" + tenantId.toString().substring(0, 8) + "', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", tenantId));
        jdbc.update("INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at) VALUES (:id, :tenantId, :email, 'Test Owner', 'ACTIVE', 'dummy', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", userId).addValue("tenantId", tenantId).addValue("email", "owner-" + userId.toString().substring(0, 8) + "@test.example"));
        return tenantId;
    }

    @Test
    @DisplayName("Successful account create with valid owner")
    void successfulCreate() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        CreateAccountCommand cmd = new CreateAccountCommand("Test Acct", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST");
        AccountRecord created = accountUseCases.create(tenantId, actorId, cmd);
        assertNotNull(created.id());
        assertEquals("Test Acct", created.displayName());
        assertEquals("ACTIVE", created.lifecycleStatus());
        assertEquals(0, created.version());
    }

    @Test
    @DisplayName("Successful account get by ID")
    void successfulGet() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        CreateAccountCommand cmd = new CreateAccountCommand("Get Test", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST");
        AccountRecord created = accountUseCases.create(tenantId, actorId, cmd);
        AccountRecord fetched = accountUseCases.getById(tenantId, created.id());
        assertEquals(created.id(), fetched.id());
        assertEquals("Get Test", fetched.displayName());
    }

    @Test
    @DisplayName("Successful account list")
    void successfulList() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Acct 1", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Acct 2", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        var list = accountUseCases.list(tenantId, 50, null);
        assertTrue(list.size() >= 2);
    }

    @Test
    @DisplayName("Successful account update")
    void successfulUpdate() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Original", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        AccountRecord updated = accountUseCases.update(tenantId, actorId, created.id(),
                new UpdateAccountCommand("Updated Name", null, null, null, null, null, null), created.version());
        assertEquals("Updated Name", updated.displayName());
        assertEquals(1, updated.version());
    }

    @Test
    @DisplayName("Successful account archive")
    void successfulArchive() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Archive Me", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        AccountRecord archived = accountUseCases.archive(tenantId, actorId, created.id(), created.version());
        assertEquals("ARCHIVED", archived.lifecycleStatus());
        assertEquals(1, archived.version());
    }

    @Test
    @DisplayName("Archived account update is rejected")
    void archivedUpdateRejected() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Will Archive", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        accountUseCases.archive(tenantId, actorId, created.id(), created.version());
        assertThrows(CrmContractException.class, () ->
                accountUseCases.update(tenantId, actorId, created.id(),
                        new UpdateAccountCommand("Try Update", null, null, null, null, null, null), created.version() + 1));
    }

    @Test
    @DisplayName("Stale version update is rejected")
    void staleVersionRejected() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Stale Test", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        // Update once to advance version
        accountUseCases.update(tenantId, actorId, created.id(),
                new UpdateAccountCommand("First Update", null, null, null, null, null, null), created.version());
        // Try update with old version
        assertThrows(CrmContractException.class, () ->
                accountUseCases.update(tenantId, actorId, created.id(),
                        new UpdateAccountCommand("Stale Update", null, null, null, null, null, null), created.version()));
    }

    @Test
    @DisplayName("Cross-tenant get is rejected")
    void crossTenantGetRejected() {
        UUID tenantA = seedTenantAndOwner();
        UUID actorA = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantA), UUID.class);
        AccountRecord created = accountUseCases.create(tenantA, actorA, new CreateAccountCommand("Tenant A Acct", "BUSINESS", actorA, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        UUID tenantB = seedTenantAndOwner();
        assertThrows(CrmContractException.class, () -> accountUseCases.getById(tenantB, created.id()));
    }

    @Test
    @DisplayName("List excludes archived accounts")
    void listExcludesArchived() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord active = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Active Acct", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        AccountRecord toArchive = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("To Archive", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        accountUseCases.archive(tenantId, actorId, toArchive.id(), toArchive.version());
        var list = accountUseCases.list(tenantId, 50, null);
        assertTrue(list.stream().anyMatch(a -> a.id().equals(active.id())));
        assertFalse(list.stream().anyMatch(a -> a.id().equals(toArchive.id())));
    }

    @Test
    @DisplayName("Self-parent is rejected")
    void selfParentRejected() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Self Parent", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        assertThrows(CrmContractException.class, () ->
                accountUseCases.update(tenantId, actorId, created.id(),
                        new UpdateAccountCommand(null, null, created.id(), null, null, null, null), created.version()));
    }

    @Test
    @DisplayName("Audit row exists after create")
    void auditExistsAfterCreate() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Audit Test", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        Integer count = jdbc.queryForObject("SELECT count(*) FROM platform_audit_logs WHERE target_tenant_id = :t AND resource_id = :rid AND action = 'CREATE'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId).addValue("rid", created.id().toString()), Integer.class);
        assertNotNull(count);
        assertTrue(count > 0, "Audit row should exist after create");
    }

    @Test
    @DisplayName("Timeline row exists after create")
    void timelineExistsAfterCreate() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Timeline Test", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        Integer count = jdbc.queryForObject("SELECT count(*) FROM crm_timeline_events WHERE tenant_id = :t AND subject_id = :sid AND event_type = 'crm.account.created'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId).addValue("sid", created.id()), Integer.class);
        assertNotNull(count);
        assertTrue(count > 0, "Timeline row should exist after create");
    }

    @Test
    @DisplayName("Successful account restore")
    void successfulRestore() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Restore Me", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        AccountRecord archived = accountUseCases.archive(tenantId, actorId, created.id(), created.version());
        AccountRecord restored = accountUseCases.restore(tenantId, actorId, created.id(), archived.version());
        assertEquals("ACTIVE", restored.lifecycleStatus());
        assertEquals(2, restored.version());
    }

    @Test
    @DisplayName("Update writes audit row")
    void updateWritesAudit() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Audit Update", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        accountUseCases.update(tenantId, actorId, created.id(), new UpdateAccountCommand("Updated", null, null, null, null, null, null), created.version());
        Integer count = jdbc.queryForObject("SELECT count(*) FROM platform_audit_logs WHERE target_tenant_id = :t AND resource_id = :rid AND action = 'UPDATE'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId).addValue("rid", created.id().toString()), Integer.class);
        assertNotNull(count);
        assertTrue(count > 0, "Audit UPDATE row should exist");
    }

    @Test
    @DisplayName("Archive writes audit row")
    void archiveWritesAudit() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Audit Archive", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        accountUseCases.archive(tenantId, actorId, created.id(), created.version());
        Integer count = jdbc.queryForObject("SELECT count(*) FROM platform_audit_logs WHERE target_tenant_id = :t AND resource_id = :rid AND action = 'ARCHIVE'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId).addValue("rid", created.id().toString()), Integer.class);
        assertNotNull(count);
        assertTrue(count > 0, "Audit ARCHIVE row should exist");
    }

    @Test
    @DisplayName("Update writes timeline row")
    void updateWritesTimeline() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Timeline Update", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        accountUseCases.update(tenantId, actorId, created.id(), new UpdateAccountCommand("Updated", null, null, null, null, null, null), created.version());
        Integer count = jdbc.queryForObject("SELECT count(*) FROM crm_timeline_events WHERE tenant_id = :t AND subject_id = :sid AND event_type = 'crm.account.updated'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId).addValue("sid", created.id()), Integer.class);
        assertNotNull(count);
        assertTrue(count > 0, "Timeline UPDATE row should exist");
    }

    @Test
    @DisplayName("Archive writes timeline row")
    void archiveWritesTimeline() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Timeline Archive", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        accountUseCases.archive(tenantId, actorId, created.id(), created.version());
        Integer count = jdbc.queryForObject("SELECT count(*) FROM crm_timeline_events WHERE tenant_id = :t AND subject_id = :sid AND event_type = 'crm.account.archived'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId).addValue("sid", created.id()), Integer.class);
        assertNotNull(count);
        assertTrue(count > 0, "Timeline ARCHIVE row should exist");
    }

    @Test
    @DisplayName("Audit result is SUCCESS")
    void auditResultIsSuccess() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Result Test", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        String result = jdbc.queryForObject("SELECT result FROM platform_audit_logs WHERE target_tenant_id = :t AND resource_id = :rid AND action = 'CREATE'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId).addValue("rid", created.id().toString()), String.class);
        assertEquals("SUCCESS", result);
    }

    @Test
    @DisplayName("Audit correlation_id is populated")
    void auditCorrelationIdPopulated() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId, new CreateAccountCommand("Correlation Test", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        String correlationId = jdbc.queryForObject("SELECT correlation_id FROM platform_audit_logs WHERE target_tenant_id = :t AND resource_id = :rid AND action = 'CREATE'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId).addValue("rid", created.id().toString()), String.class);
        assertNotNull(correlationId);
        assertFalse(correlationId.isBlank());
    }

    @Test
    @DisplayName("Cross-tenant update is rejected")
    void crossTenantUpdateRejected() {
        UUID tenantA = seedTenantAndOwner();
        UUID actorA = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantA), UUID.class);
        AccountRecord created = accountUseCases.create(tenantA, actorA, new CreateAccountCommand("Cross Update", "BUSINESS", actorA, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        UUID tenantB = seedTenantAndOwner();
        assertThrows(CrmContractException.class, () ->
                accountUseCases.update(tenantB, UUID.randomUUID(), created.id(),
                        new UpdateAccountCommand("Hacked", null, null, null, null, null, null), 0L));
    }

    @Test
    @DisplayName("Cross-tenant archive is rejected")
    void crossTenantArchiveRejected() {
        UUID tenantA = seedTenantAndOwner();
        UUID actorA = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantA), UUID.class);
        AccountRecord created = accountUseCases.create(tenantA, actorA, new CreateAccountCommand("Cross Archive", "BUSINESS", actorA, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        UUID tenantB = seedTenantAndOwner();
        assertThrows(CrmContractException.class, () ->
                accountUseCases.archive(tenantB, UUID.randomUUID(), created.id(), 0L));
    }

    @Test
    @DisplayName("Cross-tenant list does not expose other tenant accounts")
    void crossTenantListIsolation() {
        UUID tenantA = seedTenantAndOwner();
        UUID actorA = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantA), UUID.class);
        accountUseCases.create(tenantA, actorA, new CreateAccountCommand("Tenant A Only", "BUSINESS", actorA, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        UUID tenantB = seedTenantAndOwner();
        var listB = accountUseCases.list(tenantB, 50, null);
        assertFalse(listB.stream().anyMatch(a -> "Tenant A Only".equals(a.displayName())));
    }

    @Test
    @DisplayName("Cross-tenant owner assignment is rejected")
    void crossTenantOwnerAssignmentRejected() {
        UUID tenantA = seedTenantAndOwner();
        UUID actorA = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantA), UUID.class);
        UUID tenantB = seedTenantAndOwner();
        UUID actorB = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantB), UUID.class);
        // Creating an account in tenant A with an owner from tenant B must fail validation.
        CrmContractException ex = assertThrows(CrmContractException.class, () ->
                accountUseCases.create(tenantA, actorA, new CreateAccountCommand(
                        "Cross Owner", "BUSINESS", actorB, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST")));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("Cross-tenant parent assignment is rejected")
    void crossTenantParentAssignmentRejected() {
        UUID tenantA = seedTenantAndOwner();
        UUID actorA = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantA), UUID.class);
        AccountRecord parentInA = accountUseCases.create(tenantA, actorA,
                new CreateAccountCommand("Parent A", "BUSINESS", actorA, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        UUID tenantB = seedTenantAndOwner();
        UUID actorB = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantB), UUID.class);
        // Creating an account in tenant B with a parent from tenant A must fail.
        assertThrows(CrmContractException.class, () ->
                accountUseCases.create(tenantB, actorB, new CreateAccountCommand(
                        "Child B", "BUSINESS", actorB, parentInA.id(), "SAR", "ar-SA", "Asia/Riyadh", "TEST")));
    }

    @Test
    @DisplayName("Cross-tenant restore is rejected")
    void crossTenantRestoreRejected() {
        UUID tenantA = seedTenantAndOwner();
        UUID actorA = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantA), UUID.class);
        AccountRecord created = accountUseCases.create(tenantA, actorA,
                new CreateAccountCommand("Cross Restore", "BUSINESS", actorA, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        AccountRecord archived = accountUseCases.archive(tenantA, actorA, created.id(), created.version());
        UUID tenantB = seedTenantAndOwner();
        assertThrows(CrmContractException.class, () ->
                accountUseCases.restore(tenantB, UUID.randomUUID(), created.id(), archived.version()));
    }

    @Test
    @DisplayName("Failed update writes no audit row (transactional rollback)")
    void failedUpdateWritesNoAuditRow() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId,
                new CreateAccountCommand("Rollback Audit", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        long baseline = jdbc.queryForObject(
                "SELECT count(*) FROM platform_audit_logs WHERE target_tenant_id = :t AND resource_id = :rid",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId)
                        .addValue("rid", created.id().toString()), Long.class);
        // A stale version will throw CrmContractException; no audit UPDATE row should be persisted.
        assertThrows(CrmContractException.class, () ->
                accountUseCases.update(tenantId, actorId, created.id(),
                        new UpdateAccountCommand("Should Rollback", null, null, null, null, null, null),
                        created.version() + 999L));
        long after = jdbc.queryForObject(
                "SELECT count(*) FROM platform_audit_logs WHERE target_tenant_id = :t AND resource_id = :rid AND action = 'UPDATE'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId)
                        .addValue("rid", created.id().toString()), Long.class);
        assertEquals(0L, after, "Failed update must not persist an UPDATE audit row");
        long totalAfter = jdbc.queryForObject(
                "SELECT count(*) FROM platform_audit_logs WHERE target_tenant_id = :t AND resource_id = :rid",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId)
                        .addValue("rid", created.id().toString()), Long.class);
        assertEquals(baseline, totalAfter, "Failed update must not change audit row count");
    }

    @Test
    @DisplayName("Failed update writes no timeline row (transactional rollback)")
    void failedUpdateWritesNoTimelineRow() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId,
                new CreateAccountCommand("Rollback Timeline", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        long baseline = jdbc.queryForObject(
                "SELECT count(*) FROM crm_timeline_events WHERE tenant_id = :t AND subject_id = :sid",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId)
                        .addValue("sid", created.id()), Long.class);
        assertThrows(CrmContractException.class, () ->
                accountUseCases.update(tenantId, actorId, created.id(),
                        new UpdateAccountCommand("Should Rollback", null, null, null, null, null, null),
                        created.version() + 999L));
        long updatedTimeline = jdbc.queryForObject(
                "SELECT count(*) FROM crm_timeline_events WHERE tenant_id = :t AND subject_id = :sid AND event_type = 'crm.account.updated'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId)
                        .addValue("sid", created.id()), Long.class);
        assertEquals(0L, updatedTimeline, "Failed update must not persist an updated timeline row");
        long totalAfter = jdbc.queryForObject(
                "SELECT count(*) FROM crm_timeline_events WHERE tenant_id = :t AND subject_id = :sid",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId)
                        .addValue("sid", created.id()), Long.class);
        assertEquals(baseline, totalAfter, "Failed update must not change timeline row count");
    }

    @Test
    @DisplayName("Supplied X-Correlation-ID is persisted on audit row")
    void suppliedCorrelationIdIsPersisted() throws Exception {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        String suppliedId = "supplied-corr-id-" + UUID.randomUUID();
        // Set the X-Correlation-ID header on the request context.
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", suppliedId);
        var attrs = new org.springframework.web.context.request.ServletRequestAttributes(request);
        var previous = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(attrs);
        try {
            AccountRecord created = accountUseCases.create(tenantId, actorId,
                    new CreateAccountCommand("Supplied Correlation", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
            String persisted = jdbc.queryForObject(
                    "SELECT correlation_id FROM platform_audit_logs WHERE target_tenant_id = :t AND resource_id = :rid AND action = 'CREATE'",
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId)
                            .addValue("rid", created.id().toString()), String.class);
            assertEquals(suppliedId, persisted,
                    "Audit correlation_id must equal the supplied X-Correlation-ID header value");
        } finally {
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(previous);
        }
    }

    @Test
    @DisplayName("Generated correlation_id is a valid UUID when no header supplied")
    void generatedCorrelationIdIsValidUuid() {
        UUID tenantId = seedTenantAndOwner();
        UUID actorId = jdbc.queryForObject("SELECT id FROM users WHERE tenant_id = :t LIMIT 1",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId), UUID.class);
        AccountRecord created = accountUseCases.create(tenantId, actorId,
                new CreateAccountCommand("Generated Correlation", "BUSINESS", actorId, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST"));
        String correlationId = jdbc.queryForObject(
                "SELECT correlation_id FROM platform_audit_logs WHERE target_tenant_id = :t AND resource_id = :rid AND action = 'CREATE'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("t", tenantId)
                        .addValue("rid", created.id().toString()), String.class);
        assertNotNull(correlationId);
        // Should be parseable as a UUID (the SpringCorrelationContextAdapter falls back to UUID.randomUUID)
        assertDoesNotThrow(() -> UUID.fromString(correlationId),
                "When no X-Correlation-ID header is supplied, audit correlation_id must be a UUID");
    }

}
