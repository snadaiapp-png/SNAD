package com.sanad.crm.callerid

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * Expo local module — G8 Track E bridge (G8 EXECUTION 05 §6).
 *
 * The JS side NEVER receives onScreenCall: role management, provisioning and
 * dataset seeding only. Ring-time resolution lives entirely in
 * SanadCallScreeningService.
 */
class SanadCallScreeningModule : Module() {

    private var projection: AndroidNativeCallerProjection? = null

    private fun context(): Context =
        appContext.reactContext ?: throw CodedException("React context lost")

    private fun store(): AndroidNativeCallerProjection =
        projection ?: AndroidNativeCallerProjection(context()).also { projection = it }

    private fun currentActivity(): Activity? = appContext.currentActivity

    override fun definition() = ModuleDefinition {
        Name(CallerIdConstants.MODULE_NAME)

        // ── API version gate (§8): Android 10+ only. Older installs report
        //    UNSUPPORTED — no undocumented fallback for API 24–28.
        Function("isSupported") {
            Build.VERSION.SDK_INT >= CallerIdConstants.NATIVE_MIN_API
        }

        Function("isRoleAvailable") {
            if (Build.VERSION.SDK_INT < CallerIdConstants.NATIVE_MIN_API) return@Function false
            runCatching {
                val rm = context().getSystemService(RoleManager::class.java)
                rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
            }.getOrDefault(false)
        }

        Function("isRoleHeld") {
            if (Build.VERSION.SDK_INT < CallerIdConstants.NATIVE_MIN_API) return@Function false
            runCatching {
                val rm = context().getSystemService(RoleManager::class.java)
                rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            }.getOrDefault(false)
        }

        // Opens the system consent screen (user action REQUIRED — §9).
        AsyncFunction("requestCallScreeningRole") {
            if (Build.VERSION.SDK_INT < CallerIdConstants.NATIVE_MIN_API) return@AsyncFunction false
            val rm = context().getSystemService(RoleManager::class.java)
            if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return@AsyncFunction false
            if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) return@AsyncFunction true
            val activity = currentActivity()
            if (activity == null) {
                val intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context().startActivity(intent)
            } else {
                activity.startActivityForResult(
                    rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                    REQUEST_CALL_SCREENING_ROLE,
                )
            }
            true
        }

        AsyncFunction("getNativeCallerDatasetStatus") {
            store().status().toMap()
        }

        AsyncFunction("syncNativeCallerDataset") Coroutine {
            tenantId: String,
            datasetVersion: Int,
            generation: Long,
            datasetKey: String,
            records: List<Map<String, Any?>>,
            ->
            val inputs = records.map { it.toProjectionInput() }
            val batch = ProjectionEngine.buildGeneration(tenantId, generation, inputs)
            val rows = batch.rows.map { r ->
                // Encrypt identity at rest BEFORE persistence (Keystore AES-GCM, §23).
                r.copy(
                    displayNameEncrypted = r.displayNameEncrypted?.let { NativeCrypto.encrypt(it) },
                    accountNameEncrypted = r.accountNameEncrypted?.let { NativeCrypto.encrypt(it) },
                )
            }
            store().seedDataset(tenantId, datasetVersion, generation, datasetKey, rows)
            store().status().toMap()
        }

        AsyncFunction("purgeNativeCallerDataset") { tenantId: String? ->
            val rows = store().purge(tenantId)
            val keyDeleted = tenantId == null || store().activeTenantId() == null
            if (keyDeleted) store().deleteKeystoreAliases()
            mapOf(
                "purgedTenantId" to tenantId,
                "rowsDeleted" to rows,
                "keyAliasDeleted" to keyDeleted,
                "observationsDeleted" to 0,
            )
        }

        AsyncFunction("takePendingCallObservations") {
            store().takePendingObservations()
        }

        AsyncFunction("markObservationsFlushed") { ids: List<String> ->
            store().markObservationsFlushed(ids)
        }
    }

    companion object {
        const val REQUEST_CALL_SCREENING_ROLE = 8751
        private const val TAG = "SanadCallScreeningModule"

        fun AndroidNativeCallerProjection.Status.toMap(): Map<String, Any?> = mapOf(
            "supported" to (Build.VERSION.SDK_INT >= CallerIdConstants.NATIVE_MIN_API),
            "provisioned" to provisioned,
            "activeTenantId" to activeTenantId,
            "datasetVersion" to datasetVersion,
            "currentGeneration" to currentGeneration,
            "entryCount" to entryCount,
            "keyWrapped" to keyWrapped,
            "stale" to stale,
            "corrupt" to corrupt,
            "fullResyncSuggested" to corrupt,
        )

        fun Map<String, Any?>.toProjectionInput(): NativeProjectionInput = NativeProjectionInput(
            lookupToken = this["lookupToken"] as? String ?: "",
            entityType = this["entityType"] as? String,
            entityId = this["entityId"] as? String,
            displayName = this["displayName"] as? String,
            accountName = this["accountName"] as? String,
            phoneLabel = this["phoneLabel"] as? String,
            verified = (this["verified"] as? Boolean) ?: false,
            preferred = (this["preferred"] as? Boolean) ?: false,
            lifecycleStatus = this["lifecycleStatus"] as? String,
            privacyLevel = this["privacyLevel"] as? String,
            syncVersion = (this["syncVersion"] as? Number)?.toInt() ?: 0,
            updatedAt = this["updatedAt"] as? String ?: "",
            deleted = (this["deleted"] as? Boolean) ?: false,
        )
    }
}
