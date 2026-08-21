package com.sanad.crm.callerid

/**
 * Ring-time match resolver — native parity implementation of the backend
 * tiered policy (G8-ADR-005) and Track D offline ranking (G8-05 §4, §34).
 * PURE Kotlin (no Android deps) so the whole ranking policy is JVM-testable.
 *
 * Tiers: verified CONTACT < preferred CONTACT < other CONTACT < ACCOUNT < LEAD/other.
 * Resolution: one best tier -> EXACT (single) / AMBIGUOUS (multiple);
 * RESTRICTED entries are matched by token only and never expose identity;
 * nothing matched -> UNKNOWN. NO first-row-wins, NO fuzzy, NO suffix match.
 */
object NativeCallerResolver {

    const val TYPE_CONTACT = "CONTACT"
    const val TYPE_ACCOUNT = "ACCOUNT"

    enum class MatchState { EXACT, AMBIGUOUS, UNKNOWN, RESTRICTED, INVALID_NUMBER }

    data class Candidate(
        val entityType: String?,
        val entityId: String?,
        val displayNameEncrypted: String?,
        val accountNameEncrypted: String?,
        val phoneLabel: String?,
        val verified: Boolean,
        val preferred: Boolean,
        val privacyLevel: String?,
    ) {
        val isRestricted: Boolean get() = privacyLevel == "RESTRICTED"
    }

    data class Resolution(
        val state: MatchState,
        val candidateCount: Int,
        val candidate: Candidate?,
    )

    /** Tier rank mirroring Track D `tierOf` (CONTACT verified=0 … other=4). */
    fun tierOf(entityType: String?, verified: Boolean, preferred: Boolean): Int = when (entityType) {
        TYPE_CONTACT -> when {
            verified -> 0
            preferred -> 1
            else -> 2
        }
        TYPE_ACCOUNT -> 3
        else -> 4
    }

    /**
     * Resolve candidates returned by the indexed lookup. A RESTRICTED hit
     * resolves to RESTRICTED with NO identity candidate (server-side policy:
     * RESTRICTED never carries display PII — G8-03 §41).
     */
    fun resolve(candidates: List<Candidate>): Resolution {
        val distinct = candidates.distinctBy {
            Triple(it.entityType, it.entityId, it.lookupKey())
        }
        if (distinct.isEmpty()) return Resolution(MatchState.UNKNOWN, 0, null)

        val restricted = distinct.filter { it.isRestricted }
        if (restricted.isNotEmpty()) {
            return Resolution(MatchState.RESTRICTED, restricted.size, null)
        }

        val sorted = distinct.sortedWith(
            compareBy<Candidate> { tierOf(it.entityType, it.verified, it.preferred) }
                .thenBy { it.entityId ?: "" }
        )
        val bestTier = tierOf(sorted.first().entityType, sorted.first().verified, sorted.first().preferred)
        val best = sorted.filter {
            tierOf(it.entityType, it.verified, it.preferred) == bestTier
        }
        return if (best.size == 1) {
            Resolution(MatchState.EXACT, 1, best.first())
        } else {
            Resolution(MatchState.AMBIGUOUS, best.size, null)
        }
    }

    private fun Candidate.lookupKey(): String = "$entityType|$entityId"
}
