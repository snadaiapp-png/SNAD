package com.sanad.platform.crm.collaboration;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task C3 — Contact participant role exclusivity + owner/participant DB invariant.
 *
 * <p>Proves the C3 database contract that for the CONTACT entity type
 * only:</p>
 * <ul>
 *   <li>One user may hold at most ONE active participant role per contact
 *       (the W2 exclusivity rule, scoped CONTACT-only — TASK/CASE behavior
 *       is unchanged).</li>
 *   <li>The contact owner cannot simultaneously be an active participant
 *       (CRM_CONTACT_OWNER_CANNOT_PARTICIPATE).</li>
 *   <li>An active participant cannot be moved into the contact owner slot
 *       (CRM_CONTACT_PARTICIPANT_CANNOT_BECOME_OWNER).</li>
 * </ul>
 *
 * <p>Tests target the disposable {@code test_migration} database (never
 * shared {@code sanad}) using the {@link MigrationTestSchemaSupport}
 * isolation pattern.</p>
 */
@DisplayName("Task C3 — Contact participant invariants (PostgreSQL Direct)")
class ContactParticipantInvariantPostgresTest {

    private static final UUID TENANT_A = UUID.fromString("c3c30000-0000-4000-8000-00000000a001");
    private static final UUID TENANT_B = UUID.fromString("c3c30000-0000-4000-8000-00000000b001");
    private static final UUID USER_A = UUID.fromString("c3c30000-0000-4000-8000-00000000a002");
    private static final UUID USER_B = UUID.fromString("c3c30000-0000-4000-8000-00000000b002");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;

    @BeforeAll
    static void setup() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ContactParticipantInvariantPostgresTest");
        } catch (Throwable ignored) {
            ok = false;
        }
        Assumptions.assumeTrue(ok, "PostgreSQL Direct required");
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        Flyway flyway = Flyway.configure()
                .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false).validateOnMigrate(true).load();
                // Self-sufficiency: always start from a canonical clean state so the
                // shared test_migration history never depends on prior test order.
                flyway.clean();
                flyway.migrate();
        var ds = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        jdbc = new NamedParameterJdbcTemplate(ds);
        transactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
    }

    @BeforeEach
    void seed() {
        // Wipe the relevant rows from the disposable test_migration DB so
        // tests are independent. Order: participants first (FK to crm_contacts),
        // then contacts, then accounts/users. Tenants are reused (ON CONFLICT
        // DO NOTHING in ensureTenant).
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            del("crm_entity_participants");
            del("crm_contacts");
            del("crm_accounts");
            del("users");
            setGuc(TENANT_B);
            del("crm_entity_participants");
            del("crm_contacts");
            del("crm_accounts");
            del("users");
        });
        // Clear GUC outside the cleanup transaction so subsequent seeding
        // (which runs without a transaction) doesn't accidentally inherit it.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', NULL, false)",
                new MapSqlParameterSource(), String.class);
        ensureTenant(TENANT_A, "Tenant A");
        ensureTenant(TENANT_B, "Tenant B");
        ensureUser(USER_A, TENANT_A);
        ensureUser(USER_B, TENANT_A);
    }

    // ── A. Same CONTACT + same user CANNOT hold both COLLABORATOR + WATCHER ──

    @Test
    @DisplayName("A. Same CONTACT/user cannot hold both active COLLABORATOR and WATCHER roles")
    void a_sameContactSameUserCannotHoldBothActiveRoles() {
        UUID contactId = seedContact(TENANT_A, "Alice", null);
        // Insert COLLABORATOR
        tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_A, "COLLABORATOR"));
        // Second active role on same CONTACT+user must be rejected
        assertThatThrownBy(() -> tx(TENANT_A, () ->
                insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_A, "WATCHER")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ── B. Same user may participate in TWO different CONTACTs ──

    @Test
    @DisplayName("B. Same user may participate in two different contacts (same role)")
    void b_sameUserMayParticipateInTwoDifferentContacts() {
        UUID c1 = seedContact(TENANT_A, "Contact1", null);
        UUID c2 = seedContact(TENANT_A, "Contact2", null);
        tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", c1, USER_A, "COLLABORATOR"));
        // Same user, different contact, same role — must succeed
        tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", c2, USER_A, "COLLABORATOR"));
        assertThat(countActiveParticipants(TENANT_A, "CONTACT", c1)).isEqualTo(1);
        assertThat(countActiveParticipants(TENANT_A, "CONTACT", c2)).isEqualTo(1);
    }

    // ── C. Two different users may participate in same CONTACT ──

    @Test
    @DisplayName("C. Two different users may participate in the same contact")
    void c_twoDifferentUsersMayParticipateInSameContact() {
        UUID contactId = seedContact(TENANT_A, "Contact", null);
        tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_A, "COLLABORATOR"));
        tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_B, "WATCHER"));
        assertThat(countActiveParticipants(TENANT_A, "CONTACT", contactId)).isEqualTo(2);
    }

    // ── D. Removed historical COLLABORATOR + new active WATCHER is allowed ──

    @Test
    @DisplayName("D. Removed historical participant + new active different role is allowed")
    void d_removedHistoricalThenNewActiveRoleIsAllowed() {
        UUID contactId = seedContact(TENANT_A, "Contact", null);
        UUID oldParticipantId = UUID.randomUUID();
        tx(TENANT_A, () -> insertParticipant(oldParticipantId, TENANT_A, "CONTACT", contactId, USER_A, "COLLABORATOR"));
        // Mark the COLLABORATOR as removed (historical)
        tx(TENANT_A, () -> removeParticipant(oldParticipantId, USER_A));
        // Now the same user may be added as active WATCHER
        tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_A, "WATCHER"));
        assertThat(countActiveParticipants(TENANT_A, "CONTACT", contactId)).isEqualTo(1);
    }

    // ── E. Active owner cannot be inserted as CONTACT COLLABORATOR ──

    @Test
    @DisplayName("E. Active owner cannot be inserted as CONTACT COLLABORATOR")
    void e_activeOwnerCannotBeInsertedAsCollaborator() {
        UUID contactId = seedContact(TENANT_A, "Owned", USER_A); // owner = USER_A
        assertThatThrownBy(() -> tx(TENANT_A, () ->
                insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_A, "COLLABORATOR")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ── F. Active owner cannot be inserted as CONTACT WATCHER ──

    @Test
    @DisplayName("F. Active owner cannot be inserted as CONTACT WATCHER")
    void f_activeOwnerCannotBeInsertedAsWatcher() {
        UUID contactId = seedContact(TENANT_A, "Owned", USER_A);
        assertThatThrownBy(() -> tx(TENANT_A, () ->
                insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_A, "WATCHER")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ── G. Existing active participant cannot be UPDATEd so user_id becomes current owner ──

    @Test
    @DisplayName("G. Existing active participant cannot be UPDATEd so user_id becomes current contact owner")
    void g_existingParticipantCannotBeUpdatedToOwnerUserId() {
        UUID contactId = seedContact(TENANT_A, "Owned", USER_A); // owner = USER_A
        UUID participantId = UUID.randomUUID();
        // USER_B starts as participant
        tx(TENANT_A, () -> insertParticipant(participantId, TENANT_A, "CONTACT", contactId, USER_B, "COLLABORATOR"));
        // Try to flip user_id to USER_A (the owner)
        assertThatThrownBy(() -> tx(TENANT_A, () ->
                jdbc.update("UPDATE crm_entity_participants SET user_id = :uid WHERE id = :id",
                        p("uid", USER_A).addValue("id", participantId))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ── H. Existing participant cannot be moved by UPDATE to a CONTACT whose owner is that user ──

    @Test
    @DisplayName("H. Existing participant cannot be moved by UPDATE to a CONTACT whose owner is that user")
    void h_participantCannotBeMovedToContactOwnedByThatUser() {
        UUID ownedByA = seedContact(TENANT_A, "OwnedByA", USER_A); // owner = USER_A
        UUID contact2 = seedContact(TENANT_A, "Contact2", null);
        UUID participantId = UUID.randomUUID();
        tx(TENANT_A, () -> insertParticipant(participantId, TENANT_A, "CONTACT", contact2, USER_A, "COLLABORATOR"));
        // Move participant to a contact whose owner is USER_A — must be rejected
        assertThatThrownBy(() -> tx(TENANT_A, () ->
                jdbc.update("UPDATE crm_entity_participants SET entity_id = :eid WHERE id = :id",
                        p("eid", ownedByA).addValue("id", participantId))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ── I. crm_contacts.owner_user_id cannot be changed to a user who is already an active participant ──

    @Test
    @DisplayName("I. Owner cannot be changed to a user who is already an active participant")
    void i_ownerCannotBeChangedToActiveParticipant() {
        UUID contactId = seedContact(TENANT_A, "Contact", null);
        // USER_A is an active participant on this contact
        tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_A, "COLLABORATOR"));
        // Try to make USER_A the owner — must be rejected
        assertThatThrownBy(() -> tx(TENANT_A, () ->
                jdbc.update("UPDATE crm_contacts SET owner_user_id = :uid WHERE id = :id",
                        p("uid", USER_A).addValue("id", contactId))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ── J. Owner MAY be changed to a user whose participant row is historical/removed ──

    @Test
    @DisplayName("J. Owner may be changed to a user whose participant row is historical/removed")
    void j_ownerMayBeChangedToHistoricalParticipant() {
        UUID contactId = seedContact(TENANT_A, "Contact", null);
        UUID oldParticipantId = UUID.randomUUID();
        tx(TENANT_A, () -> insertParticipant(oldParticipantId, TENANT_A, "CONTACT", contactId, USER_A, "COLLABORATOR"));
        tx(TENANT_A, () -> removeParticipant(oldParticipantId, USER_A));
        // Make USER_A the owner — historical/removed participant, must succeed
        tx(TENANT_A, () -> jdbc.update("UPDATE crm_contacts SET owner_user_id = :uid WHERE id = :id",
                p("uid", USER_A).addValue("id", contactId)));
        assertThat(getOwner(TENANT_A, contactId)).isEqualTo(USER_A);
    }

    // ── K. CONTACT-only scope — TASK allows both COLLABORATOR + WATCHER for same user ──

    @Test
    @DisplayName("K. TASK/CASE semantics unchanged — same TASK/user may hold both COLLABORATOR and WATCHER")
    void k_taskCaseSemanticsUnchanged() {
        UUID taskId = seedTask(TENANT_A, "Task");
        // Insert COLLABORATOR + WATCHER for same user on same TASK
        tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "TASK", taskId, USER_A, "COLLABORATOR"));
        // With C3 contact-only W2 index, this MUST still be allowed for TASK
        tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "TASK", taskId, USER_A, "WATCHER"));
        assertThat(countActiveParticipants(TENANT_A, "TASK", taskId)).isEqualTo(2);
    }

    // ── L. Wrong tenant / nonexistent contact participant remains rejected by existing trigger ──

    @Test
    @DisplayName("L. Cross-tenant contact participant insertion remains rejected")
    void l_crossTenantContactParticipantRejected() {
        UUID contactB = seedContact(TENANT_B, "ContactB", null);
        assertThatThrownBy(() -> tx(TENANT_A, () ->
                insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactB, USER_A, "COLLABORATOR")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ── M. Missing app.tenant_id remains fail-closed (FORCE RLS) ──

    @Test
    @DisplayName("M. Missing app.tenant_id fails closed for participant insert")
    void m_missingGucFailsClosed() {
        UUID contactId = seedContact(TENANT_A, "Contact", null);
        // Attempt insert WITHOUT GUC
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            jdbc.update("SELECT set_config('app.tenant_id', '', false)", new MapSqlParameterSource());
            insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_A, "COLLABORATOR");
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ── N. Wrong app.tenant_id fails closed ──

    @Test
    @DisplayName("N. Wrong tenant GUC fails closed for participant insert")
    void n_wrongGucFailsClosed() {
        UUID contactA = seedContact(TENANT_A, "ContactA", null);
        UUID contactB = seedContact(TENANT_B, "ContactB", null);
        // TENANT_A's GUC, insert participant on TENANT_B's contact
        assertThatThrownBy(() -> tx(TENANT_A, () ->
                insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactB, USER_A, "COLLABORATOR")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ── O. Concurrency: same CONTACT/user, two concurrent attempts must produce exactly one winner ──

    @Test
    @DisplayName("O. Concurrent same CONTACT/user role insertion — exactly one wins")
    void o_concurrentSameContactUserOnlyOneWins() throws Exception {
        UUID contactId = seedContact(TENANT_A, "Concurrent", null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();
        Thread t1 = new Thread(() -> {
            try {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_A, "COLLABORATOR"));
            } catch (Throwable t) {
                errorA.set(t);
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_A, "WATCHER"));
            } catch (Throwable t) {
                errorB.set(t);
            }
        });
        t1.start();
        t2.start();
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        t1.join(15_000);
        t2.join(15_000);
        // Exactly one must succeed
        int errors = (errorA.get() == null ? 0 : 1) + (errorB.get() == null ? 0 : 1);
        assertThat(errors)
                .as("Exactly one concurrent insertion must succeed; the other must fail")
                .isEqualTo(1);
        // Final state: exactly one active participant row
        assertThat(countActiveParticipants(TENANT_A, "CONTACT", contactId))
                .as("Terminal state must have exactly one active participant")
                .isEqualTo(1);
    }

    // ── P. Owner/participant race — owner transfer vs participant insertion ──

    @Test
    @DisplayName("P. Concurrent owner-transfer and participant-add for same user cannot both succeed")
    void p_ownerParticipantRace() throws Exception {
        UUID contactId = seedContact(TENANT_A, "Race", null);
        // First, USER_B becomes an active participant
        tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_B, "COLLABORATOR"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> transferError = new AtomicReference<>();
        AtomicReference<Throwable> participantError = new AtomicReference<>();
        // Thread A: attempts owner transfer to USER_B (who is currently an active participant)
        Thread tA = new Thread(() -> {
            try {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                tx(TENANT_A, () -> jdbc.update(
                        "UPDATE crm_contacts SET owner_user_id = :uid WHERE id = :id",
                        p("uid", USER_B).addValue("id", contactId)));
            } catch (Throwable t) {
                transferError.set(t);
            }
        });
        // Thread B: attempts to add USER_A as participant (USER_A is currently the owner of nothing
        // — but we want a race that could violate the invariant; we add USER_B as WATCHER,
        // which would conflict if the owner transfer to USER_B succeeds).
        Thread tB = new Thread(() -> {
            try {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                tx(TENANT_A, () -> insertParticipant(UUID.randomUUID(), TENANT_A, "CONTACT", contactId, USER_B, "WATCHER"));
            } catch (Throwable t) {
                participantError.set(t);
            }
        });
        tA.start();
        tB.start();
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        tA.join(15_000);
        tB.join(15_000);
        // At least one mutation must fail — terminal state must NOT have
        // owner_user_id = USER_B AND an active participant with user_id = USER_B.
        boolean ownerIsB = USER_B.equals(getOwner(TENANT_A, contactId));
        boolean userBHasActiveParticipant = userHasActiveParticipant(TENANT_A, "CONTACT", contactId, USER_B);
        assertThat(ownerIsB && userBHasActiveParticipant)
                .as("Terminal state must NOT have owner == USER_B AND active participant USER_B")
                .isFalse();
        // At least one of the two concurrent operations must have failed
        int failures = (transferError.get() != null ? 1 : 0) + (participantError.get() != null ? 1 : 0);
        assertThat(failures)
                .as("At least one of the racing mutations must fail to preserve the invariant")
                .isGreaterThanOrEqualTo(1);
    }

    // ── HELPERS ──────────────────────────────────────────────────────────

    private void setGuc(UUID t) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                p("t", t.toString()), String.class);
    }

    private void tx(UUID t, Runnable r) {
        transactions.executeWithoutResult(s -> {
            setGuc(t);
            r.run();
        });
    }

    private void insertParticipant(UUID id, UUID tenant, String type, UUID entity, UUID user, String role) {
        jdbc.update("""
                INSERT INTO crm_entity_participants
                  (id, tenant_id, version, entity_type, entity_id, user_id, role, added_at, added_by)
                VALUES (:id, :t, 0, :type, :eid, :uid, :role, NOW(), :uid)
                """, p("id", id).addValue("t", tenant).addValue("type", type)
                .addValue("eid", entity).addValue("uid", user).addValue("role", role));
    }

    private void removeParticipant(UUID participantId, UUID removedBy) {
        jdbc.update("""
                UPDATE crm_entity_participants
                SET removed_at = NOW(), removed_by = :rb
                WHERE id = :id AND removed_at IS NULL
                """, p("id", participantId).addValue("rb", removedBy));
    }

    private long countActiveParticipants(UUID tenant, String type, UUID entity) {
        Long count = transactions.execute(s -> {
            setGuc(tenant);
            return jdbc.queryForObject("""
                    SELECT COUNT(*) FROM crm_entity_participants
                    WHERE tenant_id = :t AND entity_type = :type AND entity_id = :eid
                    AND removed_at IS NULL
                    """,
                    p("t", tenant).addValue("type", type).addValue("eid", entity),
                    Long.class);
        });
        return count != null ? count : 0L;
    }

    private boolean userHasActiveParticipant(UUID tenant, String type, UUID entity, UUID user) {
        Boolean exists = transactions.execute(s -> {
            setGuc(tenant);
            return jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM crm_entity_participants
                    WHERE tenant_id = :t AND entity_type = :type AND entity_id = :eid
                    AND user_id = :uid AND removed_at IS NULL)
                    """,
                    p("t", tenant).addValue("type", type).addValue("eid", entity).addValue("uid", user),
                    Boolean.class);
        });
        return Boolean.TRUE.equals(exists);
    }

    private UUID getOwner(UUID tenant, UUID contactId) {
        return transactions.execute(s -> {
            setGuc(tenant);
            return jdbc.queryForObject(
                    "SELECT owner_user_id FROM crm_contacts WHERE tenant_id = :t AND id = :id",
                    p("t", tenant).addValue("id", contactId),
                    UUID.class);
        });
    }

    private void del(String table) {
        // Delete by tenant_id (current GUC scopes the WHERE on the FORCE RLS
        // tables, but plain DELETE on non-RLS tables like users still needs
        // an explicit tenant_id filter so we don't touch seeded system data).
        jdbc.update("DELETE FROM " + table + " WHERE tenant_id = :t",
                new MapSqlParameterSource().addValue("t", currentGucTenant()));
    }

    private UUID currentGucTenant() {
        // The @BeforeEach setGuc(...) call sets the tenant GUC before each
        // del(...) call, so we can read it back here. (Reading it via SQL
        // avoids threading a tenantId parameter through every helper call.)
        return jdbc.queryForObject(
                "SELECT current_setting('app.tenant_id', true)::uuid",
                new MapSqlParameterSource(),
                UUID.class);
    }

    private UUID seedContact(UUID tenant, String name, UUID ownerId) {
        UUID id = UUID.randomUUID();
        // Run inside a tenant-scoped transaction so FORCE RLS on crm_contacts
        // (enabled by V20260823_1) accepts the INSERT.
        tx(tenant, () -> {
            MapSqlParameterSource params = p("id", id).addValue("t", tenant)
                    .addValue("n", name).addValue("norm", name.toLowerCase())
                    .addValue("u", USER_A);
            if (ownerId == null) {
                jdbc.update("""
                        INSERT INTO crm_contacts (id, tenant_id, given_name, display_name, normalized_name,
                            lifecycle_status, created_by, updated_by, created_at, updated_at)
                        VALUES (:id, :t, :n, :n, :norm, 'ACTIVE', :u, :u, NOW(), NOW())
                        """, params);
            } else {
                params.addValue("oid", ownerId);
                jdbc.update("""
                        INSERT INTO crm_contacts (id, tenant_id, given_name, display_name, normalized_name,
                            lifecycle_status, owner_user_id, created_by, updated_by, created_at, updated_at)
                        VALUES (:id, :t, :n, :n, :norm, 'ACTIVE', :oid, :u, :u, NOW(), NOW())
                        """, params);
            }
        });
        return id;
    }

    private UUID seedTask(UUID tenant, String title) {
        UUID id = UUID.randomUUID();
        tx(tenant, () -> jdbc.update("""
                INSERT INTO crm_tasks (id, tenant_id, title, status, created_by, updated_by, created_at, updated_at)
                VALUES (:id, :t, :title, 'OPEN', :u, :u, NOW(), NOW())
                """, p("id", id).addValue("t", tenant).addValue("title", title).addValue("u", USER_A)));
        return id;
    }

    private void ensureTenant(UUID id, String name) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :sub, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, p("id", id).addValue("name", name).addValue("sub", "c3-" + id));
    }

    private void ensureUser(UUID id, UUID tenant) {
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :t, :email, :name, 'ACTIVE', 'dummy', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, p("id", id).addValue("t", tenant)
                .addValue("email", "c3-" + id + "@snad.test")
                .addValue("name", "C3 User " + id.toString().substring(0, 8)));
    }

    private MapSqlParameterSource p(String k, Object v) {
        return new MapSqlParameterSource().addValue(k, v);
    }
}
