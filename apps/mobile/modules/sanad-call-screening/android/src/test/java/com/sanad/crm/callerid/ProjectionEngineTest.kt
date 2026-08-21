package com.sanad.crm.callerid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generation atomicity + delta idempotence + tombstone + tenant isolation
 * (G8 EXECUTION 05 §24–§27). The SQLite layer applies these batches and
 * switches `current_generation` atomically in one transaction; this engine
 * defines the pure semantics under test.
 */
class ProjectionEngineTest {

    private fun record(
        token: String,
        tenant: String = "tenant-a",
        name: String? = "اسم",
        deleted: Boolean = false,
        entity: String = "CONTACT",
    ) = NativeProjectionInput(
        lookupToken = token,
        entityType = entity,
        entityId = token + "-id",
        displayName = name,
        accountName = null,
        phoneLabel = "mobile",
        verified = false,
        preferred = false,
        lifecycleStatus = "ACTIVE",
        privacyLevel = "PUBLIC",
        syncVersion = 1,
        updatedAt = "2026-08-21T00:00:00Z",
        deleted = deleted,
    )

    @Test
    fun sameDeltaAppliedTwiceProducesNoDuplicates() {
        val input = listOf(record("tok-1"), record("tok-2"))
        val gen1 = ProjectionEngine.buildGeneration("t", 18, input)
        val gen2 = ProjectionEngine.buildGeneration("t", 18, input)
        assertEquals(gen1.rows.size, gen2.rows.size)
        assertEquals(gen1.rows.map { it.key }.toSet(), gen2.rows.map { it.key }.toSet())
    }

    @Test
    fun tombstoneMarksRowAsDeleted() {
        val input = listOf(record("tok-1"), record("tok-2", deleted = true))
        val gen = ProjectionEngine.buildGeneration("t", 18, input)
        val active = ProjectionEngine.activeRows(gen)
        assertEquals(listOf("tok-1"), active.map { it.lookupToken })
    }

    @Test
    fun tenantIsolationKeepsTokensApart() {
        val a = ProjectionEngine.buildGeneration("tenant-a", 18, listOf(record("TOKEN-X")))
        val b = ProjectionEngine.buildGeneration("tenant-b", 18, listOf(record("TOKEN-X")))
        assertTrue(a.rows.all { it.tenantId == "tenant-a" })
        assertTrue(b.rows.all { it.tenantId == "tenant-b" })
        assertFalse(a.rows.any { it.tenantId == "tenant-b" })
    }

    @Test
    fun fullRebuildRequiredOnVersionMismatchOrCorruption() {
        assertTrue(ProjectionEngine.requiresFullRebuild(expectedVersion = 42, storedVersion = 41, corrupt = false))
        assertTrue(ProjectionEngine.requiresFullRebuild(expectedVersion = 42, storedVersion = 42, corrupt = true))
        assertFalse(ProjectionEngine.requiresFullRebuild(expectedVersion = 42, storedVersion = 42, corrupt = false))
    }

    @Test
    fun generationRowsCarryTheirGeneration() {
        val gen = ProjectionEngine.buildGeneration("t", 17, listOf(record("tok-1")))
        assertEquals(17L, gen.generation)
        assertTrue(gen.rows.all { it.datasetGeneration == 17L })
    }
}
