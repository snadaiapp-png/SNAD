package com.sanad.platform.partner.delegation;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Application-layer decision evidence for Wave 2 Task 5
 * ({@link PartnerDelegationGate}).
 *
 * Proves the W2-T5 required matrix on a real PostgreSQL Direct database:
 * active valid delegation = ALLOW; missing/expired/suspended/revoked/inactive
 * delegation = DENY; foreign tenant / foreign partner / forged ids = DENY;
 * inactive binding = DENY; inactive partner = DENY; inactive/unknown
 * capability = DENY; business-data capabilities (CRM/HRM/Accounting) can never
 * be delegated; delegation is per-capability (no implicit sibling access).
 */
class PartnerDelegationGatePostgresTest {

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String CONTROL_TENANT =
            "00000000-0000-0000-0000-000000000001";

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static PartnerDelegationGate gate;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "PartnerDelegationGatePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for PartnerDelegationGatePostgresTest");

        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
        Flyway.configure()
                .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL), USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .load()
                .migrate();

        dataSource = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL), USER, PASSWORD);
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        gate = new PartnerDelegationGate(jdbc, CONTROL_TENANT);
    }

    @AfterEach
    void restoreRegistryStatus() {
        // Defensive: keep the shared registry rows ACTIVE for sibling tests.
        inTx(() -> jdbc.update(
                "UPDATE partner_delegation_capabilities SET status='ACTIVE' " +
                "WHERE status <> 'ACTIVE'"));
    }

    // ------------------------------------------------------------------
    // required decision matrix
    // ------------------------------------------------------------------

    @Test
    void activeValidDelegationAllows() {
        Fixture f = fixture();
        insertGrant(f, "TENANT.ACTIVATE", null, null);
        PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), "TENANT.ACTIVATE");
        assertThat(d.allowed()).isTrue();
        assertThat(d.reason()).isEqualTo("DELEGATION_ALLOWED");
        assertThat(d.grantId()).isNotNull();
        assertThat(d.bindingId()).isNotNull();
        assertThat(d.capabilityCode()).isEqualTo("TENANT.ACTIVATE");
        assertThat(d.partnerId()).isEqualTo(f.partnerA());
        assertThat(d.tenantId()).isEqualTo(f.tenantId());
    }

    @Test
    void missingDelegationDenies() {
        Fixture f = fixture();
        PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), "TENANT.SUSPEND");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("DELEGATION_MISSING");
    }

    @Test
    void expiredDelegationDenies() {
        Fixture f = fixture();
        insertGrant(f, "TENANT.USER.MANAGE",
                Instant.now().minusSeconds(172800), Instant.now().minusSeconds(86400));
        PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), "TENANT.USER.MANAGE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("DELEGATION_EXPIRED");
    }

    @Test
    void revokedDelegationDenies() {
        Fixture f = fixture();
        UUID grant = insertGrant(f, "TENANT.SUSPEND", null, null);
        inTx(() -> jdbc.update(
                "UPDATE partner_delegation_grants SET status='REVOKED', revoked_at=NOW(), " +
                "revoked_reason='test revocation', updated_by=? WHERE id=?",
                f.actor(), grant));
        PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), "TENANT.SUSPEND");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("DELEGATION_REVOKED");
    }

    @Test
    void suspendedDelegationDenies() {
        Fixture f = fixture();
        UUID grant = insertGrant(f, "TENANT.SUSPEND", null, null);
        inTx(() -> jdbc.update(
                "UPDATE partner_delegation_grants SET status='SUSPENDED', suspended_at=NOW(), " +
                "suspended_reason='test suspension', updated_by=? WHERE id=?",
                f.actor(), grant));
        PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), "TENANT.SUSPEND");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("DELEGATION_SUSPENDED");
    }

    @Test
    void inactiveDelegationDenies() {
        Fixture f = fixture();
        UUID grant = insertGrant(f, "TENANT.SUSPEND", null, null);
        inTx(() -> jdbc.update(
                "UPDATE partner_delegation_grants SET status='INACTIVE', updated_by=? WHERE id=?",
                f.actor(), grant));
        PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), "TENANT.SUSPEND");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("DELEGATION_INACTIVE");
    }

    @Test
    void foreignTenantBoundToAnotherPartnerDeniesWithPartnerMismatch() {
        Fixture f = fixture();
        // partnerB owns an ACTIVE binding for tenant2; partnerA probes tenant2.
        UUID tenant2 = UUID.randomUUID();
        createTenant(tenant2);
        bind(f.partnerB(), tenant2);
        PartnerDelegationDecision d = check(f.partnerA(), tenant2, "TENANT.ACTIVATE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("PARTNER_MISMATCH");
    }

    @Test
    void unboundTenantDeniesWithTenantMismatch() {
        Fixture f = fixture();
        UUID unbound = UUID.randomUUID();
        createTenant(unbound);
        PartnerDelegationDecision d = check(f.partnerA(), unbound, "TENANT.ACTIVATE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("TENANT_MISMATCH");
    }

    @Test
    void forgedPartnerIdDeniesWithPartnerMismatch() {
        Fixture f = fixture();
        // Caller claims partnerB but tenant is governed by partnerA.
        PartnerDelegationDecision d = check(f.partnerB(), f.tenantId(), "TENANT.ACTIVATE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("PARTNER_MISMATCH");
    }

    @Test
    void forgedUnknownPartnerIdDenies() {
        Fixture f = fixture();
        UUID forged = UUID.randomUUID();
        PartnerDelegationDecision d = check(forged, f.tenantId(), "TENANT.ACTIVATE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("PARTNER_MISMATCH");
    }

    @Test
    void forgedTenantIdDeniesWithTenantMismatch() {
        Fixture f = fixture();
        PartnerDelegationDecision d = check(f.partnerA(), UUID.randomUUID(), "TENANT.ACTIVATE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("TENANT_MISMATCH");
    }

    @Test
    void missingPartnerContextDenies() {
        Fixture f = fixture();
        PartnerDelegationDecision d = check(null, f.tenantId(), "TENANT.ACTIVATE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("PARTNER_CONTEXT_REQUIRED");
    }

    @Test
    void missingTenantContextDenies() {
        Fixture f = fixture();
        PartnerDelegationDecision d = check(f.partnerA(), null, "TENANT.ACTIVATE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("TENANT_CONTEXT_REQUIRED");
    }

    @Test
    void inactiveBindingDenies() {
        Fixture f = fixture();
        UUID grant = insertGrant(f, "TENANT.ACTIVATE", null, null);
        assertThat(grant).isNotNull();
        inTx(() -> jdbc.update(
                "UPDATE partner_tenant_bindings SET status='SUSPENDED', suspended_at=NOW(), " +
                "suspended_reason='test suspension', updated_by=? WHERE tenant_id=? AND status='ACTIVE'",
                f.actor(), f.tenantId()));
        PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), "TENANT.ACTIVATE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("BINDING_INACTIVE");
    }

    @Test
    void expiredBindingWindowDenies() {
        Fixture f = fixture();
        UUID grant = insertGrant(f, "TENANT.ACTIVATE", null, null);
        assertThat(grant).isNotNull();
        inTx(() -> jdbc.update(
                "UPDATE partner_tenant_bindings SET valid_from=NOW() - INTERVAL '2 days', " +
                "valid_until=NOW() - INTERVAL '1 day', updated_by=? " +
                "WHERE tenant_id=? AND status='ACTIVE'",
                f.actor(), f.tenantId()));
        PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), "TENANT.ACTIVATE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("BINDING_EXPIRED");
    }

    @Test
    void inactivePartnerDenies() {
        Fixture f = fixture();
        UUID grant = insertGrant(f, "TENANT.ACTIVATE", null, null);
        assertThat(grant).isNotNull();
        inTx(() -> jdbc.update(
                "UPDATE partners SET status='SUSPENDED', suspended_at=NOW(), " +
                "suspended_reason='test suspension', updated_by=? WHERE id=?",
                f.actor(), f.partnerA()));
        PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), "TENANT.ACTIVATE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("PARTNER_INACTIVE");
    }

    @Test
    void inactiveCapabilityDenies() {
        Fixture f = fixture();
        insertGrant(f, "SUBSCRIPTION.CANCEL", null, null);
        inTx(() -> jdbc.update(
                "UPDATE partner_delegation_capabilities SET status='INACTIVE' " +
                "WHERE code='SUBSCRIPTION.CANCEL'"));
        PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), "SUBSCRIPTION.CANCEL");
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("CAPABILITY_INACTIVE");
    }

    @Test
    void businessDataCapabilitiesCanNeverBeDelegated() {
        Fixture f = fixture();
        for (String businessData : new String[]{"CRM.READ", "HRM.READ", "ACCOUNTING.READ"}) {
            PartnerDelegationDecision d = check(f.partnerA(), f.tenantId(), businessData);
            assertThat(d.allowed())
                    .as("business-data capability %s must never be delegatable", businessData)
                    .isFalse();
            assertThat(d.reason()).isEqualTo("CAPABILITY_UNKNOWN");
        }
    }

    @Test
    void delegationIsPerCapabilityWithNoImplicitSiblingAccess() {
        Fixture f = fixture();
        insertGrant(f, "TENANT.ACTIVATE", null, null);
        PartnerDelegationDecision create = check(f.partnerA(), f.tenantId(), "TENANT.CREATE");
        assertThat(create.allowed()).isFalse();
        assertThat(create.reason()).isEqualTo("DELEGATION_MISSING");
        PartnerDelegationDecision billing = check(f.partnerA(), f.tenantId(), "BILLING.READ");
        assertThat(billing.allowed()).isFalse();
        assertThat(billing.reason()).isEqualTo("DELEGATION_MISSING");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private PartnerDelegationDecision check(UUID partnerId, UUID tenantId, String capability) {
        return tx.execute(status -> gate.check(partnerId, tenantId, capability));
    }

    private interface TxWork {
        void run();
    }

    private void inTx(TxWork work) {
        tx.executeWithoutResult(status -> {
            // Fixture writes mirror the production control plane: platform
            // scope is required by FORCE RLS on partner governance tables.
            // app.partner_id stays UNSET — the audit RLS platform branch
            // requires IS NULL, and grant writes fire audit triggers.
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                    String.class, CONTROL_TENANT);
            work.run();
        });
    }

    private Fixture fixture() {
        UUID tenant = UUID.randomUUID();
        UUID partnerA = UUID.randomUUID();
        UUID partnerB = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        inTx(() -> {
            jdbc.update(
                    "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                    "VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                    tenant, "W2-T5-G " + tenant, "w2t5g-" + tenant.toString().substring(0, 12));
            for (UUID partner : new UUID[]{partnerA, partnerB}) {
                jdbc.update(
                        "INSERT INTO partners (id,partner_type,status,created_by) " +
                        "VALUES (?,'PARTNER','ACTIVE',?)", partner, actor);
            }
            jdbc.update(
                    "INSERT INTO partner_tenant_bindings " +
                    "(id,partner_id,tenant_id,created_by,updated_by) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), partnerA, tenant, actor, actor);
        });
        return new Fixture(tenant, partnerA, partnerB, actor);
    }

    private UUID insertGrant(Fixture f, String capability, Instant validFrom, Instant validUntil) {
        UUID grantId = UUID.randomUUID();
        inTx(() -> jdbc.update(
                "INSERT INTO partner_delegation_grants " +
                "(id,partner_id,tenant_id,capability_code,valid_from,valid_until,created_by,updated_by) " +
                "VALUES (?,?,?,?,COALESCE(?, NOW()),?,?,?)",
                grantId, f.partnerA(), f.tenantId(), capability,
                validFrom == null ? null : java.sql.Timestamp.from(validFrom),
                validUntil == null ? null : java.sql.Timestamp.from(validUntil),
                f.actor(), f.actor()));
        return grantId;
    }

    private void createTenant(UUID tenantId) {
        inTx(() -> jdbc.update(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                "VALUES (?,?,?,'ACTIVE',NOW(),NOW())",
                tenantId, "W2-T5-U " + tenantId, "w2t5u-" + tenantId.toString().substring(0, 12)));
    }

    private void bind(UUID partnerId, UUID tenantId) {
        inTx(() -> jdbc.update(
                "INSERT INTO partner_tenant_bindings " +
                "(id,partner_id,tenant_id,created_by,updated_by) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), partnerId, tenantId, partnerId, partnerId));
    }

    private record Fixture(UUID tenantId, UUID partnerA, UUID partnerB, UUID actor) {}
}
