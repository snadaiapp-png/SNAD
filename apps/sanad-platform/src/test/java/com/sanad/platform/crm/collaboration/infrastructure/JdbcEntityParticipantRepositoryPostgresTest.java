package com.sanad.platform.crm.collaboration.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.collaboration.domain.*;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcEntityParticipantRepositoryPostgresTest {

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcEntityParticipantRepository repository;

    private static final UUID TENANT_A = UUID.fromString("d1000000-0000-4000-8000-00000000a001");
    private static final UUID TENANT_B = UUID.fromString("d1000000-0000-4000-8000-00000000b001");
    private static final UUID USER_A = UUID.fromString("d1000000-0000-4000-8000-00000000a002");

    @BeforeAll
    static void setup() {
        boolean ok;
        try { ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("JdbcEntityParticipantRepositoryPostgresTest"); }
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
        repository = new JdbcEntityParticipantRepository(jdbc);
    }

    @BeforeEach
    void seed() {
        for (UUID t : new UUID[]{TENANT_A, TENANT_B}) {
            transactions.executeWithoutResult(s -> {
                setGuc(t);
                jdbc.update("DELETE FROM crm_entity_participants WHERE tenant_id = :t", p("t", t));
                jdbc.update("DELETE FROM crm_timeline_events WHERE tenant_id = :t", p("t", t));
                jdbc.update("DELETE FROM crm_tasks WHERE tenant_id = :t", p("t", t));
                jdbc.update("DELETE FROM crm_cases WHERE tenant_id = :t", p("t", t));
                jdbc.update("DELETE FROM crm_contacts WHERE tenant_id = :t", p("t", t));
                jdbc.update("DELETE FROM crm_accounts WHERE tenant_id = :t", p("t", t));
            });
        }
        var ts = new MapSqlParameterSource().addValue("a", TENANT_A).addValue("b", TENANT_B);
        jdbc.update("DELETE FROM users WHERE tenant_id IN (:a,:b)", ts);
        jdbc.update("DELETE FROM tenants WHERE id IN (:a,:b)", ts);
        ensureTenant(TENANT_A); ensureTenant(TENANT_B); ensureUser(USER_A, TENANT_A);
    }

    @Test void insertFindAndListReturnOnlyCurrentTenant() {
        UUID c = seedContact(TENANT_A, "Alice"); UUID pid = UUID.randomUUID();
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); repository.insert(EntityParticipant.active(pid, TENANT_A, CollaborationEntityType.CONTACT, c, USER_A, ParticipantRole.COLLABORATOR, USER_A, Instant.now())); });
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); assertThat(repository.findById(TENANT_A, pid)).isPresent(); assertThat(repository.listActive(TENANT_A, CollaborationEntityType.CONTACT, c)).hasSize(1); });
        transactions.executeWithoutResult(s -> { setGuc(TENANT_B); assertThat(repository.findById(TENANT_A, pid)).isEmpty(); });
    }

    @Test void duplicateActiveRelationIsRejected() {
        UUID c = seedContact(TENANT_A, "Bob"); UUID id1 = UUID.randomUUID(); UUID id2 = UUID.randomUUID();
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); repository.insert(EntityParticipant.active(id1, TENANT_A, CollaborationEntityType.CONTACT, c, USER_A, ParticipantRole.WATCHER, USER_A, Instant.now())); });
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> { setGuc(TENANT_A); repository.insert(EntityParticipant.active(id2, TENANT_A, CollaborationEntityType.CONTACT, c, USER_A, ParticipantRole.WATCHER, USER_A, Instant.now().plusSeconds(1))); })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void removedRelationCanBeAddedAgainAsNewHistoryRow() {
        UUID c = seedContact(TENANT_A, "Carol"); UUID id1 = UUID.randomUUID(); UUID id2 = UUID.randomUUID();
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); repository.insert(EntityParticipant.active(id1, TENANT_A, CollaborationEntityType.CONTACT, c, USER_A, ParticipantRole.WATCHER, USER_A, Instant.now())); assertThat(repository.markRemoved(TENANT_A, id1, 0L, USER_A, Instant.now())).isTrue(); });
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); var h = repository.findById(TENANT_A, id1); assertThat(h).isPresent(); assertThat(h.get().isActive()).isFalse(); assertThat(h.get().version()).isEqualTo(1L); });
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); repository.insert(EntityParticipant.active(id2, TENANT_A, CollaborationEntityType.CONTACT, c, USER_A, ParticipantRole.WATCHER, USER_A, Instant.now().plusSeconds(60))); });
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); var a = repository.findActive(TENANT_A, CollaborationEntityType.CONTACT, c, USER_A, ParticipantRole.WATCHER); assertThat(a).isPresent(); assertThat(a.get().id()).isEqualTo(id2); });
    }

    @Test void staleVersionCannotRemoveRelation() {
        UUID c = seedContact(TENANT_A, "Dave"); UUID id = UUID.randomUUID();
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); repository.insert(EntityParticipant.active(id, TENANT_A, CollaborationEntityType.CONTACT, c, USER_A, ParticipantRole.COLLABORATOR, USER_A, Instant.now())); assertThat(repository.markRemoved(TENANT_A, id, 7L, USER_A, Instant.now())).isFalse(); });
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); var p = repository.findById(TENANT_A, id); assertThat(p).isPresent(); assertThat(p.get().isActive()).isTrue(); assertThat(p.get().version()).isEqualTo(0L); });
    }

    @Test void findActiveIsRoleSpecific() {
        // W2 (V20260823_2) prohibits more than one ACTIVE participant per
        // (tenant, contact, user) regardless of role. The original fixture
        // inserted two ACTIVE rows for the same user with different roles
        // (COLLABORATOR + WATCHER) which V20260823_2 now correctly rejects
        // with `uk_crm_contact_participant_active_user` DuplicateKeyException.
        //
        // The test's intent is to prove `findActive(...)` is role-specific —
        // i.e., querying for COLLABORATOR returns the COLLABORATOR row and
        // querying for WATCHER returns the WATCHER row. Using two DIFFERENT
        // users preserves this proof while complying with W2 (the partial
        // unique index keys on (tenant_id, entity_id, user_id) so different
        // users are independent). This mirrors the W2-compliant pattern
        // established by ContactParticipantInvariantPostgresTest
        // .c_twoDifferentUsersMayParticipateInSameContact.
        UUID c = seedContact(TENANT_A, "Eve");
        UUID userCollaborator = USER_A;
        UUID userWatcher = UUID.randomUUID(); ensureUser(userWatcher, TENANT_A);
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A);
            repository.insert(EntityParticipant.active(UUID.randomUUID(), TENANT_A, CollaborationEntityType.CONTACT, c, userCollaborator, ParticipantRole.COLLABORATOR, USER_A, Instant.now()));
            repository.insert(EntityParticipant.active(UUID.randomUUID(), TENANT_A, CollaborationEntityType.CONTACT, c, userWatcher, ParticipantRole.WATCHER, USER_A, Instant.now().plusSeconds(1)));
            assertThat(repository.findActive(TENANT_A, CollaborationEntityType.CONTACT, c, userCollaborator, ParticipantRole.COLLABORATOR)).isPresent();
            assertThat(repository.findActive(TENANT_A, CollaborationEntityType.CONTACT, c, userWatcher, ParticipantRole.WATCHER)).isPresent();
        });
    }

    @Test void listActiveExcludesRemovedHistory() {
        // W2 (V20260823_2) prohibits two ACTIVE participants for the same
        // (tenant, contact, user) regardless of role. The original fixture
        // inserted two ACTIVE rows for USER_A with different roles, then
        // marked one removed — but the second INSERT was already blocked
        // by `uk_crm_contact_participant_active_user` before `markRemoved`
        // could run.
        //
        // The test's intent is to prove `listActive(...)` excludes
        // removed/historical rows while `findById(...)` still returns them.
        // Using two DIFFERENT users (one COLLABORATOR, one WATCHER) — then
        // marking the COLLABORATOR as removed — preserves the proof: after
        // removal, `listActive` returns only the still-active WATCHER row
        // (hasSize=1, id=id2), and `findById(id1)` still returns the
        // historical COLLABORATOR row. This mirrors the W2-compliant pattern
        // established by ContactParticipantInvariantPostgresTest
        // .d_removedHistoricalThenNewActiveRoleIsAllowed (which uses one
        // user with role transition) — here we use two users so both
        // participants can be ACTIVE simultaneously before the removal,
        // matching the test's original assertion shape.
        UUID c = seedContact(TENANT_A, "Frank"); UUID id1 = UUID.randomUUID(); UUID id2 = UUID.randomUUID();
        UUID userCollaborator = USER_A;
        UUID userWatcher = UUID.randomUUID(); ensureUser(userWatcher, TENANT_A);
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A);
            repository.insert(EntityParticipant.active(id1, TENANT_A, CollaborationEntityType.CONTACT, c, userCollaborator, ParticipantRole.COLLABORATOR, USER_A, Instant.now()));
            repository.insert(EntityParticipant.active(id2, TENANT_A, CollaborationEntityType.CONTACT, c, userWatcher, ParticipantRole.WATCHER, USER_A, Instant.now().plusSeconds(1)));
            repository.markRemoved(TENANT_A, id1, 0L, USER_A, Instant.now());
            var list = repository.listActive(TENANT_A, CollaborationEntityType.CONTACT, c);
            assertThat(list).hasSize(1); assertThat(list.get(0).id()).isEqualTo(id2);
        });
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); assertThat(repository.findById(TENANT_A, id1)).isPresent(); });
    }

    @Test void listActiveOrderingIsStable() {
        UUID c = seedContact(TENANT_A, "Grace"); UUID u1 = UUID.randomUUID(); ensureUser(u1, TENANT_A); UUID u2 = UUID.randomUUID(); ensureUser(u2, TENANT_A); UUID u3 = UUID.randomUUID(); ensureUser(u3, TENANT_A);
        Instant bt = Instant.parse("2026-08-21T10:00:00Z");
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001"); UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002"); UUID id3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A);
            repository.insert(EntityParticipant.active(id2, TENANT_A, CollaborationEntityType.CONTACT, c, u2, ParticipantRole.WATCHER, USER_A, bt));
            repository.insert(EntityParticipant.active(id1, TENANT_A, CollaborationEntityType.CONTACT, c, u1, ParticipantRole.COLLABORATOR, USER_A, bt));
            repository.insert(EntityParticipant.active(id3, TENANT_A, CollaborationEntityType.CONTACT, c, u3, ParticipantRole.WATCHER, USER_A, bt.plusSeconds(1)));
            var list = repository.listActive(TENANT_A, CollaborationEntityType.CONTACT, c);
            assertThat(list).hasSize(3); assertThat(list.get(0).id()).isEqualTo(id1); assertThat(list.get(1).id()).isEqualTo(id2); assertThat(list.get(2).id()).isEqualTo(id3);
        });
    }

    @Test void missingTenantContextFailsClosed() {
        UUID c = seedContact(TENANT_A, "Heidi"); UUID pid = UUID.randomUUID();
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> { repository.insert(EntityParticipant.active(pid, TENANT_A, CollaborationEntityType.CONTACT, c, USER_A, ParticipantRole.COLLABORATOR, USER_A, Instant.now())); })).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void negativeExpectedVersionIsRejectedWithoutUpdate() {
        UUID c = seedContact(TENANT_A, "Ivan"); UUID id = UUID.randomUUID();
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); repository.insert(EntityParticipant.active(id, TENANT_A, CollaborationEntityType.CONTACT, c, USER_A, ParticipantRole.COLLABORATOR, USER_A, Instant.now())); });
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> { setGuc(TENANT_A); repository.markRemoved(TENANT_A, id, -1L, USER_A, Instant.now()); })).isInstanceOf(IllegalArgumentException.class);
        transactions.executeWithoutResult(s -> { setGuc(TENANT_A); var p = repository.findById(TENANT_A, id); assertThat(p).isPresent(); assertThat(p.get().isActive()).isTrue(); assertThat(p.get().version()).isEqualTo(0L); });
    }

    // ========== HELPERS ==========
    private void setGuc(UUID t) { jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)", p("t", t.toString()), String.class); }
    private MapSqlParameterSource p(String k, Object v) { return new MapSqlParameterSource().addValue(k, v); }
    private UUID seedContact(UUID t, String n) { UUID id = UUID.randomUUID(); transactions.executeWithoutResult(s -> { setGuc(t); jdbc.update("INSERT INTO crm_contacts (id, tenant_id, given_name, display_name, normalized_name, lifecycle_status, created_by, updated_by, created_at, updated_at) VALUES (:id,:t,:n,:n,:norm,'ACTIVE',:u,:u,NOW(),NOW())", p("id",id).addValue("t",t).addValue("n",n).addValue("norm",n.toLowerCase()).addValue("u",USER_A)); }); return id; }
    private void ensureTenant(UUID id) { jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (:id,:name,:sub,'ACTIVE',NOW(),NOW()) ON CONFLICT (id) DO NOTHING", p("id",id).addValue("name","Test "+id).addValue("sub","repo-"+id)); }
    private void ensureUser(UUID id, UUID t) { jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (:id,:t,:email,:name,'ACTIVE','dummy',NOW(),NOW()) ON CONFLICT (id) DO NOTHING", p("id",id).addValue("t",t).addValue("email","repo-"+id+"@snad.test").addValue("name","Repo User")); }
}
