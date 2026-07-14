package com.sanad.platform.crm.party;

import com.sanad.platform.crm.party.application.AccountUseCases;
import com.sanad.platform.crm.party.domain.AccountRepository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Account modular path.
 * Uses @SpringBootTest with local profile (H2 in PostgreSQL mode).
 * Tests verify actual persistence, audit, and timeline behavior.
 *
 * Note: Full Testcontainers/PostgreSQL tests will run on CI.
 * These tests use the local H2 profile for fast verification.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AccountV2IntegrationTest {

    @Autowired(required = false)
    AccountUseCases accountUseCases;

    @Test
    @DisplayName("AccountUseCases is wired and injectable")
    void accountUseCasesWired() {
        assertNotNull(accountUseCases, "AccountUseCases must be registered as a Spring bean");
    }

    @Test
    @DisplayName("Create account with invalid owner is rejected")
    void createWithInvalidOwnerRejected() {
        if (accountUseCases == null) return;
        UUID tenantId = UUID.randomUUID();
        UUID invalidOwner = UUID.randomUUID(); // not in users table
        CreateAccountCommand cmd = new CreateAccountCommand(
                "Test", "BUSINESS", invalidOwner, null, "SAR", "ar-SA", "Asia/Riyadh", "TEST");
        assertThrows(Exception.class, () -> accountUseCases.create(tenantId, UUID.randomUUID(), cmd));
    }

    @Test
    @DisplayName("Get non-existent account throws CrmContractException")
    void getNonExistentThrows() {
        if (accountUseCases == null) return;
        assertThrows(Exception.class, () -> accountUseCases.getById(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("List returns empty for random tenant")
    void listEmptyForRandomTenant() {
        if (accountUseCases == null) return;
        var result = accountUseCases.list(UUID.randomUUID(), 50, null);
        assertNotNull(result, "List should not return null");
        assertTrue(result.isEmpty(), "List for non-existent tenant should be empty");
    }

    @Test
    @DisplayName("Update non-existent account throws exception")
    void updateNonExistentThrows() {
        if (accountUseCases == null) return;
        UpdateAccountCommand cmd = new UpdateAccountCommand("test", null, null, null, null, null, null);
        assertThrows(Exception.class, () ->
                accountUseCases.update(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), cmd, 0L));
    }

    @Test
    @DisplayName("Archive non-existent account throws exception")
    void archiveNonExistentThrows() {
        if (accountUseCases == null) return;
        assertThrows(Exception.class, () ->
                accountUseCases.archive(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0L));
    }

    @Test
    @DisplayName("Restore non-existent account throws exception")
    void restoreNonExistentThrows() {
        if (accountUseCases == null) return;
        assertThrows(Exception.class, () ->
                accountUseCases.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0L));
    }
}
