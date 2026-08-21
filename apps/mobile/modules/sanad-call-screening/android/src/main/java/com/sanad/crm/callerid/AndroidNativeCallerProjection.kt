package com.sanad.crm.callerid

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/**
 * Native ring-time projection store — a DERIVED CACHE of the Track D caller
 * dataset (G8 EXECUTION 05 §18–§21). Source of truth remains Track D; this
 * store is never synced independently (no native server sync, §19).
 *
 * Design:
 *  - rows carry `dataset_generation`; the active generation is switched
 *    atomically in a transaction (§24) so a half-written dataset is never
 *    visible to the ring path;
 *  - lookups are INDEXED on (tenant_id, phone_lookup_token) — no scan, no
 *    decrypt-all (§21);
 *  - display/account names are encrypted at rest under Keystore AES-GCM (§23);
 *  - the Track D dataset HMAC key is stored WRAPPED by Keystore (§22);
 *  - a RINGING observation table buffers Track C events for the JS flush
 *    (§42 — never network-posted inside onScreenCall).
 */
@Suppress("SameParameterValue")
class AndroidNativeCallerProjection(context: Context) {

    private val dbHelper = Db(context)

    val isCorrupt: Boolean get() = _corrupt
    private var _corrupt = false

    // ── meta keys ──────────────────────────────────────────────────────────
    companion object {
        const val META_ACTIVE_TENANT = "active_tenant_id"
        const val META_DATASET_VERSION = "dataset_version"
        const val META_CURRENT_GENERATION = "current_generation"
        const val META_WRAPPED_DATASET_KEY = "wrapped_dataset_key"
        const val META_LAST_SYNCED_AT = "native_last_synced_at"
        const val OBSERVATION_PREFIX = "ANDROID_SCREENING_"
        const val DB_NAME = "snad_native_caller_projection.db"
    }

    data class Status(
        val provisioned: Boolean,
        val activeTenantId: String?,
        val datasetVersion: Int,
        val currentGeneration: Long,
        val entryCount: Int,
        val keyWrapped: Boolean,
        val stale: Boolean,
        val corrupt: Boolean,
    )

    data class LookupHit(
        val entityType: String?,
        val entityId: String?,
        val displayNameEncrypted: String?,
        val accountNameEncrypted: String?,
        val phoneLabel: String?,
        val verified: Boolean,
        val preferred: Boolean,
        val privacyLevel: String?,
    )

    private class Db(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE native_caller_projection (
                  tenant_id TEXT NOT NULL,
                  phone_lookup_token TEXT NOT NULL,
                  entity_type TEXT NOT NULL,
                  entity_id TEXT NOT NULL,
                  display_name_enc TEXT,
                  account_name_enc TEXT,
                  phone_label TEXT,
                  verified INTEGER NOT NULL DEFAULT 0,
                  preferred INTEGER NOT NULL DEFAULT 0,
                  lifecycle_status TEXT NOT NULL,
                  privacy_level TEXT NOT NULL,
                  sync_version INTEGER NOT NULL,
                  updated_at TEXT NOT NULL,
                  deleted_at TEXT,
                  dataset_generation INTEGER NOT NULL,
                  PRIMARY KEY (tenant_id, phone_lookup_token, entity_type, entity_id)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX idx_native_caller_token ON native_caller_projection(tenant_id, phone_lookup_token)"
            )
            db.execSQL("CREATE TABLE native_caller_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL(
                """
                CREATE TABLE native_call_observations (
                  id TEXT PRIMARY KEY,
                  tenant_id TEXT,
                  status TEXT NOT NULL,
                  occurred_at TEXT NOT NULL,
                  flushed INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    // ── provisioning / dataset seeding (JS bridge → Track D) ──────────────
    fun seedDataset(
        tenantId: String,
        datasetVersion: Int,
        generation: Long,
        datasetKey: String,
        rows: List<ProjectionEngine.Row>,
    ) {
        val wrapped = NativeCrypto.wrapDatasetKey(datasetKey)
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // 1. write the new generation
            for (r in rows) {
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO native_caller_projection (
                      tenant_id, phone_lookup_token, entity_type, entity_id,
                      display_name_enc, account_name_enc, phone_label, verified, preferred,
                      lifecycle_status, privacy_level, sync_version, updated_at, deleted_at,
                      dataset_generation
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """.trimIndent(),
                    arrayOf(
                        r.tenantId, r.lookupToken, r.entityType, r.entityId,
                        r.displayNameEncrypted, r.accountNameEncrypted, r.phoneLabel,
                        if (r.verified) 1 else 0, if (r.preferred) 1 else 0,
                        r.lifecycleStatus, r.privacyLevel, r.syncVersion, r.updatedAt,
                        r.deletedAt, r.datasetGeneration,
                    ),
                )
            }
            // 2. atomically commit the new active generation
            putMetaTx(db, META_ACTIVE_TENANT, tenantId)
            putMetaTx(db, META_DATASET_VERSION, datasetVersion.toString())
            putMetaTx(db, META_CURRENT_GENERATION, generation.toString())
            putMetaTx(db, META_WRAPPED_DATASET_KEY, wrapped)
            putMetaTx(db, META_LAST_SYNCED_AT, System.currentTimeMillis().toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // 3. garbage-collect older generations (outside the commit window)
        db.execSQL(
            "DELETE FROM native_caller_projection WHERE dataset_generation < ?",
            arrayOf(generation.toString()),
        )
    }

    fun status(): Status {
        val db = dbHelper.readableDatabase
        return try {
            val tenant = getMeta(META_ACTIVE_TENANT)
            val version = getMeta(META_DATASET_VERSION)?.toIntOrNull() ?: 0
            val generation = getMeta(META_CURRENT_GENERATION)?.toLongOrNull() ?: 0L
            val wrapped = getMeta(META_WRAPPED_DATASET_KEY) != null
            val count = db.rawQuery(
                "SELECT COUNT(*) FROM native_caller_projection WHERE dataset_generation = ?",
                arrayOf(generation.toString()),
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            val lastSynced = getMeta(META_LAST_SYNCED_AT)?.toLongOrNull() ?: 0L
            val stale = System.currentTimeMillis() - lastSynced >
                CallerIdConstants.NATIVE_STALE_THRESHOLD_MS
            Status(
                provisioned = tenant != null && wrapped,
                activeTenantId = tenant,
                datasetVersion = version,
                currentGeneration = generation,
                entryCount = count,
                keyWrapped = wrapped,
                stale = stale,
                corrupt = _corrupt,
            )
        } catch (t: Throwable) {
            _corrupt = true
            Status(false, null, 0, 0L, 0, false, true, true)
        }
    }

    fun datasetKey(): String? {
        val wrapped = getMeta(META_WRAPPED_DATASET_KEY) ?: return null
        return try {
            NativeCrypto.unwrapDatasetKey(wrapped)
        } catch (t: Throwable) {
            _corrupt = true
            null
        }
    }

    fun activeTenantId(): String? = getMeta(META_ACTIVE_TENANT)

    /**
     * Indexed ring-time lookup — (tenant_id, phone_lookup_token) on the ACTIVE
     * generation only. Returns decrypted-identity candidates for non-RESTRICTED
     * rows; RESTRICTED rows resolve by token with no identity.
     */
    fun lookup(tenantId: String, token: String): List<NativeCallerResolver.Candidate> {
        val db = dbHelper.readableDatabase
        val generation = getMeta(META_CURRENT_GENERATION)?.toLongOrNull() ?: return emptyList()
        return try {
            db.rawQuery(
                """
                SELECT entity_type, entity_id, display_name_enc, account_name_enc,
                       phone_label, verified, preferred, privacy_level
                FROM native_caller_projection
                WHERE tenant_id = ? AND phone_lookup_token = ?
                  AND dataset_generation = ? AND deleted_at IS NULL
                """.trimIndent(),
                arrayOf(tenantId, token, generation.toString()),
            ).use { c ->
                val out = ArrayList<NativeCallerResolver.Candidate>()
                while (c.moveToNext()) {
                    val privacy = c.getString(7)
                    val restricted = privacy == "RESTRICTED"
                    out.add(
                        NativeCallerResolver.Candidate(
                            entityType = c.getString(0),
                            entityId = c.getString(1),
                            displayNameEncrypted = if (restricted) null else decryptOrNull(c.getString(2)),
                            accountNameEncrypted = if (restricted) null else decryptOrNull(c.getString(3)),
                            phoneLabel = if (restricted) null else c.getString(4),
                            verified = c.getInt(5) == 1,
                            preferred = c.getInt(6) == 1,
                            privacyLevel = privacy,
                        )
                    )
                }
                out
            }
        } catch (t: Throwable) {
            _corrupt = true
            emptyList()
        }
    }

    fun queueRingingObservation(tenantId: String?) {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "INSERT OR IGNORE INTO native_call_observations (id, tenant_id, status, occurred_at, flushed) VALUES (?,?,?,?,0)",
            arrayOf(
                OBSERVATION_PREFIX + UUID.randomUUID(),
                tenantId,
                "RINGING",
                java.time.Instant.now().toString(),
            ),
        )
    }

    fun takePendingObservations(): List<Map<String, String?>> {
        val db = dbHelper.readableDatabase
        return db.rawQuery(
            "SELECT id, tenant_id, status, occurred_at FROM native_call_observations WHERE flushed = 0",
            null,
        ).use { c ->
            val out = mutableListOf<Map<String, String?>>()
            while (c.moveToNext()) {
                out.add(
                    mapOf(
                        "id" to c.getString(0),
                        "tenantId" to c.getString(1),
                        "status" to c.getString(2),
                        "occurredAt" to c.getString(3),
                    )
                )
            }
            out
        }
    }

    fun markObservationsFlushed(ids: List<String>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                db.execSQL("UPDATE native_call_observations SET flushed = 1 WHERE id = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Purge per tenant (tenant switch) or complete (logout/revoke). */
    fun purge(tenantId: String?): Int {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val rows = if (tenantId == null) {
                db.delete("native_caller_projection", null, null)
            } else {
                db.delete("native_caller_projection", "tenant_id = ?", arrayOf(tenantId))
            }
            db.delete("native_call_observations", null, null)
            if (tenantId == null || tenantId == getMeta(META_ACTIVE_TENANT)) {
                db.delete("native_caller_meta", null, null)
            }
            db.setTransactionSuccessful()
            return rows
        } finally {
            db.endTransaction()
            _corrupt = false
        }
    }

    fun deleteKeystoreAliases() {
        NativeCrypto.deleteAliases()
    }

    // ── helpers ────────────────────────────────────────────────────────────
    private fun putMetaTx(db: SQLiteDatabase, key: String, value: String) {
        db.execSQL(
            "INSERT OR REPLACE INTO native_caller_meta (key, value) VALUES (?,?)",
            arrayOf(key, value),
        )
    }

    private fun getMeta(key: String): String? {
        val db = dbHelper.readableDatabase
        return db.rawQuery("SELECT value FROM native_caller_meta WHERE key = ?", arrayOf(key))
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    private fun decryptOrNull(payload: String?): String? {
        if (payload == null) return null
        return try {
            NativeCrypto.decrypt(payload)
        } catch (t: Throwable) {
            _corrupt = true
            null
        }
    }
}
