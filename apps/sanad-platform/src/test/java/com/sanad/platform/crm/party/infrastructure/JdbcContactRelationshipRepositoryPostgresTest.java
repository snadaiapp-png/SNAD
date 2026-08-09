package com.sanad.platform.crm.party.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.CreateRelationshipCommand;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.CreateRelationshipRoleCommand;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipRoleRecord;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcContactRelationshipRepository}
 * (TD-003-S2).
 *
 * <p>Covers relationship create + list-by-contact, role catalog create + list, the
 * {@code CREATED} history row, and custom-role ({@code OTHER}) linkage. Uses a manually
 * constructed {@link ObjectMapper} (matching the production constructor signature).
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
class JdbcContactRelationshipRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcContactRelationshipRepository relationships;
    private UUID tenantId;
    private UUID actorId;
    private UUID accountId;
    private UUID contactId;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        relationships = new JdbcContactRelationshipRepository(jdbc(), mapper);
        tenantId = newTenant();
        actorId = UUID.randomUUID();
        // account + contact are required FK targets; contact seeded WITHOUT account_id so the
        // V20260717_1 legacy-relationship backfill does not pre-create a relationship row
        accountId = seedAccount(tenantId, actorId, "Rel Account", "rel-account");
        contactId = seedContact(tenantId, actorId, "Rel", "Rel Contact");
    }

    @Test
    void createRelationship_persistsAndEmitsCreatedHistory() {
        RelationshipRecord saved = inTransaction(() -> relationships.createRelationship(
                tenantId, actorId, contactId,
                new CreateRelationshipCommand(accountId, "DECISION_MAKER", null,
                        false, null, null, "CTO", "Engineering",
                        "DECIDER", actorId)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.status()).isEqualTo("ACTIVE");
        assertThat(saved.roleCode()).isEqualTo("DECISION_MAKER");
        assertThat(saved.accountId()).isEqualTo(accountId);

        // a CREATED history row should be present with a parseable JSON snapshot
        var history = relationships.relationshipHistory(tenantId, saved.id(), 10);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).eventType()).isEqualTo("CREATED");
        assertThat(history.get(0).snapshot()).isNotBlank();

        // list-by-contact returns the new relationship
        var byContact = relationships.listByContact(tenantId, contactId, 10, null, null);
        assertThat(byContact).hasSize(1);
        assertThat(byContact.get(0).id()).isEqualTo(saved.id());
    }

    @Test
    void createRelationship_forOtherRoleRequiresCustomRole() {
        // seed a custom role first
        RelationshipRoleRecord role = inTransaction(() -> relationships.createRole(tenantId, actorId,
                new CreateRelationshipRoleCommand("VIP_BUYER", "مشتري مميز", "VIP Buyer")));

        assertThat(role.code()).isEqualTo("VIP_BUYER");
        assertThat(role.active()).isTrue();

        RelationshipRecord saved = inTransaction(() -> relationships.createRelationship(
                tenantId, actorId, contactId,
                new CreateRelationshipCommand(accountId, "OTHER", role.id(),
                        false, null, null, null, null, "NONE", actorId)));

        assertThat(saved.roleCode()).isEqualTo("OTHER");
        assertThat(saved.customRoleId()).isEqualTo(role.id());
        assertThat(saved.customRoleNameEn()).isEqualTo("VIP Buyer");

        // role catalog list returns the seeded role
        var roles = relationships.listRoles(tenantId, false);
        assertThat(roles).extracting(RelationshipRoleRecord::code).contains("VIP_BUYER");
    }

    @Test
    void listByAccount_returnsRelationshipsForAccount() {
        inTransaction(() -> relationships.createRelationship(tenantId, actorId, contactId,
                new CreateRelationshipCommand(accountId, "BILLING", null,
                        false, null, null, null, null, "NONE", actorId)));

        var byAccount = relationships.listByAccount(tenantId, accountId, 10, null, null);
        assertThat(byAccount).hasSize(1);
        assertThat(byAccount.get(0).accountId()).isEqualTo(accountId);
    }

    @Test
    void findProfile_returnsContactProfile() {
        // the contact's profile row is created by V20260717_1 backfill; verify it is retrievable
        var profile = relationships.findProfile(tenantId, contactId);
        assertThat(profile).isNotNull();
        assertThat(profile.id()).isEqualTo(contactId);
    }
}
