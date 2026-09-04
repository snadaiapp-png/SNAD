package com.sanad.platform.crm.collaboration.infrastructure;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 3C — Concurrent participant referential integrity")
class CrmEntityParticipantConcurrencyIntegrityPostgresTest {

    private static String jdbcUrl, jdbcUser, jdbcPass;
    private static final UUID TENANT = UUID.fromString("b1000000-0000-4000-8000-00000000c001");
    private static final UUID USER_ID = UUID.fromString("b1000000-0000-4000-8000-00000000c002");

    @BeforeAll
    static void setup() {
        boolean ok;
        try { ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("CrmEntityParticipantConcurrencyIntegrityPostgresTest"); }
        catch (Throwable ignored) { ok = false; }
        Assumptions.assumeTrue(ok, "PostgreSQL Direct required");
        jdbcUrl = System.getenv().getOrDefault("SPRING_DATASOURCE_URL","jdbc:postgresql://localhost:5432/sanad");
        jdbcUser = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME","sanad");
        jdbcPass = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD","");
        Flyway.configure().dataSource(jdbcUrl, jdbcUser, jdbcPass)
                .locations("classpath:db/migration","classpath:db/vendor/postgresql")
                .cleanDisabled(false).validateOnMigrate(true).load().migrate();
    }

    @BeforeEach void seed() throws Exception {
        try (Connection c = openConnection()) {
            c.setAutoCommit(false); setGuc(c, TENANT); cleanup(c);
            ensureTenant(c, TENANT); ensureUser(c, USER_ID, TENANT); c.commit();
        }
    }

    @Test void validationFunctionUsesForKeyShareOnContact() throws Exception {
        String def = queryFunctionDef();
        assertThat(def).contains("FOR KEY SHARE").contains("crm_contacts");
    }

    @Test void validationFunctionUsesForKeyShareOnTask() throws Exception {
        String def = queryFunctionDef();
        assertThat(def).contains("FOR KEY SHARE").contains("crm_tasks");
    }

    @Test void validationFunctionUsesForKeyShareOnCase() throws Exception {
        String def = queryFunctionDef();
        assertThat(def).contains("FOR KEY SHARE").contains("crm_cases");
    }

    @Test void insertWinsThenConcurrentDeleteIsRejected() throws Exception {
        UUID contactId = seedContact();
        try (Connection connA = openConnection()) {
            connA.setAutoCommit(false); setGuc(connA, TENANT);
            insertParticipant(connA, UUID.randomUUID(), TENANT, "CONTACT", contactId, USER_ID);
            ExecutorService exec = Executors.newSingleThreadExecutor();
            Future<Boolean> delFuture = exec.submit(() -> { Connection cb = null; try { cb = openConnection(); cb.setAutoCommit(false); setGuc(cb, TENANT); try (Statement s = cb.createStatement()) { s.execute("SET LOCAL lock_timeout = '3s'"); } int d = cb.createStatement().executeUpdate("DELETE FROM crm_contacts WHERE id = '" + contactId + "'"); cb.commit(); return d > 0; } catch (SQLException e) { if (cb != null) try { cb.rollback(); } catch (Exception ignored) {} return false; } finally { if (cb != null) try { cb.close(); } catch (Exception ignored) {} } });
            Thread.sleep(500); connA.commit();
            Boolean delSucceeded = delFuture.get(10, TimeUnit.SECONDS); exec.shutdown();
            assertThat(delSucceeded).as("DELETE must be rejected after participant INSERT commits").isFalse();
            try (Connection v = openConnection()) { v.setAutoCommit(false); setGuc(v, TENANT);
                assertThat(countRows(v, "crm_contacts", contactId)).as("contact must still exist").isEqualTo(1);
                assertThat(countParticipants(v, "CONTACT", contactId)).as("participant must still exist").isEqualTo(1);
                v.commit();
            }
        }
    }

    @Test void deleteWinsThenConcurrentParticipantInsertIsRejected() throws Exception {
        UUID contactId = seedContact();
        try (Connection connA = openConnection()) {
            connA.setAutoCommit(false); setGuc(connA, TENANT);
            connA.createStatement().executeUpdate("DELETE FROM crm_contacts WHERE id = '" + contactId + "'");
            ExecutorService exec = Executors.newSingleThreadExecutor();
            Future<Boolean> insFuture = exec.submit(() -> { Connection cb = null; try { cb = openConnection(); cb.setAutoCommit(false); setGuc(cb, TENANT); try (Statement s = cb.createStatement()) { s.execute("SET LOCAL lock_timeout = '3s'"); } insertParticipant(cb, UUID.randomUUID(), TENANT, "CONTACT", contactId, USER_ID); cb.commit(); return true; } catch (SQLException e) { if (cb != null) try { cb.rollback(); } catch (Exception ignored) {} return false; } finally { if (cb != null) try { cb.close(); } catch (Exception ignored) {} } });
            Thread.sleep(500); connA.commit();
            Boolean insSucceeded = insFuture.get(10, TimeUnit.SECONDS); exec.shutdown();
            assertThat(insSucceeded).as("participant INSERT must be rejected after entity DELETE commits").isFalse();
            try (Connection v = openConnection()) { v.setAutoCommit(false); setGuc(v, TENANT);
                assertThat(countRows(v, "crm_contacts", contactId)).as("contact must be deleted").isEqualTo(0);
                assertThat(countParticipants(v, "CONTACT", contactId)).as("participant must not exist").isEqualTo(0);
                v.commit();
            }
        }
    }

    @Test void validationFunctionIsNotSecurityDefiner() throws Exception {
        try (Connection c = openConnection()) {
            var rs = c.createStatement().executeQuery("SELECT prosecdef FROM pg_proc WHERE proname = 'crm_validate_entity_participant_reference'");
            assertThat(rs.next()).isTrue(); assertThat(rs.getBoolean("prosecdef")).isFalse();
        }
    }

    // HELPERS
    private Connection openConnection() throws SQLException { return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPass); }
    private void setGuc(Connection c, UUID t) throws SQLException { try (var ps = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) { ps.setString(1, t.toString()); ps.executeQuery(); } }
    private void insertParticipant(Connection c, UUID id, UUID tenant, String type, UUID entity, UUID user) throws SQLException { try (var ps = c.prepareStatement("INSERT INTO crm_entity_participants (id,tenant_id,version,entity_type,entity_id,user_id,role,added_at,added_by) VALUES (?,?,?,?,?,?,'COLLABORATOR',NOW(),?)")) { ps.setObject(1, id); ps.setObject(2, tenant); ps.setLong(3, 0); ps.setString(4, type); ps.setObject(5, entity); ps.setObject(6, user); ps.setObject(7, user); ps.executeUpdate(); } }
    private UUID seedContact() throws Exception { try (Connection c = openConnection()) { c.setAutoCommit(false); setGuc(c, TENANT); UUID id = UUID.randomUUID(); try (var ps = c.prepareStatement("INSERT INTO crm_contacts (id,tenant_id,given_name,display_name,normalized_name,lifecycle_status,created_by,updated_by,created_at,updated_at) VALUES (?,?,?,'Test','test','ACTIVE',?,?,NOW(),NOW())")) { ps.setObject(1, id); ps.setObject(2, TENANT); ps.setString(3, "Test"); ps.setObject(4, USER_ID); ps.setObject(5, USER_ID); ps.executeUpdate(); } c.commit(); return id; } }
    private int countRows(Connection c, String table, UUID id) throws SQLException { setGuc(c, TENANT); try (var ps = c.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE id = ?")) { ps.setObject(1, id); var rs = ps.executeQuery(); return rs.next() ? rs.getInt(1) : 0; } }
    private int countParticipants(Connection c, String type, UUID entity) throws SQLException { setGuc(c, TENANT); try (var ps = c.prepareStatement("SELECT COUNT(*) FROM crm_entity_participants WHERE entity_type = ? AND entity_id = ?")) { ps.setString(1, type); ps.setObject(2, entity); var rs = ps.executeQuery(); return rs.next() ? rs.getInt(1) : 0; } }
    private String queryFunctionDef() throws Exception { try (Connection c = openConnection()) { try (var ps = c.prepareStatement("SELECT pg_get_functiondef(oid) FROM pg_proc WHERE proname = 'crm_validate_entity_participant_reference'")) { var rs = ps.executeQuery(); return rs.next() ? rs.getString(1) : ""; } } }
    private void cleanup(Connection c) throws SQLException { for (String t : new String[]{"crm_entity_participants","crm_timeline_events","crm_contacts","crm_accounts"}) c.createStatement().executeUpdate("DELETE FROM " + t + " WHERE tenant_id = '" + TENANT + "'"); c.createStatement().executeUpdate("DELETE FROM users WHERE tenant_id = '" + TENANT + "'"); c.createStatement().executeUpdate("DELETE FROM tenants WHERE id = '" + TENANT + "'"); }
    private void ensureTenant(Connection c, UUID id) throws SQLException { c.createStatement().executeUpdate("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES ('" + id + "','Conc','conc-" + id + "','ACTIVE',NOW(),NOW()) ON CONFLICT (id) DO NOTHING"); }
    private void ensureUser(Connection c, UUID id, UUID tenant) throws SQLException { c.createStatement().executeUpdate("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES ('" + id + "','" + tenant + "','conc-" + id + "@snad.test','Conc','ACTIVE','dummy',NOW(),NOW()) ON CONFLICT (id) DO NOTHING"); }
}
