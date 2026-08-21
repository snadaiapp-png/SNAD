package com.sanad.crm.callerid

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Non-PII ring-path instrumentation (G8 EXECUTION 05 §14, §51): monotonic
 * timings and aggregate counters. NEVER logs raw phones, normalized phones,
 * names, keys or encrypted payloads (§50) — only statuses + latencies.
 */
object RingMetrics {

    private const val TAG = "SanadCallerId"

    // counters (status-only labels)
    val total = AtomicLong()
    val exact = AtomicLong()
    val ambiguous = AtomicLong()
    val unknown = AtomicLong()
    val restricted = AtomicLong()
    val roleNotGranted = AtomicLong()
    val datasetStale = AtomicLong()
    val corruption = AtomicLong()

    fun record(state: String, roleHeld: Boolean, stale: Boolean, elapsedMs: Long) {
        total.incrementAndGet()
        when (state) {
            "EXACT" -> exact.incrementAndGet()
            "AMBIGUOUS" -> ambiguous.incrementAndGet()
            "RESTRICTED" -> restricted.incrementAndGet()
            else -> unknown.incrementAndGet()
        }
        if (!roleHeld) roleNotGranted.incrementAndGet()
        if (stale) datasetStale.incrementAndGet()
        Log.i(
            TAG,
            "native_caller_total_ms=$elapsedMs state=$state role=$roleHeld stale=$stale"
        )
    }

    fun recordCorruption() {
        corruption.incrementAndGet()
        Log.w(TAG, "native_store_corruption=true")
    }
}
