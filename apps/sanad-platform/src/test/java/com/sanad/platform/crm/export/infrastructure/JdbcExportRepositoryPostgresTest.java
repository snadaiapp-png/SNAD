package com.sanad.platform.crm.export.infrastructure;

import com.sanad.platform.crm.export.domain.ExportRepository.AccountExportRow;
import com.sanad.platform.crm.export.domain.ExportRepository.ContactExportRow;
import com.sanad.platform.crm.export.domain.ExportRepository.LeadExportRow;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcExportRepository} (TD-003-S2).
 *
 * <p>Validates the read-only export queries (accounts, contacts, leads) against a real
 * PostgreSQL instance: substring filtering, tenant scoping, field mapping, and the empty result
 * path. Verifies null {@code score} round-trips as null {@code BigDecimal}.
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
class JdbcExportRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcExportRepository export;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        export = new JdbcExportRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();

        seedAccount(tenantId, actorId, "Acme Export", "acme-export");
        seedContact(tenantId, actorId, "Jane", "Jane Export");
        seedLead(tenantId, actorId, "Acme Lead Export", "Acme Co");
    }

    @Test
    void exportAccounts_filtersBySearchSubstring() {
        var rows = export.exportAccounts(tenantId, "acme", 50);

        assertThat(rows).hasSize(1);
        AccountExportRow row = rows.get(0);
        assertThat(row.displayName()).isEqualTo("Acme Export");
        assertThat(row.accountType()).isEqualTo("BUSINESS");
        assertThat(row.lifecycleStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void exportAccounts_withNullSearchReturnsAll() {
        var rows = export.exportAccounts(tenantId, null, 50);
        assertThat(rows).hasSize(1);
    }

    @Test
    void exportContacts_mapsNameAndEmail() {
        var rows = export.exportContacts(tenantId, "export", 50);

        assertThat(rows).hasSize(1);
        ContactExportRow row = rows.get(0);
        assertThat(row.givenName()).isEqualTo("Jane");
    }

    @Test
    void exportLeads_mapsCompanyAndNullScore() {
        var rows = export.exportLeads(tenantId, "acme", 50);

        assertThat(rows).hasSize(1);
        LeadExportRow row = rows.get(0);
        assertThat(row.displayName()).isEqualTo("Acme Lead Export");
        assertThat(row.companyName()).isEqualTo("Acme Co");
        assertThat(row.status()).isEqualTo("NEW");
        // score seeded as NULL → must round-trip as null, not zero
        assertThat(row.score()).isNull();
    }

    @Test
    void exportAccounts_isTenantScoped() {
        UUID otherTenant = newTenant();
        seedAccount(otherTenant, actorId, "Acme Other", "acme-other");

        var rows = export.exportAccounts(tenantId, "acme", 50);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).displayName()).isEqualTo("Acme Export");
    }

    private void seedLead(UUID tenant, UUID actor, String displayName, String company) {
        UUID leadId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc().update("""
                INSERT INTO crm_leads (id, tenant_id, version, display_name, normalized_name,
                    company_name, email, normalized_email, status, score,
                    created_by, updated_by, created_at, updated_at)
                VALUES (:id, :tenantId, 0, :name, :normalized, :company, NULL, NULL,
                    'NEW', NULL, :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", leadId)
                .addValue("tenantId", tenant)
                .addValue("name", displayName)
                .addValue("normalized", displayName.toLowerCase().replace(' ', '-'))
                .addValue("company", company)
                .addValue("actorId", actor)
                .addValue("now", now));
    }
}
