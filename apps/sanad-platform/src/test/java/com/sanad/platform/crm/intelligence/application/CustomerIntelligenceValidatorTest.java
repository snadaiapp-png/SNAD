package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerIntelligenceValidatorTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final CustomerIntelligenceValidator validator = new CustomerIntelligenceValidator(accountRepository);

    private AccountRecord testAccount(String status) {
        return new AccountRecord(ACCOUNT_ID, 0L, "Test Account", "test account",
                "CUSTOMER", status, "USD", "en", "UTC", "TEST", null, ACTOR_ID,
                Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("validateCustomer")
    class ValidateCustomerTests {

        @Test
        @DisplayName("should return account when valid and active")
        void shouldReturnAccount_whenValidAndActive() {
            // Arrange
            AccountRecord account = testAccount("ACTIVE");
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(account);

            // Act
            AccountRecord result = validator.validateCustomer(TENANT_ID, ACCOUNT_ID);

            // Assert
            assertThat(result).isEqualTo(account);
        }

        @Test
        @DisplayName("should throw ACCOUNT_NOT_FOUND when account does not exist")
        void shouldThrow_whenAccountNotFound() {
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(null);

            assertThatThrownBy(() -> validator.validateCustomer(TENANT_ID, ACCOUNT_ID))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class)
                    .satisfies(e -> {
                        var ex = (CustomerIntelligenceValidator.CustomerValidationException) e;
                        assertThat(ex.code()).isEqualTo("ACCOUNT_NOT_FOUND");
                        assertThat(ex.getMessage()).contains(ACCOUNT_ID.toString());
                    });
        }

        @Test
        @DisplayName("should throw ACCOUNT_INACTIVE when account is inactive")
        void shouldThrow_whenAccountInactive() {
            AccountRecord inactiveAccount = testAccount("INACTIVE");
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(inactiveAccount);

            assertThatThrownBy(() -> validator.validateCustomer(TENANT_ID, ACCOUNT_ID))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class)
                    .satisfies(e -> {
                        var ex = (CustomerIntelligenceValidator.CustomerValidationException) e;
                        assertThat(ex.code()).isEqualTo("ACCOUNT_INACTIVE");
                    });
        }

        @Test
        @DisplayName("should throw ACCOUNT_INACTIVE when account is ARCHIVED")
        void shouldThrow_whenAccountArchived() {
            AccountRecord archivedAccount = testAccount("ARCHIVED");
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(archivedAccount);

            assertThatThrownBy(() -> validator.validateCustomer(TENANT_ID, ACCOUNT_ID))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class)
                    .satisfies(e -> assertThat(((CustomerIntelligenceValidator.CustomerValidationException) e).code())
                            .isEqualTo("ACCOUNT_INACTIVE"));
        }

        @Test
        @DisplayName("should be case-insensitive for ACTIVE status")
        void shouldHandleCaseInsensitiveStatus() {
            AccountRecord activeAccount = testAccount("active");
            when(accountRepository.findById(TENANT_ID, ACCOUNT_ID)).thenReturn(activeAccount);

            assertThatNoException().isThrownBy(() -> validator.validateCustomer(TENANT_ID, ACCOUNT_ID));
        }
    }

    @Nested
    @DisplayName("validateScoreType")
    class ValidateScoreTypeTests {

        @Test
        @DisplayName("should accept valid score types")
        void shouldAcceptValidTypes() {
            assertThatNoException().isThrownBy(() -> validator.validateScoreType("HEALTH"));
            assertThatNoException().isThrownBy(() -> validator.validateScoreType("CLV"));
            assertThatNoException().isThrownBy(() -> validator.validateScoreType("ENGAGEMENT"));
            assertThatNoException().isThrownBy(() -> validator.validateScoreType("RISK"));
            assertThatNoException().isThrownBy(() -> validator.validateScoreType("LOYALTY"));
        }

        @Test
        @DisplayName("should reject null score type")
        void shouldRejectNull() {
            assertThatThrownBy(() -> validator.validateScoreType(null))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class)
                    .satisfies(e -> assertThat(((CustomerIntelligenceValidator.CustomerValidationException) e).code())
                            .isEqualTo("INVALID_SCORE_TYPE"));
        }

        @Test
        @DisplayName("should reject blank score type")
        void shouldRejectBlank() {
            assertThatThrownBy(() -> validator.validateScoreType("  "))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class);
        }

        @Test
        @DisplayName("should reject unknown score type")
        void shouldRejectUnknownType() {
            assertThatThrownBy(() -> validator.validateScoreType("UNKNOWN"))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class)
                    .satisfies(e -> assertThat(((CustomerIntelligenceValidator.CustomerValidationException) e).getMessage())
                            .contains("UNKNOWN"));
        }
    }

    @Nested
    @DisplayName("validateConfidence")
    class ValidateConfidenceTests {

        @Test
        @DisplayName("should accept confidence above threshold")
        void shouldAcceptAboveThreshold() {
            assertThatNoException().isThrownBy(() -> validator.validateConfidence(0.85, 0.6));
        }

        @Test
        @DisplayName("should accept confidence equal to threshold")
        void shouldAcceptAtThreshold() {
            assertThatNoException().isThrownBy(() -> validator.validateConfidence(0.6, 0.6));
        }

        @Test
        @DisplayName("should reject confidence below threshold")
        void shouldRejectBelowThreshold() {
            assertThatThrownBy(() -> validator.validateConfidence(0.4, 0.6))
                    .isInstanceOf(CustomerIntelligenceValidator.CustomerValidationException.class)
                    .satisfies(e -> {
                        var ex = (CustomerIntelligenceValidator.CustomerValidationException) e;
                        assertThat(ex.code()).isEqualTo("LOW_CONFIDENCE");
                    });
        }
    }
}
