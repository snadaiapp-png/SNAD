package com.sanad.crm.callerid

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.TextView

/**
 * Minimal native caller-ID card (G8 EXECUTION 05 §36–§40). NOT the full
 * Track I post-call UI: it only renders identity fields that are ALREADY
 * decrypted by the service (EXACT), or an honest state marker otherwise.
 * No Customer360 / edit / actions — Track I later.
 *
 * Lock-screen minimum disclosure (§38): PUBLIC/INTERNAL → full card;
 * CONFIDENTIAL → masked display name; RESTRICTED → marker only
 * ("عميل محمي") — identity is not even stored for RESTRICTED (G8-03 §41).
 *
 * Lifecycle (§40): auto-closes after a short timeout or when the user
 * leaves/dismisses; never becomes a persistent UI.
 */
class SanadCallerIdActivity : Activity() {

    private val autoClose = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the lock screen without stealing input from the dialer.
        setShowWhenLocked(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        setContentView(R.layout.sanad_caller_id_card)

        val state = intent.getStringExtra(CallerIdConstants.STATE_EXTRAS) ?: "UNKNOWN"
        val privacy = intent.getStringExtra(CallerIdConstants.PRIVACY_EXTRA) ?: "PUBLIC"
        val locked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        val stale = intent.getBooleanExtra(CallerIdConstants.STALE_EXTRA, false)

        val nameView = findViewById<TextView>(R.id.sanad_cid_display_name)
        val accountView = findViewById<TextView>(R.id.sanad_cid_account_name)
        val labelView = findViewById<TextView>(R.id.sanad_cid_phone_label)
        val metaView = findViewById<TextView>(R.id.sanad_cid_meta)
        val stateView = findViewById<TextView>(R.id.sanad_cid_state)

        when (state) {
            "EXACT" -> {
                val name = intent.getStringExtra(CallerIdConstants.DISPLAY_NAME_EXTRA)
                val account = intent.getStringExtra(CallerIdConstants.ACCOUNT_NAME_EXTRA)
                val label = intent.getStringExtra(CallerIdConstants.PHONE_LABEL_EXTRA)
                val verified = intent.getBooleanExtra(CallerIdConstants.VERIFIED_EXTRA, false)

                val displayName = when {
                    privacy == "RESTRICTED" -> getString(R.string.sanad_cid_restricted_marker)
                    locked && privacy == "CONFIDENTIAL" -> maskName(name)
                    else -> name
                }
                nameView.text = displayName?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.sanad_cid_unknown_caller)
                accountView.text = if (locked && privacy == "CONFIDENTIAL") null else account
                labelView.text = if (locked && privacy == "CONFIDENTIAL") null else label
                stateView.text = getString(R.string.sanad_cid_state_exact)
                metaView.text = buildString {
                    if (verified) append(getString(R.string.sanad_cid_verified)).append(" · ")
                    if (stale) append(getString(R.string.sanad_cid_stale))
                }
            }
            "AMBIGUOUS" -> {
                nameView.text = getString(R.string.sanad_cid_ambiguous)
                stateView.text = getString(R.string.sanad_cid_state_ambiguous)
            }
            "RESTRICTED" -> {
                nameView.text = getString(R.string.sanad_cid_restricted_marker)
                stateView.text = getString(R.string.sanad_cid_state_restricted)
            }
            else -> { // UNKNOWN / INVALID_NUMBER / fallback
                nameView.text = getString(R.string.sanad_cid_unknown_caller)
                stateView.text = getString(R.string.sanad_cid_state_unknown)
            }
        }
        // The system incoming-call UI remains the primary surface; this card
        // only supplements it and closes itself.
        autoClose.postDelayed({ finish() }, AUTO_CLOSE_MS)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finish()
    }

    override fun onDestroy() {
        autoClose.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun maskName(name: String?): String? {
        if (name == null || name.isBlank()) return null
        return name.substring(0, 1) + getString(R.string.sanad_cid_mask_suffix)
    }

    private companion object {
        const val AUTO_CLOSE_MS = 12_000L
    }
}
