package com.sanad.platform.crm.tag.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.tag.domain.TagRepository.CreateTagCommand;
import com.sanad.platform.crm.tag.domain.TagRepository.TagAssignmentRecord;
import com.sanad.platform.crm.tag.domain.TagRepository.TagRecord;
import com.sanad.platform.crm.tag.domain.TagRepository.UpdateTagCommand;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcTagRepository} (TD-003-S2).
 *
 * <p>Covers tag CRUD, name trimming, optimistic-concurrency conflict on update, assignment
 * idempotency (the {@code uk_crm_tag_assignments} unique constraint), and cascade delete of
 * assignments when a tag is removed.
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
class JdbcTagRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcTagRepository tags;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        tags = new JdbcTagRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    @Test
    void create_persistsAndTrimsName() {
        TagRecord saved = inTransaction(() -> tags.create(tenantId, actorId,
                new CreateTagCommand("  VIP  ", "#gold")));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.name()).isEqualTo("VIP");
        assertThat(saved.color()).isEqualTo("#gold");

        assertThat(tags.findById(tenantId, saved.id())).isEqualTo(saved);
    }

    @Test
    void findAll_supportsSearchFilter() {
        inTransaction(() -> tags.create(tenantId, actorId, new CreateTagCommand("VIP", null)));
        inTransaction(() -> tags.create(tenantId, actorId, new CreateTagCommand("Cold Lead", null)));

        var filtered = tags.findAll(tenantId, 50, "vip");
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).name()).isEqualTo("VIP");

        assertThat(tags.findAll(tenantId, 50, null)).hasSize(2);
    }

    @Test
    void update_bumpsVersionAndAppliesChanges() {
        TagRecord created = inTransaction(() -> tags.create(tenantId, actorId,
                new CreateTagCommand("VIP", "#gold")));

        TagRecord updated = inTransaction(() -> tags.update(tenantId, actorId, created.id(),
                new UpdateTagCommand("Platinum", "#silver"), 0));

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.name()).isEqualTo("Platinum");
        assertThat(updated.color()).isEqualTo("#silver");
    }

    @Test
    void update_withStaleVersionThrowsConcurrencyConflict() {
        TagRecord created = inTransaction(() -> tags.create(tenantId, actorId,
                new CreateTagCommand("VIP", null)));
        inTransaction(() -> tags.update(tenantId, actorId, created.id(),
                new UpdateTagCommand("Platinum", null), 0)); // v1

        assertThatThrownBy(() -> inTransaction(() ->
                tags.update(tenantId, actorId, created.id(),
                        new UpdateTagCommand("Bronze", null), 0)))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));
    }

    @Test
    void assign_isIdempotentForSameTagAndSubject() {
        UUID tagId = inTransaction(() -> tags.create(tenantId, actorId,
                new CreateTagCommand("VIP", null))).id();
        UUID subjectId = UUID.randomUUID();

        TagAssignmentRecord first = inTransaction(() ->
                tags.assign(tenantId, actorId, tagId, "ACCOUNT", subjectId));
        TagAssignmentRecord second = inTransaction(() ->
                tags.assign(tenantId, actorId, tagId, "ACCOUNT", subjectId));

        // the unique constraint (tenant, tag, subject_type, subject_id) makes the 2nd a no-op
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(tags.findAssignmentsBySubject(tenantId, "ACCOUNT", subjectId)).hasSize(1);
    }

    @Test
    void unassign_isIdempotentAndDoesNotFailWhenAbsent() {
        UUID tagId = inTransaction(() -> tags.create(tenantId, actorId,
                new CreateTagCommand("VIP", null))).id();
        UUID subjectId = UUID.randomUUID();

        inTransaction(() -> tags.assign(tenantId, actorId, tagId, "ACCOUNT", subjectId));
        inTransaction(() -> tags.unassign(tenantId, actorId, tagId, "ACCOUNT", subjectId));
        // second unassign of a now-absent assignment must not throw
        inTransaction(() -> tags.unassign(tenantId, actorId, tagId, "ACCOUNT", subjectId));

        assertThat(tags.findAssignmentsBySubject(tenantId, "ACCOUNT", subjectId)).isEmpty();
    }

    @Test
    void delete_removesTagAndItsAssignments() {
        UUID tagId = inTransaction(() -> tags.create(tenantId, actorId,
                new CreateTagCommand("VIP", null))).id();
        UUID subjectId = UUID.randomUUID();
        inTransaction(() -> tags.assign(tenantId, actorId, tagId, "ACCOUNT", subjectId));

        inTransaction(() -> tags.delete(tenantId, actorId, tagId));

        assertThatThrownBy(() -> tags.findById(tenantId, tagId))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_TAG_NOT_FOUND));
        assertThat(tags.findAssignmentsByTag(tenantId, tagId, 50)).isEmpty();
    }

    @Test
    void delete_whenMissingThrowsNotFound() {
        assertThatThrownBy(() -> inTransaction(() ->
                tags.delete(tenantId, actorId, UUID.randomUUID())))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_TAG_NOT_FOUND));
    }
}
