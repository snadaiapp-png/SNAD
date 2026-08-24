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
        ContactRecord saved = inTenantTransaction(tenantId, () -> contacts.create(tenantId, actorId,
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
        ContactRecord created = inTenantTransaction(tenantId, () -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe",
                        null, null, null, null, actorId, null)));

        ContactRecord updated = inTenantTransaction(tenantId, () -> contacts.update(tenantId, actorId,
                created.id(), new UpdateContactCommand(null, "Janet", "Doe",
                        null, null, null, null, actorId, null), 0));

        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.givenName()).isEqualTo("Janet");
    }

    @Test
    void update_withStaleVersionThrowsConcurrencyConflict() {
        ContactRecord created = inTenantTransaction(tenantId, () -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe", null, null, null, null, actorId, null)));
        inTenantTransaction(tenantId, () -> contacts.update(tenantId, actorId, created.id(),
                new UpdateContactCommand(null, "v1", null, null, null, null, null, actorId, null), 0));

        assertThatThrownBy(() -> inTenantTransaction(tenantId, () ->
                contacts.update(tenantId, actorId, created.id(),
                        new UpdateContactCommand(null, "stale", null, null, null, null, null, actorId, null), 0)))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));
    }

    @Test
    void archive_thenRestoreTogglesLifecycleStatus() {
        ContactRecord created = inTenantTransaction(tenantId, () -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe", null, null, null, null, actorId, null)));

        ContactRecord archived = inTenantTransaction(tenantId, () ->
                contacts.archive(tenantId, actorId, created.id(), 0));
        assertThat(archived.lifecycleStatus()).isEqualTo("ARCHIVED");

        ContactRecord restored = inTenantTransaction(tenantId, () ->
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

    // ── C5 — canonical Contact transfer repository primitives ──────────────

    @Test
    void findByIdForUpdate_returnsTenantScopedContact() {
        ContactRecord created = inTenantTransaction(tenantId, () -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe", null, null, null, null, actorId, null)));

        ContactRecord locked = inTenantTransaction(tenantId, () ->
                contacts.findByIdForUpdate(tenantId, created.id()));
        assertThat(locked.id()).isEqualTo(created.id());
        assertThat(locked.ownerUserId()).isEqualTo(actorId);
    }

    @Test
    void findByIdForUpdate_whenMissingThrowsNotFound() {
        assertThatThrownBy(() -> inTenantTransaction(tenantId, () ->
                contacts.findByIdForUpdate(tenantId, UUID.randomUUID())))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONTACT_NOT_FOUND));
    }

    @Test
    void transferOwner_changesOnlyOwnerRelatedFieldsAndIncrementsVersionOnce() {
        UUID originalOwner = actorId;
        UUID newOwner = UUID.randomUUID();
        ContactRecord created = inTenantTransaction(tenantId, () -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe",
                        "jane@example.com", null, null, null, originalOwner, null)));

        ContactRecord transferred = inTenantTransaction(tenantId, () ->
                contacts.transferOwner(tenantId, actorId, created.id(), newOwner, created.version(),
                        java.time.Instant.parse("2026-08-23T12:00:00Z")));

        assertThat(transferred.ownerUserId())
                .as("transferOwner must change owner_user_id")
                .isEqualTo(newOwner);
        assertThat(transferred.version())
                .as("transferOwner must increment version exactly once")
                .isEqualTo(created.version() + 1);
        // No other Contact business fields may change
        assertThat(transferred.givenName()).isEqualTo(created.givenName());
        assertThat(transferred.familyName()).isEqualTo(created.familyName());
        assertThat(transferred.displayName()).isEqualTo(created.displayName());
        assertThat(transferred.primaryEmail()).isEqualTo(created.primaryEmail());
        assertThat(transferred.normalizedEmail()).isEqualTo(created.normalizedEmail());
        assertThat(transferred.lifecycleStatus()).isEqualTo(created.lifecycleStatus());
        assertThat(transferred.consentSummary()).isEqualTo(created.consentSummary());
    }

    @Test
    void transferOwner_withStaleVersionThrowsConcurrencyConflict() {
        UUID originalOwner = actorId;
        UUID newOwner = UUID.randomUUID();
        ContactRecord created = inTenantTransaction(tenantId, () -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe", null, null, null, null, originalOwner, null)));

        long staleVersion = created.version() + 999L;
        assertThatThrownBy(() -> inTenantTransaction(tenantId, () ->
                contacts.transferOwner(tenantId, actorId, created.id(), newOwner, staleVersion,
                        java.time.Instant.now())))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));
    }

    @Test
    void transferOwner_withStaleVersionLeavesOwnerUnchanged() {
        UUID originalOwner = actorId;
        UUID newOwner = UUID.randomUUID();
        ContactRecord created = inTenantTransaction(tenantId, () -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe", null, null, null, null, originalOwner, null)));

        long staleVersion = created.version() + 999L;
        assertThatThrownBy(() -> inTenantTransaction(tenantId, () ->
                contacts.transferOwner(tenantId, actorId, created.id(), newOwner, staleVersion,
                        java.time.Instant.now())))
                .isInstanceOf(CrmContractException.class);

        // Re-read after the failed transfer
        ContactRecord current = inTenantTransaction(tenantId, () -> contacts.findById(tenantId, created.id()));
        assertThat(current.ownerUserId())
                .as("Owner must be unchanged after stale-version rejection")
                .isEqualTo(originalOwner);
        assertThat(current.version())
                .as("Version must be unchanged after stale-version rejection")
                .isEqualTo(created.version());
    }

    @Test
    void transferOwner_withWrongTenantCannotMutateAnotherTenantContact() {
        UUID originalOwner = actorId;
        UUID newOwner = UUID.randomUUID();
        ContactRecord created = inTenantTransaction(tenantId, () -> contacts.create(tenantId, actorId,
                new CreateContactCommand(null, "Jane", "Doe", null, null, null, null, originalOwner, null)));

        UUID otherTenant = newTenant();
        // Under FORCE RLS the wrong-tenant GUC makes the row invisible, so
        // the versioned UPDATE affects 0 rows. The canonical transfer
        // primitive translates this to CRM_CONCURRENCY_CONFLICT — the
        // caller (ContactTransferUseCases) is responsible for calling
        // findByIdForUpdate first, which surfaces the more specific
        // CRM_CONTACT_NOT_FOUND when the contact is genuinely absent.
        assertThatThrownBy(() -> inTenantTransaction(otherTenant, () ->
                contacts.transferOwner(otherTenant, actorId, created.id(), newOwner, created.version(),
                        java.time.Instant.now())))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));

        // The original Contact must be unchanged.
        ContactRecord current = inTenantTransaction(tenantId, () -> contacts.findById(tenantId, created.id()));
        assertThat(current.ownerUserId()).isEqualTo(originalOwner);
        assertThat(current.version()).isEqualTo(created.version());
    }
}
