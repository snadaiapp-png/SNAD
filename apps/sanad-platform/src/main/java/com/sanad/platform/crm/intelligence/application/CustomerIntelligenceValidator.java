package com.sanad.platform.crm.intelligence.application;

import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Validation framework for customer intelligence operations.
 * Enforces tenant ownership, customer existence, and active status.
 */
@Component
public class CustomerIntelligenceValidator {

    private final AccountRepository accountRepository;

    public CustomerIntelligenceValidator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Validates that the account exists, belongs to the tenant, and is active.
     *
     * @throws CustomerValidationException if validation fails
     */
    public AccountRecord validateCustomer(UUID tenantId, UUID accountId) {
        AccountRecord account = accountRepository.findById(tenantId, accountId);
        if (account == null) {
            throw new CustomerValidationException(
                    "ACCOUNT_NOT_FOUND", "Customer account not found: " + accountId);
        }

        if (!"ACTIVE".equalsIgnoreCase(account.lifecycleStatus())) {
            throw new CustomerValidationException(
                    "ACCOUNT_INACTIVE", "Customer account is not active: " + account.lifecycleStatus());
        }

        return account;
    }

    /**
     * Validates a score type is within the allowed set.
     */
    public void validateScoreType(String scoreType) {
        if (scoreType == null || scoreType.isBlank()) {
            throw new CustomerValidationException("INVALID_SCORE_TYPE", "Score type is required");
        }
        if (!java.util.Set.of("HEALTH", "CLV", "ENGAGEMENT", "RISK", "LOYALTY").contains(scoreType)) {
            throw new CustomerValidationException(
                    "INVALID_SCORE_TYPE", "Unknown score type: " + scoreType);
        }
    }

    /**
     * Validates confidence threshold.
     */
    public void validateConfidence(double confidence, double minThreshold) {
        if (confidence < minThreshold) {
            throw new CustomerValidationException(
                    "LOW_CONFIDENCE",
                    "Confidence " + confidence + " below threshold " + minThreshold);
        }
    }

    /**
     * Standard validation exception for customer intelligence operations.
     */
    public static class CustomerValidationException extends RuntimeException {
        private final String code;

        public CustomerValidationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() { return code; }
    }
}
