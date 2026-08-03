package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.party.domain.ContactRepository.CreateContactCommand;
import com.sanad.platform.crm.party.domain.ContactRepository.UpdateContactCommand;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcContactRepository} (TD-003-S2).
 *
 * <p>Covers create round-trip (display-name derivation, email normalization), update with
 * optimistic-concurrency enforcement, archive/restore lifecycle, and the not-found error path.
 * Contacts are seeded with {@code account_id = null} to avoid the legacy-relationship backfill.
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
class JdbcContactRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcContactRepository contacts;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        contacts = new JdbcContactRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    @Test
    void create_derivesDisplayNameAndNormalizesEmail() {
        ContactRecord saved = inTransaction(() -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe",
                        "Jane.Doe@Example.com", null, "ar-SA", null, actorId, null)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.lifecycleStatus()).isEqualTo("ACTIVE");
        // display name derived as "given family"
        assertThat(saved.displayName()).isEqualTo("Jane Doe");
        assertThat(saved.normalizedEmail()).isEqualTo("jane.doe@example.com");
        assertThat(saved.consentSummary()).isEqualTo("UNKNOWN"); // default
    }

    @Test
    void update_bumpsVersionAndAppliesChanges() {
        ContactRecord created = inTransaction(() -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe",
                        null, null, null, null, actorId, null)));

        ContactRecord updated = inTransaction(() -> contacts.update(tenantId, actorId,
                created.id(), new UpdateContactCommand(null, "Janet", "Doe",
                        null, null, null, null, actorId, null), 0));

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.givenName()).isEqualTo("Janet");
    }

    @Test
    void update_withStaleVersionThrowsConcurrencyConflict() {
        ContactRecord created = inTransaction(() -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe", null, null, null, null, actorId, null)));
        inTransaction(() -> contacts.update(tenantId, actorId, created.id(),
                new UpdateContactCommand(null, "v1", null, null, null, null, null, actorId, null), 0));

        assertThatThrownBy(() -> inTransaction(() ->
                contacts.update(tenantId, actorId, created.id(),
                        new UpdateContactCommand(null, "stale", null, null, null, null, null, actorId, null), 0)))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));
    }

    @Test
    void archive_thenRestoreTogglesLifecycleStatus() {
        ContactRecord created = inTransaction(() -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe", null, null, null, null, actorId, null)));

        ContactRecord archived = inTransaction(() ->
                contacts.archive(tenantId, actorId, created.id(), 0));
        assertThat(archived.lifecycleStatus()).isEqualTo("ARCHIVED");

        ContactRecord restored = inTransaction(() ->
                contacts.restore(tenantId, actorId, created.id(), archived.version()));
        assertThat(restored.lifecycleStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void findById_whenMissingThrowsNotFound() {
        assertThatThrownBy(() -> contacts.findById(tenantId, UUID.randomUUID()))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONTACT_NOT_FOUND));
    }
}
