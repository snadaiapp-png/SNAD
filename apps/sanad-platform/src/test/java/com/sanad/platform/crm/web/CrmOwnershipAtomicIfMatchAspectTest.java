package com.sanad.platform.crm.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CrmOwnershipAtomicIfMatchAspectTest {

    @Test
    void resolvesEveryGovernedOwnershipMutationToItsDatabaseRow() {
        UUID team = UUID.randomUUID();
        UUID membership = UUID.randomUUID();
        UUID queue = UUID.randomUUID();
        UUID territory = UUID.randomUUID();
        UUID territoryAssignment = UUID.randomUUID();
        UUID rule = UUID.randomUUID();
        UUID transfer = UUID.randomUUID();

        assertTarget("PATCH", "/api/v2/crm/teams/" + team,
                "crm_sales_teams", "sales-team", team);
        assertTarget("PATCH", "/api/v2/crm/teams/" + team + "/memberships/" + membership,
                "crm_team_memberships", "team-membership", membership);
        assertTarget("DELETE", "/api/v2/crm/teams/" + team + "/memberships/" + membership,
                "crm_team_memberships", "team-membership", membership);
        assertTarget("PATCH", "/api/v2/crm/queues/" + queue,
                "crm_queues", "queue", queue);
        assertTarget("PATCH", "/api/v2/crm/territories/" + territory,
                "crm_territories", "territory", territory);
        assertTarget("DELETE", "/api/v2/crm/territories/" + territory
                        + "/assignments/" + territoryAssignment,
                "crm_territory_assignments", "territory-assignment", territoryAssignment);
        assertTarget("PATCH", "/api/v2/crm/assignment-rules/" + rule + "/versions/4/activate",
                "crm_assignment_rules", "assignment-rule", rule);
        assertTarget("POST", "/api/v2/crm/transfers/" + transfer + "/submit",
                "crm_transfer_requests", "transfer-request", transfer);
        assertTarget("POST", "/api/v2/crm/transfers/" + transfer + "/approve",
                "crm_transfer_requests", "transfer-request", transfer);
        assertTarget("POST", "/api/v2/crm/transfers/" + transfer + "/cancel",
                "crm_transfer_requests", "transfer-request", transfer);
    }

    @Test
    void ignoresReadsCreatesAndUnrelatedOwnershipPaths() {
        assertThat(resolve("GET", "/api/v2/crm/teams/" + UUID.randomUUID())).isNull();
        assertThat(resolve("POST", "/api/v2/crm/teams")).isNull();
        assertThat(resolve("POST", "/api/v2/crm/queues/" + UUID.randomUUID() + "/items/"
                + UUID.randomUUID() + "/claim")).isNull();
        assertThat(resolve("PATCH", "/api/v2/crm/accounts/" + UUID.randomUUID())).isNull();
    }

    @Test
    void timestampVersionMatchesMicrosecondContract() {
        Instant value = Instant.parse("2026-07-26T12:34:56.123456789Z");
        long expected = Math.addExact(
                Math.multiplyExact(value.getEpochSecond(), 1_000_000L),
                123_456L);
        assertThat(CrmOwnershipAtomicIfMatchAspect.timestampVersion(value)).isEqualTo(expected);
    }

    private void assertTarget(
            String method,
            String path,
            String table,
            String entityType,
            UUID id) {
        CrmOwnershipAtomicIfMatchAspect.LockTarget target = resolve(method, path);
        assertThat(target).isNotNull();
        assertThat(target.table()).isEqualTo(table);
        assertThat(target.entityType()).isEqualTo(entityType);
        assertThat(target.id()).isEqualTo(id);
    }

    private CrmOwnershipAtomicIfMatchAspect.LockTarget resolve(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        return CrmOwnershipAtomicIfMatchAspect.resolveTarget(request);
    }
}
