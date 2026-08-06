package com.sanad.platform.crm.configuration.infrastructure;

import com.sanad.platform.crm.configuration.domain.CustomFieldRepository.CreateCustomFieldCommand;
import com.sanad.platform.crm.configuration.domain.CustomFieldRepository.CustomFieldRecord;
import com.sanad.platform.crm.configuration.domain.CustomFieldRepository.UpdateCustomFieldCommand;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcCustomFieldRepository}.
 *
 * <p>This is the test deferred by TD-003-S2 (see {@code TD-003-S2-IMPLEMENTATION-REPORT.md},
 * Defect A1). It could not be written then because {@code crm_custom_field_definitions}
 * was missing the {@code version}, {@code created_by}, {@code updated_by} and
 * {@code updated_at} columns that the repository INSERTs/UPDATEs — exercising {@code create()}
 * or {@code update()} threw {@code BadSqlGrammarException}. Epic REM-1 reconciled that schema
 * drift (migration {@code V20260804_1}), so this test now locks the contract end-to-end:
 * create round-trip, update with optimistic-concurrency version bump, version-mismatch conflict,
 * not-found, and tenant scoping.
 *
 * <p>Branch: fix/rem-1-crm-schema-drift (Epic REM-1)
 */
class JdbcCustomFieldRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcCustomFieldRepository customFields;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        customFields = new JdbcCustomFieldRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    @Test
    void create_persistsDefinitionWithZeroVersionAndAuditColumns() {
        CustomFieldRecord saved = inTransaction(() -> customFields.create(tenantId, actorId,
                new CreateCustomFieldCommand("ACCOUNT", "vip_tier_" + suffix(),
                        "VIP", "VIP Tier", "TEXT", false, false, false)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();            // optimistic-lock initial value
        assertThat(saved.entityType()).isEqualTo("ACCOUNT");
        assertThat(saved.fieldKey()).startsWith("vip_tier_");
        assertThat(saved.dataType()).isEqualTo("TEXT");
        assertThat(saved.active()).isTrue();
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isNotNull();
    }

    @Test
    void findById_roundTripsCreatedDefinition() {
        CustomFieldRecord saved = createOne("lead_source", "TEXT");

        CustomFieldRecord fetched = customFields.findById(tenantId, saved.id());
        assertThat(fetched).isEqualTo(saved);
    }

    @Test
    void update_bumpsVersionAndMutatesProvidedFields() {
        CustomFieldRecord saved = createOne("industry", "TEXT");

        CustomFieldRecord updated = inTransaction(() -> customFields.update(tenantId, actorId, saved.id(),
                new UpdateCustomFieldCommand("Updated AR", "Updated EN", null, true, null),
                saved.version()));

        assertThat(updated.version()).isEqualTo(saved.version() + 1);   // optimistic-lock bump
        assertThat(updated.labelAr()).isEqualTo("Updated AR");
        assertThat(updated.labelEn()).isEqualTo("Updated EN");
        assertThat(updated.searchable()).isTrue();
        assertThat(updated.required()).isEqualTo(saved.required());     // unchanged
    }

    @Test
    void update_withStaleVersionThrowsConcurrencyConflict() {
        CustomFieldRecord saved = createOne("rating", "NUMBER");

        // The UPDATE ... WHERE version=:expectedVersion matches 0 rows when the presented
        // version is stale; the repository maps that to CRM_CONCURRENCY_CONFLICT.
        long staleVersion = saved.version() + 99;
        assertThatThrownBy(() -> inTransaction(() -> customFields.update(tenantId, actorId, saved.id(),
                new UpdateCustomFieldCommand("AR", "EN", null, null, null), staleVersion)))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));
    }

    @Test
    void findById_whenMissingThrowsNotFound() {
        assertThatThrownBy(() -> customFields.findById(tenantId, UUID.randomUUID()))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CUSTOM_FIELD_NOT_FOUND));
    }

    @Test
    void findAll_isTenantScopedAndOptionallyFilteredByEntityType() {
        createOne("acct_field", "TEXT");
        createOne("acct_field2", "TEXT");

        UUID otherTenant = newTenant();
        inTransaction(() -> customFields.create(otherTenant, actorId,
                new CreateCustomFieldCommand("ACCOUNT", "other_tenant_field", "AR", "EN",
                        "TEXT", false, false, false)));

        assertThat(customFields.findAll(tenantId, null)).hasSize(2);
        assertThat(customFields.findAll(tenantId, "ACCOUNT")).hasSize(2);
        assertThat(customFields.findAll(otherTenant, null)).hasSize(1);   // tenant isolation
    }

    // ---- helpers ----

    private CustomFieldRecord createOne(String keySuffix, String dataType) {
        return inTransaction(() -> customFields.create(tenantId, actorId,
                new CreateCustomFieldCommand("ACCOUNT", keySuffix + "_" + suffix(),
                        "AR " + keySuffix, "EN " + keySuffix, dataType, false, false, false)));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
