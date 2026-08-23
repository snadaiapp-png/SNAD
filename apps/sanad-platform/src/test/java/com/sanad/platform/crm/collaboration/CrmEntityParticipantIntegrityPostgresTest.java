package com.sanad.platform.crm.collaboration;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Task 3A — Participant entity integrity (PostgreSQL Direct)")
class CrmEntityParticipantIntegrityPostgresTest {

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static final UUID TENANT_A = UUID.fromString("e1000000-0000-4000-8000-00000000a001");
    private static final UUID TENANT_B = UUID.fromString("e1000000-0000-4000-8000-00000000b001");
    private static final UUID USER_A = UUID.fromString("e1000000-0000-4000-8000-00000000a002");

    @BeforeAll
    static void setup() {
        boolean ok;
        try { ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("CrmEntityParticipantIntegrityPostgresTest"); }
        catch (Throwable ignored) { ok = false; }
        Assumptions.assumeTrue(ok, "PostgreSQL Direct required");
        Flyway.configure().dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL","jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME","sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD",""))
                .locations("classpath:db/migration","classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false).validateOnMigrate(true).load().migrate();
        var ds = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL","jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME","sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD",""));
        jdbc = new NamedParameterJdbcTemplate(ds);
        transactions = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
    }

    @BeforeEach void seed() {
        for (UUID t : new UUID[]{TENANT_A, TENANT_B}) {
            transactions.executeWithoutResult(s -> { setGuc(t); del(t, "crm_entity_participants"); del(t, "crm_timeline_events"); del(t, "crm_tasks"); del(t, "crm_cases"); del(t, "crm_contacts"); del(t, "crm_accounts"); });
        }
        var ts = new MapSqlParameterSource().addValue("a", TENANT_A).addValue("b", TENANT_B);
        jdbc.update("DELETE FROM users WHERE tenant_id IN (:a,:b)", ts);
        jdbc.update("DELETE FROM tenants WHERE id IN (:a,:b)", ts);
        ensureTenant(TENANT_A); ensureTenant(TENANT_B); ensureUser(USER_A, TENANT_A);
    }

    @Test void validContactParticipantSucceeds() { UUID c = seedContact(TENANT_A, "Alice"); tx(TENANT_A, () -> insert(TENANT_A, "CONTACT", c)); }
    @Test void nonexistentContactIsRejected() { assertThatThrownBy(() -> tx(TENANT_A, () -> insert(TENANT_A, "CONTACT", UUID.randomUUID()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
    @Test void crossTenantContactIsRejected() { UUID c = seedContact(TENANT_B, "Bob"); assertThatThrownBy(() -> tx(TENANT_A, () -> insert(TENANT_A, "CONTACT", c))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
    @Test void contactIdCannotBeReferencedAsTask() { UUID c = seedContact(TENANT_A, "Carol"); assertThatThrownBy(() -> tx(TENANT_A, () -> insert(TENANT_A, "TASK", c))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
    @Test void validTaskParticipantSucceeds() { UUID t = seedTask(TENANT_A, "Build"); tx(TENANT_A, () -> insert(TENANT_A, "TASK", t)); }
    @Test void nonexistentTaskIsRejected() { assertThatThrownBy(() -> tx(TENANT_A, () -> insert(TENANT_A, "TASK", UUID.randomUUID()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
    @Test void validCaseParticipantSucceeds() { UUID c = seedCase(TENANT_A, "Ticket"); tx(TENANT_A, () -> insert(TENANT_A, "CASE", c)); }
    @Test void nonexistentCaseIsRejected() { assertThatThrownBy(() -> tx(TENANT_A, () -> insert(TENANT_A, "CASE", UUID.randomUUID()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }

    @Test void participantReferenceCannotBeChangedToInvalidEntity() { UUID c = seedContact(TENANT_A, "Dave"); UUID pid = UUID.randomUUID(); tx(TENANT_A, () -> insert(pid, TENANT_A, "CONTACT", c)); assertThatThrownBy(() -> tx(TENANT_A, () -> jdbc.update("UPDATE crm_entity_participants SET entity_id = :n WHERE id = :id", p("n", UUID.randomUUID()).addValue("id", pid)))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
    @Test void participantReferenceCannotBeChangedCrossTenant() { UUID ca = seedContact(TENANT_A, "Eve"); UUID pid = UUID.randomUUID(); tx(TENANT_A, () -> insert(pid, TENANT_A, "CONTACT", ca)); UUID cb = seedContact(TENANT_B, "Frank"); assertThatThrownBy(() -> tx(TENANT_A, () -> jdbc.update("UPDATE crm_entity_participants SET entity_id = :n WHERE id = :id", p("n", cb).addValue("id", pid)))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }

    @Test void contactWithParticipantHistoryCannotBeHardDeleted() { UUID c = seedContact(TENANT_A, "Grace"); tx(TENANT_A, () -> insert(TENANT_A, "CONTACT", c)); assertThatThrownBy(() -> tx(TENANT_A, () -> jdbc.update("DELETE FROM crm_contacts WHERE id = :id", p("id", c)))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
    @Test void taskWithParticipantHistoryCannotBeHardDeleted() { UUID t = seedTask(TENANT_A, "Important"); tx(TENANT_A, () -> insert(TENANT_A, "TASK", t)); assertThatThrownBy(() -> tx(TENANT_A, () -> jdbc.update("DELETE FROM crm_tasks WHERE id = :id", p("id", t)))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
    @Test void caseWithParticipantHistoryCannotBeHardDeleted() { UUID c = seedCase(TENANT_A, "Case"); tx(TENANT_A, () -> insert(TENANT_A, "CASE", c)); assertThatThrownBy(() -> tx(TENANT_A, () -> jdbc.update("DELETE FROM crm_cases WHERE id = :id", p("id", c)))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }

    @Test void archivingContactWithParticipantHistoryIsAllowed() { UUID c = seedContact(TENANT_A, "Heidi"); tx(TENANT_A, () -> insert(TENANT_A, "CONTACT", c)); tx(TENANT_A, () -> assertThat(jdbc.update("UPDATE crm_contacts SET lifecycle_status = 'ARCHIVED' WHERE id = :id", p("id", c))).isEqualTo(1)); }
    @Test void completingTaskWithParticipantHistoryIsAllowed() { UUID t = seedTask(TENANT_A, "Task"); tx(TENANT_A, () -> insert(TENANT_A, "TASK", t)); tx(TENANT_A, () -> assertThat(jdbc.update("UPDATE crm_tasks SET status = 'COMPLETED' WHERE id = :id", p("id", t))).isEqualTo(1)); }
    @Test void closingCaseWithParticipantHistoryIsAllowed() { UUID c = seedCase(TENANT_A, "Case"); tx(TENANT_A, () -> insert(TENANT_A, "CASE", c)); tx(TENANT_A, () -> assertThat(jdbc.update("UPDATE crm_cases SET status = 'CLOSED' WHERE id = :id", p("id", c))).isEqualTo(1)); }

    @Test void hardDeleteWithoutTenantContextIsRejected() {
        UUID c = seedContact(TENANT_A, "Ivan");
        tx(TENANT_A, () -> insert(TENANT_A, "CONTACT", c));
        // Set the GUC to TENANT_A so the SELECT inside the DELETE WHERE
        // clause can find the row (FORCE RLS on crm_contacts after
        // V20260823_1 means a DELETE without GUC would silently affect
        // 0 rows), then clear the GUC inside the same transaction before
        // issuing the DELETE so the trg_crm_contacts_delete_guard trigger
        // fires its CRM_DELETE_GUARD_TENANT_CONTEXT_REQUIRED check.
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            // First lock + verify the row exists under TENANT_A's GUC.
            setGuc(TENANT_A);
            // Then clear the GUC for the DELETE itself so the trigger's
            // tenant-context guard fires.
            jdbc.queryForObject("SELECT set_config('app.tenant_id', NULL, true)", new MapSqlParameterSource(), String.class);
            jdbc.update("DELETE FROM crm_contacts WHERE id = :id", p("id", c));
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void validationFunctionIsNotSecurityDefiner() { assertThat(jdbc.queryForObject("SELECT prosecdef FROM pg_proc WHERE proname = 'crm_validate_entity_participant_reference'", new MapSqlParameterSource(), Boolean.class)).isFalse(); }
    @Test void runtimeRoleIsNotSuperuserOrBypassrls() { assertThat(jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user", new MapSqlParameterSource(), Boolean.class)).isFalse(); assertThat(jdbc.queryForObject("SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user", new MapSqlParameterSource(), Boolean.class)).isFalse(); }

    // HELPERS
    private void setGuc(UUID t) { jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)", p("t", t.toString()), String.class); }
    private void tx(UUID t, Runnable r) { transactions.executeWithoutResult(s -> { setGuc(t); r.run(); }); }
    private void insert(UUID tenant, String type, UUID entity) { insert(UUID.randomUUID(), tenant, type, entity); }
    private void insert(UUID id, UUID tenant, String type, UUID entity) { jdbc.update("INSERT INTO crm_entity_participants (id,tenant_id,version,entity_type,entity_id,user_id,role,added_at,added_by) VALUES (:id,:t,0,:type,:e,:u,'COLLABORATOR',NOW(),:u)", p("id",id).addValue("t",tenant).addValue("type",type).addValue("e",entity).addValue("u",USER_A)); }
    private void del(UUID t, String table) { jdbc.update("DELETE FROM " + table + " WHERE tenant_id = :t", p("t", t)); }
    private UUID seedContact(UUID t, String n) { UUID id = UUID.randomUUID(); tx(t, () -> jdbc.update("INSERT INTO crm_contacts (id,tenant_id,given_name,display_name,normalized_name,lifecycle_status,created_by,updated_by,created_at,updated_at) VALUES (:id,:t,:n,:n,:norm,'ACTIVE',:u,:u,NOW(),NOW())", p("id",id).addValue("t",t).addValue("n",n).addValue("norm",n.toLowerCase()).addValue("u",USER_A))); return id; }
    private UUID seedTask(UUID t, String title) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO crm_tasks (id,tenant_id,title,status,created_by,updated_by,created_at,updated_at) VALUES (:id,:t,:title,'OPEN',:u,:u,NOW(),NOW())", p("id",id).addValue("t",t).addValue("title",title).addValue("u",USER_A)); return id; }
    private UUID seedCase(UUID t, String subject) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO crm_cases (id,tenant_id,subject,status) VALUES (:id,:t,:subject,'OPEN')", p("id",id).addValue("t",t).addValue("subject",subject)); return id; }
    private void ensureTenant(UUID id) { jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (:id,:name,:sub,'ACTIVE',NOW(),NOW()) ON CONFLICT (id) DO NOTHING", p("id",id).addValue("name","Test "+id).addValue("sub","integ-"+id)); }
    private void ensureUser(UUID id, UUID t) { jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (:id,:t,:email,:name,'ACTIVE','dummy',NOW(),NOW()) ON CONFLICT (id) DO NOTHING", p("id",id).addValue("t",t).addValue("email","integ-"+id+"@snad.test").addValue("name","Test User")); }
    private MapSqlParameterSource p(String k, Object v) { return new MapSqlParameterSource().addValue(k, v); }
}
