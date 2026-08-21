package com.sanad.crm.callerid

/**
 * Atomic dataset-generation + delta semantics for the native ring-time
 * projection (G8 EXECUTION 05 §24–§26). PURE Kotlin (JVM-testable).
 *
 * Model: rows are tagged with a `datasetGeneration`. A sync writes the NEW
 * generation, then commits by atomically switching `currentGeneration`;
 * rows of older generations are garbage-collected afterwards. A failed sync
 * leaves the previous generation untouched — no partial dataset is ever the
 * active one.
 */
object ProjectionEngine {

    data class Row(
        val tenantId: String,
        val lookupToken: String,
        val entityType: String,
        val entityId: String,
        val displayNameEncrypted: String?,
        val accountNameEncrypted: String?,
        val phoneLabel: String?,
        val verified: Boolean,
        val preferred: Boolean,
        val lifecycleStatus: String,
        val privacyLevel: String,
        val syncVersion: Int,
        val updatedAt: String,
        val deletedAt: String?,
        val datasetGeneration: Long,
    ) {
        val key: String get() = "$tenantId|$lookupToken|$entityType|$entityId"
        val isTombstone: Boolean get() = deletedAt != null
    }

    data class Batch(val generation: Long, val rows: List<Row>)

    /**
     * Merge Track D delta records into a new generation batch.
     * Idempotent per (tenant, token, entityType, entityId): the same delta
     * applied twice produces the same row set (G8-05 §25).
     */
    fun buildGeneration(
        tenantId: String,
        generation: Long,
        records: List<NativeProjectionInput>,
        tombstones: List<NativeProjectionInput> = emptyList(),
    ): Batch {
        val byKey = mutableMapOf<String, Row>()
        for (r in records + tombstones) {
            val deleted = r.deleted || tombstones.any { t -> t.sameKey(r) && t.deleted }
            byKey[r.key(tenantId)] = Row(
                tenantId = tenantId,
                lookupToken = r.lookupToken,
                entityType = r.entityType ?: "UNKNOWN",
                entityId = r.entityId ?: r.lookupToken,
                displayNameEncrypted = r.displayName,
                accountNameEncrypted = r.accountName,
                phoneLabel = r.phoneLabel,
                verified = r.verified,
                preferred = r.preferred,
                lifecycleStatus = r.lifecycleStatus ?: "ACTIVE",
                privacyLevel = r.privacyLevel ?: "PUBLIC",
                syncVersion = r.syncVersion,
                updatedAt = r.updatedAt,
                deletedAt = if (deleted) r.updatedAt else null,
                datasetGeneration = generation,
            )
        }
        return Batch(generation, byKey.values.toList())
    }

    /** Rows whose tombstone flag is set (kept so the lookup filters them). */
    fun activeRows(batch: Batch): List<Row> = batch.rows.filter { !it.isTombstone }

    /** True when a full rebuild is required (version mismatch / corrupt store). */
    fun requiresFullRebuild(expectedVersion: Int, storedVersion: Int, corrupt: Boolean): Boolean =
        corrupt || storedVersion != expectedVersion
}

/**
 * Bridge record describing one projection row coming from JS/Track D.
 * Identity fields arrive UNencrypted from the bridge; the SQLite layer
 * encrypts them at rest under the Keystore alias before persistence
 * (G8-05 §20, §23).
 */
data class NativeProjectionInput(
    val lookupToken: String,
    val entityType: String?,
    val entityId: String?,
    val displayName: String?,
    val accountName: String?,
    val phoneLabel: String?,
    val verified: Boolean,
    val preferred: Boolean,
    val lifecycleStatus: String?,
    val privacyLevel: String?,
    val syncVersion: Int,
    val updatedAt: String,
    val deleted: Boolean,
) {
    fun key(tenantId: String): String =
        "$tenantId|$lookupToken|${entityType ?: "UNKNOWN"}|${entityId ?: lookupToken}"

    fun sameKey(other: NativeProjectionInput): Boolean {
        val a = key("")
        val b = other.key("")
        return a == b
    }
}
