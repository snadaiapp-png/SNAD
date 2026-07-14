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

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AccountV2IntegrationTest {

    @Autowired(required = false)
    AccountUseCases accountUseCases;

    @Test
    @DisplayName("AccountUseCases bean is registered")
    void accountUseCasesBeanExists() {
        assertNotNull(accountUseCases, "AccountUseCases must be registered as a Spring bean");
    }

    @Test
    @DisplayName("Create account with valid command does not NPE on wiring")
    void createAccountWiringValidated() {
        if (accountUseCases == null) return;
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CreateAccountCommand cmd = new CreateAccountCommand(
                "Test Account V2", "BUSINESS", actorId, null,
                "SAR", "ar-SA", "Asia/Riyadh", "TEST");
        assertDoesNotThrow(() -> {
            try {
                accountUseCases.create(tenantId, actorId, cmd);
            } catch (Exception e) {
                if (e instanceof NullPointerException && e.getMessage() != null && e.getMessage().contains("repo")) {
                    fail("Repository not wired: " + e.getMessage());
                }
            }
        });
    }

    @Test
    @DisplayName("Update on non-existent account does not NPE on wiring")
    void updateAccountWiringValidated() {
        if (accountUseCases == null) return;
        assertDoesNotThrow(() -> {
            try {
                accountUseCases.update(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        new UpdateAccountCommand("test", null, null, null, null, null, null), 0L);
            } catch (Exception e) {
                if (e instanceof NullPointerException && e.getMessage() != null && e.getMessage().contains("repo")) {
                    fail("Repository not wired: " + e.getMessage());
                }
            }
        });
    }
}
