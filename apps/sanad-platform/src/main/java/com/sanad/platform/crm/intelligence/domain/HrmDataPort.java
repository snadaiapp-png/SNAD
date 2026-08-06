package com.sanad.platform.crm.intelligence.domain;

import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral port for HRM data.
 */
public interface HrmDataPort {
    HrmAccountTeamSnapshot loadAccountTeam(UUID tenantId, UUID accountId);

    record HrmAccountTeamSnapshot(
            UUID accountId, String accountManagerName, String accountManagerEmail,
            List<String> teamMembers, int teamSize, String coverageStatus,
            boolean available) {

        public static HrmAccountTeamSnapshot unavailable(UUID accountId) {
            return new HrmAccountTeamSnapshot(accountId, "N/A", null,
                    List.of(), 0, "UNKNOWN", false);
        }
    }
}
