package com.sanad.crm.callerid

import android.os.Build

/** G8 Track E constants (G8 EXECUTION 05 §8, §13, §51). */
object CallerIdConstants {

    const val MODULE_NAME = "SanadCallScreening"

    /** Native caller ID requires Android 10+ (RoleManager) — §8. */
    val NATIVE_MIN_API = Build.VERSION_CODES.Q

    /** Internal decision targets (ms) — §13; platform hard deadline is 5 s. */
    const val TARGET_NATIVE_DECISION_MS = 300L
    const val HARD_INTERNAL_FALLBACK_MS = 750L
    const val PLATFORM_DEADLINE_MS = 5000L

    /** RSSI-free, no phone/tenant/customer labels in logs — §50. */
    const val SENTINEL_HANDLE_NULL = "PRESENTATION_UNKNOWN"

    const val NATIVE_STALE_THRESHOLD_MS = 24L * 60 * 60 * 1000

    const val STATE_EXTRAS = "sanad.callerid.state"
    const val DISPLAY_NAME_EXTRA = "sanad.callerid.displayName"
    const val ACCOUNT_NAME_EXTRA = "sanad.callerid.accountName"
    const val PHONE_LABEL_EXTRA = "sanad.callerid.phoneLabel"
    const val VERIFIED_EXTRA = "sanad.callerid.verified"
    const val ENTITY_TYPE_EXTRA = "sanad.callerid.entityType"
    const val STALE_EXTRA = "sanad.callerid.stale"
    const val PRIVACY_EXTRA = "sanad.callerid.privacy"
    const val PROVISIONED_EXTRA = "sanad.callerid.provisioned"
}
