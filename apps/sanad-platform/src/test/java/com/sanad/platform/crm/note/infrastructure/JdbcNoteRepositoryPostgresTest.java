package com.sanad.platform.crm.note.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.note.domain.NoteRepository.CreateNoteCommand;
import com.sanad.platform.crm.note.domain.NoteRepository.NoteRecord;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcNoteRepository} (TD-003-S2).
 *
 * <p>Covers create round-trip, list-by-subject including/excluding archived, archive lifecycle,
 * and the concurrency-conflict + already-archived error paths. Exercises the real SQL against
 * a fresh {@code postgres:16-alpine} instance with the full Flyway-migrated CRM schema.
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
class JdbcNoteRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcNoteRepository notes;
    private UUID tenantId;
    private UUID actorId;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        notes = new JdbcNoteRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
        subjectId = UUID.randomUUID(); // subject_id has no FK; any UUID works
    }

    @Test
    void create_persistsNoteAndRoundTripsViaFindById() {
        NoteRecord saved = inTransaction(() -> notes.create(tenantId, actorId,
                new CreateNoteCommand("ACCOUNT", subjectId, "Initial contact call", actorId)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.subjectType()).isEqualTo("ACCOUNT");
        assertThat(saved.body()).isEqualTo("Initial contact call");
        assertThat(saved.archived()).isFalse();
        assertThat(saved.authorUserId()).isEqualTo(actorId);

        NoteRecord fetched = notes.findById(tenantId, saved.id());
        assertThat(fetched).isEqualTo(saved);
    }

    @Test
    void create_defaultsAuthorToActorWhenAuthorNull() {
        NoteRecord saved = inTransaction(() -> notes.create(tenantId, actorId,
                new CreateNoteCommand("LEAD", subjectId, "no explicit author", null)));

        assertThat(saved.authorUserId()).isEqualTo(actorId);
    }

    @Test
    void findAllBySubject_excludesArchivedByDefault() {
        UUID keep = inTransaction(() -> notes.create(tenantId, actorId,
                new CreateNoteCommand("ACCOUNT", subjectId, "keep", null))).id();
        UUID toArchive = inTransaction(() -> notes.create(tenantId, actorId,
                new CreateNoteCommand("ACCOUNT", subjectId, "archive me", null))).id();

        inTransaction(() -> notes.archive(tenantId, actorId, toArchive, 0));

        var activeOnly = notes.findAllBySubject(tenantId, "ACCOUNT", subjectId, 50, false);
        assertThat(activeOnly).hasSize(1);
        assertThat(activeOnly.get(0).id()).isEqualTo(keep);

        var includingArchived = notes.findAllBySubject(tenantId, "ACCOUNT", subjectId, 50, true);
        assertThat(includingArchived).hasSize(2);
    }

    @Test
    void archive_setsArchivedTrueAndBumpsVersion() {
        NoteRecord created = inTransaction(() -> notes.create(tenantId, actorId,
                new CreateNoteCommand("CONTACT", subjectId, "to be archived", null)));

        NoteRecord archived = inTransaction(() -> notes.archive(tenantId, actorId, created.id(), 0));

        assertThat(archived.archived()).isTrue();
        assertThat(archived.version()).isEqualTo(1);
        assertThat(notes.findById(tenantId, created.id()).archived()).isTrue();
    }

    @Test
    void archive_withStaleVersionThrowsConcurrencyConflict() {
        NoteRecord created = inTransaction(() -> notes.create(tenantId, actorId,
                new CreateNoteCommand("ACCOUNT", subjectId, "v0", null)));
        inTransaction(() -> notes.archive(tenantId, actorId, created.id(), 0)); // now v1

        // attempting to archive again using the stale expectedVersion=0 must fail
        assertThatThrownBy(() -> inTransaction(() ->
                notes.archive(tenantId, actorId, created.id(), 0)))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));
    }

    @Test
    void archive_whenAlreadyArchivedThrowsAlreadyArchived() {
        NoteRecord created = inTransaction(() -> notes.create(tenantId, actorId,
                new CreateNoteCommand("ACCOUNT", subjectId, "once", null)));
        inTransaction(() -> notes.archive(tenantId, actorId, created.id(), 0)); // archived, v1

        assertThatThrownBy(() -> inTransaction(() ->
                notes.archive(tenantId, actorId, created.id(), 1)))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_NOTE_ALREADY_ARCHIVED));
    }

    @Test
    void findById_whenMissingThrowsNotFound() {
        assertThatThrownBy(() -> notes.findById(tenantId, UUID.randomUUID()))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_NOTE_NOT_FOUND));
    }
}
