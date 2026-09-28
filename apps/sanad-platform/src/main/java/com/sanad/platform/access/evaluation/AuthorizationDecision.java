package com.sanad.platform.access.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed, explainable result of the five-stage unified decision pipeline
 * (spec §4.1 Revision D).
 *
 * @param decision        "ALLOW" or "DENY"
 * @param capability      canonical capability code the decision was made for
 * @param source          the {@link DecisionSource} the decision is attributed to
 * @param reason          stable machine-readable reason string
 * @param matchedRoleId   id of the role that matched (ROLE_GRANT only)
 * @param matchedRoleCode code of the role that matched (ROLE_GRANT only)
 * @param scopeType       scope type of the winning candidate, when applicable
 * @param policy          relationship/delegation policy identifier, when applicable
 * @param decisionId      unique id of this decision (traceability)
 * @param evaluatedAt     evaluation timestamp
 * @param trace           deterministic stage-by-stage evaluation trace
 */
public record AuthorizationDecision(
        String decision,
        String capability,
        DecisionSource source,
        String reason,
        UUID matchedRoleId,
        String matchedRoleCode,
        String scopeType,
        String policy,
        UUID decisionId,
        Instant evaluatedAt,
        List<String> trace) {

    public AuthorizationDecision {
        trace = trace == null ? List.of() : List.copyOf(trace);
    }
}
