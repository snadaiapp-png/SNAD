package com.sanad.platform.crm.intelligence.infrastructure;

import com.sanad.platform.crm.intelligence.domain.HrmDataPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Mock HRM adapter — returns deterministic synthetic account team data.
 */
@Component
@ConditionalOnProperty(name = "sanad.intelligence.hrm.provider", havingValue = "mock", matchIfMissing = true)
public class MockHrmDataAdapter implements HrmDataPort {

    @Override
    public HrmAccountTeamSnapshot loadAccountTeam(UUID tenantId, UUID accountId) {
        int hash = Math.abs(accountId.hashCode());
        String[] managers = {"Ahmed Al-Rashid", "Fatima Al-Zahra", "Omar Al-Saud", "Layla Al-Otaibi"};
        String[] members = {"Sales Rep A", "Sales Rep B", "CSM Lead C"};
        String coverage = (hash % 3 == 0) ? "PARTIAL_COVERAGE" : "FULL_COVERAGE";
        return new HrmAccountTeamSnapshot(
                accountId,
                managers[hash % managers.length],
                "account.manager@sanad.sa",
                List.of(members),
                members.length + 1,
                coverage,
                true
        );
    }
}
