package com.sanad.platform.crm.party;

import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.party.application.CustomerMasterUseCases;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.CreateAddressCommand;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.CreateIdentifierCommand;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.CreateRelationshipCommand;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.MergeResult;
import com.sanad.platform.crm.party.domain.CustomerMasterRepository.UpdateCustomerMasterCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("local")
class CustomerMasterMergeIntegrationTest {
    @Autowired CustomerMasterUseCases useCases;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @MockBean AuditPort auditPort;

    private final List<UUID> tenantIds = new ArrayList<>();

    @AfterEach
    void removeCommittedFixtures() {
        for (UUID tenantId : tenantIds) {
            MapSqlParameterSource tenant = p().addValue("tenantId", tenantId);
            jdbc.update("DELETE FROM crm_timeline_events WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_account_merge_history WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_account_status_history WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_account_relationships WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_account_identifiers WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_account_addresses WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_opportunity_stage_history WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_activities WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_opportunities WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_contacts WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_leads WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM crm_accounts WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM platform_audit_logs WHERE target_tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM users WHERE tenant_id=:tenantId", tenant);
            jdbc.update("DELETE FROM tenants WHERE id=:tenantId", tenant);
        }
        tenantIds.clear();
    }

    @Test
    void movesCompleteMasterDataAndEnrichesGoldenRecord() {
        Fixture fixture = fixture("complete-merge");
        UUID source = account(fixture, "Duplicate Enterprise");
        UUID target = account(fixture, "Golden Enterprise");
        UUID related = account(fixture, "Related Enterprise");

        useCases.updateProfile(fixture.tenantId(), fixture.userId(), source,
                new UpdateCustomerMasterCommand(
                        "Duplicate Enterprise LLC", "Duplicate Trading", "CR-MERGE-01", "VAT-MERGE-01",
                        "SOFTWARE", "ENTERPRISE", "GOLD", "https://duplicate.example",
                        "master@duplicate.example", "+966500000001", "SA", "LOW",
                        new BigDecimal("125000"), 30), 0);
        useCases.addAddress(fixture.tenantId(), fixture.userId(), source,
                new CreateAddressCommand("REGISTERED", "HQ", "King Road", null, "Riyadh",
                        null, "12345", "SA", true));
        useCases.addIdentifier(fixture.tenantId(), fixture.userId(), source,
                new CreateIdentifierCommand("COMMERCIAL_REGISTRATION", "CR-MERGE-01", "SA", true, true));
        useCases.addRelationship(fixture.tenantId(), fixture.userId(), source,
                new CreateRelationshipCommand(related, "AFFILIATE", LocalDate.now(), null, "Group member"));

        MergeResult result = useCases.merge(fixture.tenantId(), fixture.userId(), source, target,
                1, 0, "Verified enterprise duplicate");

        assertThat(result.addressesMoved()).isEqualTo(1);
        assertThat(result.identifiersMoved()).isEqualTo(1);
        assertThat(result.relationshipsMoved()).isEqualTo(1);
        assertThat(useCases.listAddresses(fixture.tenantId(), target)).hasSize(1);
        assertThat(useCases.listIdentifiers(fixture.tenantId(), target)).hasSize(1);
        assertThat(useCases.listRelationships(fixture.tenantId(), target)).hasSize(1);

        var golden = useCases.getProfile(fixture.tenantId(), target);
        assertThat(golden.legalName()).isEqualTo("Duplicate Enterprise LLC");
        assertThat(golden.registrationNumber()).isEqualTo("CR-MERGE-01");
        assertThat(golden.primaryEmail()).isEqualTo("master@duplicate.example");
        assertThat(golden.dataQualityScore()).isEqualTo(100);

        Map<String, Object> sourceRow = jdbc.queryForMap(
                "SELECT lifecycle_status,merged_into_account_id FROM crm_accounts WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", source));
        assertThat(sourceRow.get("lifecycle_status")).isEqualTo("ARCHIVED");
        assertThat(sourceRow.get("merged_into_account_id")).isEqualTo(target);

        Map<String, Object> history = jdbc.queryForMap(
                "SELECT addresses_moved,identifiers_moved,relationships_moved FROM crm_account_merge_history " +
                        "WHERE tenant_id=:tenantId AND source_account_id=:sourceId",
                p().addValue("tenantId", fixture.tenantId()).addValue("sourceId", source));
        assertThat(((Number) history.get("addresses_moved")).intValue()).isEqualTo(1);
        assertThat(((Number) history.get("identifiers_moved")).intValue()).isEqualTo(1);
        assertThat(((Number) history.get("relationships_moved")).intValue()).isEqualTo(1);
    }

    @Test
    void rollsBackAllMergeWritesWhenAuditFails() {
        Fixture fixture = fixture("merge-rollback");
        UUID source = account(fixture, "Rollback Source");
        UUID target = account(fixture, "Rollback Target");
        UUID addressId = useCases.addAddress(fixture.tenantId(), fixture.userId(), source,
                new CreateAddressCommand("OFFICE", "Rollback Office", "First Street", null,
                        "Riyadh", null, null, "SA", false)).id();

        doThrow(new IllegalStateException("forced audit failure"))
                .when(auditPort).record(any(), any(), anyString(), anyString(), any(), any(), any());

        assertThatThrownBy(() -> useCases.merge(fixture.tenantId(), fixture.userId(), source, target,
                0, 0, "Rollback proof"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forced audit failure");

        Map<String, Object> sourceRow = jdbc.queryForMap(
                "SELECT lifecycle_status,merged_into_account_id,version FROM crm_accounts " +
                        "WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", source));
        assertThat(sourceRow.get("lifecycle_status")).isEqualTo("ACTIVE");
        assertThat(sourceRow.get("merged_into_account_id")).isNull();
        assertThat(((Number) sourceRow.get("version")).longValue()).isZero();

        UUID addressAccount = jdbc.queryForObject(
                "SELECT account_id FROM crm_account_addresses WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", fixture.tenantId()).addValue("id", addressId), UUID.class);
        assertThat(addressAccount).isEqualTo(source);
        Integer historyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_account_merge_history WHERE tenant_id=:tenantId AND source_account_id=:sourceId",
                p().addValue("tenantId", fixture.tenantId()).addValue("sourceId", source), Integer.class);
        assertThat(historyCount).isZero();
    }

    private Fixture fixture(String key) {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (:id,:name,:subdomain,'ACTIVE',:now,:now)",
                p().addValue("id", tenantId).addValue("name", key)
                        .addValue("subdomain", key + "-" + tenantId.toString().substring(0, 8)).addValue("now", now));
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:email,'CRM Merge User','ACTIVE','dummy',:now,:now)",
                p().addValue("id", userId).addValue("tenantId", tenantId)
                        .addValue("email", key + "-" + userId.toString().substring(0, 8) + "@example.test")
                        .addValue("now", now));
        tenantIds.add(tenantId);
        return new Fixture(tenantId, userId);
    }

    private UUID account(Fixture fixture, String name) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO crm_accounts (id,tenant_id,version,display_name,normalized_name,account_type," +
                        "lifecycle_status,primary_currency_code,preferred_locale,time_zone,source,owner_user_id," +
                        "created_by,updated_by,created_at,updated_at) VALUES (:id,:tenantId,0,:name,:normalized," +
                        "'BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh','CRM005_MERGE_TEST',:owner,:owner,:owner,:now,:now)",
                p().addValue("id", id).addValue("tenantId", fixture.tenantId()).addValue("name", name)
                        .addValue("normalized", name.toLowerCase()).addValue("owner", fixture.userId()).addValue("now", now));
        return id;
    }

    private static MapSqlParameterSource p() { return new MapSqlParameterSource(); }
    private record Fixture(UUID tenantId, UUID userId) {}
}
