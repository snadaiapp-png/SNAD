package com.sanad.crm.callerid

import android.content.Intent
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * SanadCallScreeningService — G8 Track E ring path (G8 EXECUTION 05 §7, §15).
 *
 * ANDROID_RING_PATH = NATIVE ONLY (§3): no React Native bridge, no JS, no
 * network, no backend API, no DNS — even with Wi-Fi on (§16). The caller-ID
 * decision is resolved from the native projection cache and the call is
 * ALWAYS allowed (G8 is not a spam blocker — §12).
 *
 * Budget (§13): platform hard deadline 5 s; internal target ≤ 300 ms; hard
 * fallback ≤ 750 ms. `respondToCall(ALLOW)` is guaranteed inside the budget,
 * with the ORIGINAL call details, and exactly ONCE per callback.
 */
class SanadCallScreeningService : CallScreeningService() {

    private lateinit var projection: AndroidNativeCallerProjection

    private var pendingCall: Call.Details? = null
    private var responded = false
    private var startMs = 0L
    private var state = "UNKNOWN"
    private var stale = false

    override fun onCreate() {
        super.onCreate()
        projection = AndroidNativeCallerProjection(applicationContext)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        pendingCall = callDetails
        startMs = SystemClock.elapsedRealtime()
        responded = false
        state = "UNKNOWN"
        stale = projection.status().stale

        try {
            // 1. direction — only incoming calls (everything else ALLOW)
            //    getDirection() is public API 29+ but missing from some compile
            //    stubs; the platform only delivers CALLBACKS for incoming calls
            //    to a CallScreeningService holder anyway, so the reflective
            //    access is defensive only.
            if (directionOf(callDetails) != Call.Details.DIRECTION_INCOMING) {
                return allow()
            }

            // 2./3. TEL handle — null handle means the platform did not deliver
            //       a caller number (private/restricted/unknown presentation,
            //       G8-05 §35: ANDROID_PLATFORM_NOT_DELIVERED — not a SNAD bug).
            val handle = callDetails.handle ?: return allow()
            if (handle.scheme?.lowercase() != "tel") return allow()
            val rawNumber = handle.schemeSpecificPart
            if (rawNumber.isBlank()) return allow()

            // 4. active tenant binding — no tenant ⇒ nothing to identify
            val tenantId = projection.activeTenantId() ?: return allow()

            // 5. normalize (parity authority rules — SA hint, same as Track D)
            val normalized = KotlinPhoneNormalizer.normalizePhone(rawNumber, "SA")
            if (normalized == null) {
                state = "INVALID_NUMBER"
                return allow()
            }

            if (overBudget()) return allow()

            // 6. secure HMAC token — dataset key from the Keystore-wrapped store
            val datasetKey = projection.datasetKey()
            if (datasetKey == null) {
                RingMetrics.recordCorruption()
                return allow()
            }
            val token = KotlinHmac.hmacSha256Hex(datasetKey, normalized)

            if (overBudget()) return allow()

            // 7. indexed local lookup (active generation only)
            val candidates = projection.lookup(tenantId, token)

            if (overBudget()) return allow()

            // 8. resolve (tiered policy — no first-row-wins / fuzzy / random)
            val resolution = NativeCallerResolver.resolve(candidates)
            state = resolution.state.name

            // 9. minimal native caller-ID card (EXACT carries decrypted identity;
            //    AMBIGUOUS/UNKNOWN/RESTRICTED carry no identity by design §36–§37)
            dispatchCard(resolution)

            // 10. Track C boundary (§42): local RINGING observation only —
            //     flushed later by JS → POST /api/v2/crm/calls/events
            projection.queueRingingObservation(tenantId)
        } catch (t: Throwable) {
            RingMetrics.recordCorruption()
            Log.w(Tag, "ring_path_fallback=true cause=${t.javaClass.simpleName}")
        } finally {
            // ALWAYS allow the call (G8-05 §12); response is idempotent.
            allow()
        }
    }

    private fun dispatchCard(resolution: NativeCallerResolver.Resolution) {
        if (overBudget()) return
        val candidate = resolution.candidate
        val intent = Intent(this, SanadCallerIdActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(CallerIdConstants.STATE_EXTRAS, resolution.state.name)
            putExtra(CallerIdConstants.DISPLAY_NAME_EXTRA, candidate?.displayNameEncrypted)
            putExtra(CallerIdConstants.ACCOUNT_NAME_EXTRA, candidate?.accountNameEncrypted)
            putExtra(CallerIdConstants.PHONE_LABEL_EXTRA, candidate?.phoneLabel)
            putExtra(CallerIdConstants.VERIFIED_EXTRA, candidate?.verified ?: false)
            putExtra(CallerIdConstants.ENTITY_TYPE_EXTRA, candidate?.entityType)
            putExtra(CallerIdConstants.STALE_EXTRA, stale)
            putExtra(CallerIdConstants.PRIVACY_EXTRA, candidate?.privacyLevel)
            putExtra(CallerIdConstants.PROVISIONED_EXTRA, true)
        }
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            Log.w(Tag, "card_dispatch_skipped=true")
        }
    }

    /** Respond ALLOW with the ORIGINAL details — once per callback, always. */
    private fun allow() {
        if (responded) return
        responded = true
        val elapsedMs = SystemClock.elapsedRealtime() - startMs
        RingMetrics.record(state, roleHeld = true, stale = stale, elapsedMs = elapsedMs)
        val details = pendingCall ?: return
        try {
            respondToCall(
                details,
                CallResponse.Builder().setDisallowCall(false).setRejectCall(false).build()
            )
        } catch (t: Throwable) {
            Log.w(Tag, "respond_skipped=${t.javaClass.simpleName}")
        } finally {
            pendingCall = null
        }
    }

    private fun overBudget(): Boolean =
        SystemClock.elapsedRealtime() - startMs > CallerIdConstants.HARD_INTERNAL_FALLBACK_MS

    private fun directionOf(details: Call.Details): Int =
        try {
            details.javaClass.getMethod("getDirection").invoke(details) as Int
        } catch (t: Throwable) {
            Call.Details.DIRECTION_UNKNOWN
        }

    private companion object {
        const val Tag = "SanadCallScreening"
    }
}
