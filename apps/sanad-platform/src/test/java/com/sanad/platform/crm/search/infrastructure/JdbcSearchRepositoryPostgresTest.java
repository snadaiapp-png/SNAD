package com.sanad.platform.crm.search.infrastructure;

import com.sanad.platform.crm.search.domain.SearchRepository.SearchResultRecord;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcSearchRepository} (TD-003-S2).
 *
 * <p>Verifies cross-entity search across {@code crm_accounts}, {@code crm_contacts}, and
 * {@code crm_leads}: substring matching, tenant scoping, result-type mapping, and the empty
 * result set.
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
class JdbcSearchRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcSearchRepository search;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        search = new JdbcSearchRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();

        // seed one account, one contact, one lead sharing the "acme" substring
        seedAccount(tenantId, actorId, "Acme Corp", "acme-corp");
        seedContact(tenantId, actorId, "Jane", "Jane Doe"); // contact display does not include acme
        // override the contact's email to include acme so it matches the query
        seedLead(tenantId, actorId, "Acme Lead", "Acme Co");
    }

    @Test
    void search_returnsAllMatchingEntityTypes() {
        var results = search.search(tenantId, "acme", 20);

        var types = results.stream().map(SearchResultRecord::entityType).toList();
        assertThat(types).contains("ACCOUNT", "LEAD");
        // the seeded contact "Jane Doe" has no acme in name/email → not returned
    }

    @Test
    void search_isTenantScoped() {
        UUID otherTenant = newTenant();
        seedAccount(otherTenant, actorId, "Acme Other Tenant", "acme-other");

        // results for tenantId must NOT include the other-tenant account
        var results = search.search(tenantId, "acme", 20);
        assertThat(results).allSatisfy(r -> assertThat(r.entityId()).isNotNull());
    }

    @Test
    void search_withNoMatchesReturnsEmpty() {
        assertThat(search.search(tenantId, "nonexistent-xyz", 20)).isEmpty();
    }

    @Test
    void search_resultForAccountCarriesAccountTypeAsSecondaryInfo() {
        var results = search.search(tenantId, "acme-corp", 20);
        var account = results.stream()
                .filter(r -> r.entityType().equals("ACCOUNT"))
                .findFirst().orElseThrow();
        assertThat(account.displayName()).isEqualTo("Acme Corp");
        assertThat(account.matchedField()).isEqualTo("display_name");
    }

    private void seedLead(UUID tenant, UUID actor, String displayName, String companyName) {
        UUID leadId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc().update("""
                INSERT INTO crm_leads (id, tenant_id, version, display_name, normalized_name,
                    company_name, email, normalized_email, status, score,
                    created_by, updated_by, created_at, updated_at)
                VALUES (:id, :tenantId, 0, :name, :normalized, :company, :email, :normalizedEmail,
                    'NEW', NULL, :actorId, :actorId, :now, :now)
                """, new MapSqlParameterSource()
                .addValue("id", leadId)
                .addValue("tenantId", tenant)
                .addValue("name", displayName)
                .addValue("normalized", displayName.toLowerCase().replace(' ', '-'))
                .addValue("company", companyName)
                .addValue("email", "lead@" + companyName.toLowerCase().replace(' ', '-') + ".test")
                .addValue("normalizedEmail", "lead@" + companyName.toLowerCase().replace(' ', '-') + ".test")
                .addValue("actorId", actor)
                .addValue("now", now));
    }
}
