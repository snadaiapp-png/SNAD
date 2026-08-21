package com.sanad.crm.callerid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ring budget policy (G8 EXECUTION 05 §13, §48): internal target ≤ 300 ms,
 * hard fallback ≤ 750 ms, platform hard deadline 5 s. The service consults
 * this policy before every expensive step and responds ALLOW regardless.
 */
object RingBudgetPolicy {

    /** True when the internal fallback budget is exhausted → respond ALLOW. */
    fun shouldFallback(elapsedMs: Long): Boolean =
        elapsedMs > CallerIdConstants.HARD_INTERNAL_FALLBACK_MS

    /** True when the target decision budget is met (≤ 300 ms). */
    fun withinTarget(elapsedMs: Long): Boolean =
        elapsedMs <= CallerIdConstants.TARGET_NATIVE_DECISION_MS

    /** Platform hard deadline margin check — must always hold before respond. */
    fun withinPlatformDeadline(elapsedMs: Long): Boolean =
        elapsedMs < CallerIdConstants.PLATFORM_DEADLINE_MS
}

class RingBudgetPolicyTest {

    @Test
    fun targetBudgetIs300ms() {
        assertTrue(RingBudgetPolicy.withinTarget(250))
        assertTrue(RingBudgetPolicy.withinTarget(300))
        assertFalse(RingBudgetPolicy.withinTarget(301))
    }

    @Test
    fun fallbackKicksInAfter750ms() {
        assertFalse(RingBudgetPolicy.shouldFallback(100))
        assertFalse(RingBudgetPolicy.shouldFallback(750))
        assertTrue(RingBudgetPolicy.shouldFallback(751))
        assertTrue(RingBudgetPolicy.shouldFallback(4999))
    }

    @Test
    fun platformDeadlineIsNeverExceeded() {
        assertTrue(RingBudgetPolicy.withinPlatformDeadline(4999))
        assertFalse(RingBudgetPolicy.withinPlatformDeadline(5000))
    }
}
