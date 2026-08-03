package com.sanad.platform.crm.party.infrastructure;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.AccountAddress;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.AccountIdentifier;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.CreateAddressCommand;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.CreateIdentifierCommand;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.CustomerMasterProfile;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.UpdateCustomerMasterCommand;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcCustomerMasterRepository}
 * (TD-003-S2).
 *
 * <p>Operates on {@code crm_accounts} extended by the enterprise-customer-master migration
 * (V20260716_4). Covers profile read/update with version bump + data-quality recompute, address
 * add with primary-address demotion, identifier add, and the concurrency-conflict path.
 *
 * <p>Branch: crm/td-003-s2-repo-tests
 */
class JdbcCustomerMasterRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcCustomerMasterRepository customerMaster;
    private UUID tenantId;
    private UUID actorId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        customerMaster = new JdbcCustomerMasterRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
        // seed an account WITH enterprise columns so findProfile returns meaningful data
        accountId = seedEnterpriseAccount(tenantId, actorId, "Globex Corp", "globex-corp",
                "Globex Legal", "REG-12345");
    }

    @Test
    void findProfile_returnsEnterpriseColumns() {
        CustomerMasterProfile profile = customerMaster.findProfile(tenantId, accountId);

        assertThat(profile.accountId()).isEqualTo(accountId);
        assertThat(profile.version()).isZero();
        assertThat(profile.legalName()).isEqualTo("Globex Legal");
        assertThat(profile.registrationNumber()).isEqualTo("REG-12345");
        // risk_rating defaults to UNASSESSED when null on read
        assertThat(profile.riskRating()).isEqualTo("UNASSESSED");
    }

    @Test
    void updateProfile_bumpsVersionAndRecomputesDataQuality() {
        CustomerMasterProfile before = customerMaster.findProfile(tenantId, accountId);
        int qualityBefore = before.dataQualityScore();

        CustomerMasterProfile updated = inTransaction(() -> customerMaster.updateProfile(
                tenantId, actorId, accountId,
                new UpdateCustomerMasterCommand("Globex Legal LLC", "Globex Trading",
                        "REG-12345", "TAX-999", "INFORMATION_TECHNOLOGY",
                        "ENTERPRISE", "TIER_1", "globex.test",
                        "info@globex.test", "+966500000000", "SA",
                        "LOW", new BigDecimal("500000"), 30),
                before.version()));

        assertThat(updated.version()).isEqualTo(before.version() + 1);
        assertThat(updated.tradingName()).isEqualTo("Globex Trading");
        assertThat(updated.taxNumber()).isEqualTo("TAX-999");
        // adding more populated fields must not decrease the data-quality score
        assertThat(updated.dataQualityScore()).isGreaterThanOrEqualTo(qualityBefore);
    }

    @Test
    void updateProfile_withStaleVersionThrowsConcurrencyConflict() {
        CustomerMasterProfile before = customerMaster.findProfile(tenantId, accountId);
        inTransaction(() -> customerMaster.updateProfile(tenantId, actorId, accountId,
                new UpdateCustomerMasterCommand("v1 Legal", null, null, null, null,
                        null, null, null, null, null, null, null, null, null),
                before.version())); // -> v1

        // reuse the stale v0 expectedVersion
        assertThatThrownBy(() -> inTransaction(() -> customerMaster.updateProfile(
                tenantId, actorId, accountId,
                new UpdateCustomerMasterCommand("stale", null, null, null, null,
                        null, null, null, null, null, null, null, null, null),
                before.version())))
                .isInstanceOf(CrmContractException.class)
                .satisfies(ex -> assertThat(((CrmContractException) ex).code())
                        .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));
    }

    @Test
    void addAddress_demotesExistingPrimaryWhenNewIsPrimary() {
        AccountAddress first = inTransaction(() -> customerMaster.addAddress(tenantId, actorId,
                accountId, new CreateAddressCommand("REGISTERED", "HQ", "1 Main St",
                        null, "Riyadh", null, "11564", "SA", true)));
        AccountAddress second = inTransaction(() -> customerMaster.addAddress(tenantId, actorId,
                accountId, new CreateAddressCommand("BILLING", "Billing", "2 Other St",
                        null, "Jeddah", null, "21477", "SA", true)));

        assertThat(first.primaryAddress()).isTrue();
        // promoting the second must demote the first
        var refreshed = customerMaster.listAddresses(tenantId, accountId);
        var firstRefreshed = refreshed.stream().filter(a -> a.id().equals(first.id())).findFirst().orElseThrow();
        assertThat(firstRefreshed.primaryAddress()).isFalse();
        assertThat(second.primaryAddress()).isTrue();
    }

    @Test
    void addIdentifier_persistsIdentifier() {
        AccountIdentifier saved = inTransaction(() -> customerMaster.addIdentifier(tenantId, actorId,
                accountId, new CreateIdentifierCommand("VAT", "300000000000003",
                        "SA", true, false)));

        assertThat(saved.identifierType()).isEqualTo("VAT");
        assertThat(saved.identifierValue()).isEqualTo("300000000000003");
        assertThat(saved.primaryIdentifier()).isTrue();

        var identifiers = customerMaster.listIdentifiers(tenantId, accountId);
        assertThat(identifiers).hasSize(1);
    }

    /**
     * Seed an account with the V20260716_4 enterprise columns populated, so findProfile and
     * duplicate-detection have meaningful data to read.
     */
    private UUID seedEnterpriseAccount(UUID tenant, UUID actor, String displayName,
                                       String normalizedName, String legalName, String registrationNumber) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc().update("""
                INSERT INTO crm_accounts (id, tenant_id, version, display_name, normalized_name,
                    account_type, lifecycle_status, created_by, updated_by, created_at, updated_at,
                    legal_name, registration_number, data_quality_score)
                VALUES (:id, :tenantId, 0, :name, :normalized, 'BUSINESS', 'ACTIVE',
                    :actorId, :actorId, :now, :now, :legalName, :regNo, 0)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenant)
                .addValue("name", displayName)
                .addValue("normalized", normalizedName)
                .addValue("actorId", actor)
                .addValue("now", now)
                .addValue("legalName", legalName)
                .addValue("regNo", registrationNumber));
        return id;
    }
}
