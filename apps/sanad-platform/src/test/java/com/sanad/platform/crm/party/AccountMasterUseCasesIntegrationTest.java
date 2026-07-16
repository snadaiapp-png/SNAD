package com.sanad.platform.crm.party;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.party.application.AccountMasterUseCases;
import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.CreateAccountRelationshipCommand;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.CreateExternalIdentifierCommand;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.CreateTaxonomyCommand;
import com.sanad.platform.crm.party.domain.AccountMasterRepository.UpdateAccountProfileCommand;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.domain.AccountRepository.CreateAccountCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AccountMasterUseCasesIntegrationTest {

    @Autowired AccountMasterUseCases master;
    @Autowired AccountUseCases accounts;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test
    void createsEnterpriseProfileAndHonestProjectionContracts() {
        TenantUser context = seedTenantAndOwner();
        AccountRecord account = createAccount(context, "Acme Arabia");

        var view = master.get(context.tenantId(), account.id());

        assertEquals("Acme Arabia", view.profile().legalName());
        assertEquals(0, view.profile().version());
        assertEquals(3, view.projections().size());
        assertTrue(view.projections().stream()
                .allMatch(item -> "NOT_CONNECTED".equals(item.connectionStatus())));
        assertTrue(view.projections().stream()
                .allMatch(item -> item.payloadJson() == null));
    }

    @Test
    void updatesProfileWithOptimisticConcurrencyAndTaxonomyReferences() {
        TenantUser context = seedTenantAndOwner();
        AccountRecord account = createAccount(context, "Profile Account");
        var classification = master.createTaxonomy(
                context.tenantId(), context.userId(),
                new CreateTaxonomyCommand(
                        "CLASSIFICATION", "STRATEGIC", "استراتيجي", "Strategic", null));
        var segment = master.createTaxonomy(
                context.tenantId(), context.userId(),
                new CreateTaxonomyCommand(
                        "SEGMENT", "ENTERPRISE", "منشآت كبرى", "Enterprise", null));

        var updated = master.updateProfile(
                context.tenantId(), context.userId(), account.id(),
                new UpdateAccountProfileCommand(
                        "Acme Arabia LLC", "Acme", "CR-100", "VAT-200",
                        "TECHNOLOGY", "ENTERPRISE", "https://example.test", "PLATINUM",
                        "HIGH", List.of("COMPLIANCE_REVIEW"),
                        classification.id(), segment.id(), true),
                0);

        assertEquals(1, updated.version());
        assertEquals("Acme Arabia LLC", updated.legalName());
        assertEquals("HIGH", updated.riskLevel());
        assertEquals(classification.id(), updated.classificationId());
        assertEquals(segment.id(), updated.segmentId());
        assertTrue(updated.mergeCandidate());

        assertThrows(CrmContractException.class, () -> master.updateProfile(
                context.tenantId(), context.userId(), account.id(),
                new UpdateAccountProfileCommand(
                        "Stale", null, null, null, null, null, null, null,
                        null, null, null, null, null),
                0));
    }

    @Test
    void rejectsRelationshipCycles() {
        TenantUser context = seedTenantAndOwner();
        AccountRecord first = createAccount(context, "First");
        AccountRecord second = createAccount(context, "Second");

        var relationship = master.createRelationship(
                context.tenantId(), context.userId(),
                new CreateAccountRelationshipCommand(
                        first.id(), second.id(), "PARENT",
                        LocalDate.now(), null, "First points to second"));

        assertEquals("ACTIVE", relationship.status());
        assertThrows(CrmContractException.class, () -> master.createRelationship(
                context.tenantId(), context.userId(),
                new CreateAccountRelationshipCommand(
                        second.id(), first.id(), "PARENT",
                        LocalDate.now(), null, "Would close a cycle")));
    }

    @Test
    void scopesExternalIdentifiersByTenantAndProviderSystem() {
        TenantUser tenantA = seedTenantAndOwner();
        AccountRecord accountA1 = createAccount(tenantA, "A1");
        AccountRecord accountA2 = createAccount(tenantA, "A2");

        var identifier = master.addExternalIdentifier(
                tenantA.tenantId(), tenantA.userId(), accountA1.id(),
                new CreateExternalIdentifierCommand("SAP", "CUSTOMER", "C-100", "Legacy customer"));
        assertNotNull(identifier.id());

        assertThrows(CrmContractException.class, () -> master.addExternalIdentifier(
                tenantA.tenantId(), tenantA.userId(), accountA2.id(),
                new CreateExternalIdentifierCommand("SAP", "CUSTOMER", "C-100", null)));

        TenantUser tenantB = seedTenantAndOwner();
        AccountRecord accountB = createAccount(tenantB, "B1");
        var tenantBIdentifier = master.addExternalIdentifier(
                tenantB.tenantId(), tenantB.userId(), accountB.id(),
                new CreateExternalIdentifierCommand("SAP", "CUSTOMER", "C-100", null));
        assertNotNull(tenantBIdentifier.id());
    }

    @Test
    void recordsStatusAndOwnershipHistory() {
        TenantUser context = seedTenantAndOwner();
        AccountRecord created = createAccount(context, "History Account");
        AccountRecord archived = accounts.archive(
                context.tenantId(), context.userId(), created.id(), created.version());

        var history = master.get(context.tenantId(), created.id());
        assertTrue(history.statusHistory().stream()
                .anyMatch(item -> "ACTIVE".equals(item.toStatus())));
        assertTrue(history.statusHistory().stream()
                .anyMatch(item -> "ARCHIVED".equals(item.toStatus())));
        assertFalse(history.ownershipHistory().isEmpty());
        assertEquals("ARCHIVED", archived.lifecycleStatus());
    }

    @Test
    void deniesCrossTenantMasterAccess() {
        TenantUser tenantA = seedTenantAndOwner();
        AccountRecord accountA = createAccount(tenantA, "Tenant A Private");
        TenantUser tenantB = seedTenantAndOwner();

        assertThrows(CrmContractException.class,
                () -> master.get(tenantB.tenantId(), accountA.id()));
    }

    @Test
    void migrationCreatedAccountMasterTablesAndCapabilities() {
        List<String> tables = List.of(
                "CRM_ACCOUNT_PROFILES",
                "CRM_ACCOUNT_RELATIONSHIPS",
                "CRM_ACCOUNT_EXTERNAL_IDENTIFIERS",
                "CRM_ACCOUNT_STATUS_HISTORY",
                "CRM_ACCOUNT_OWNERSHIP_HISTORY",
                "CRM_ACCOUNT_PROJECTION_SNAPSHOTS",
                "CRM_ACCOUNT_TAXONOMIES");
        for (String table : tables) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME=:name",
                    new MapSqlParameterSource("name", table), Integer.class);
            assertEquals(1, count, table + " must exist");
        }
        Long capabilities = jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_capabilities WHERE code LIKE 'CRM.ACCOUNT.%'",
                new MapSqlParameterSource(), Long.class);
        assertNotNull(capabilities);
        assertTrue(capabilities >= 9);
    }

    private AccountRecord createAccount(TenantUser context, String name) {
        return accounts.create(
                context.tenantId(), context.userId(),
                new CreateAccountCommand(
                        name, "BUSINESS", context.userId(), null,
                        "SAR", "ar-SA", "Asia/Riyadh", "CRM-005-TEST"));
    }

    private TenantUser seedTenantAndOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) " +
                        "VALUES (:id,'CRM-005 Tenant',:subdomain,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("id", tenantId)
                        .addValue("subdomain", "crm005-" + tenantId.toString().substring(0, 8)));
        jdbc.update(
                "INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) " +
                        "VALUES (:id,:tenantId,:email,'CRM-005 Owner','ACTIVE','dummy',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("id", userId)
                        .addValue("tenantId", tenantId)
                        .addValue("email", "crm005-" + userId.toString().substring(0, 8) + "@example.test"));
        return new TenantUser(tenantId, userId);
    }

    private record TenantUser(UUID tenantId, UUID userId) { }
}
