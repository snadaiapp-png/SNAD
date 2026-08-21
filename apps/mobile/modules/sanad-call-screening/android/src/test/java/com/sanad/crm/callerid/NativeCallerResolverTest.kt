package com.sanad.crm.callerid

import com.sanad.crm.callerid.NativeCallerResolver.Candidate
import com.sanad.crm.callerid.NativeCallerResolver.MatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MATCHING_PARITY (G8 EXECUTION 05 §34): native resolver mirrors the backend
 * tiered policy — verified CONTACT > preferred CONTACT > other CONTACT >
 * ACCOUNT > other; EXACT single / AMBIGUOUS multiple / RESTRICTED marker-only /
 * UNKNOWN nothing. NO first-row-wins, NO fuzzy, NO suffix match.
 */
class NativeCallerResolverTest {

    private fun contact(verified: Boolean, preferred: Boolean, id: String = java.util.UUID.randomUUID().toString()) =
        Candidate("CONTACT", id, "enc1", "enc2", "mobile", verified, preferred, "PUBLIC")

    private fun account(id: String = java.util.UUID.randomUUID().toString()) =
        Candidate("ACCOUNT", id, "encA", "encB", "work", true, false, "INTERNAL")

    private fun restrictedContact() =
        Candidate("CONTACT", "r1", null, null, null, true, true, "RESTRICTED")

    @Test
    fun exactSingleBestCandidate() {
        val r = NativeCallerResolver.resolve(listOf(contact(false, false), contact(true, false)))
        assertEquals(MatchState.EXACT, r.state)
        assertEquals(1, r.candidateCount)
        assertEquals(true, r.candidate?.verified)
    }

    @Test
    fun verifiedContactBeatsPreferredContact() {
        val r = NativeCallerResolver.resolve(listOf(contact(false, true), contact(true, false)))
        assertEquals(MatchState.EXACT, r.state)
        assertEquals(true, r.candidate?.verified)
    }

    @Test
    fun ambiguousWhenTwoBestTied() {
        val r = NativeCallerResolver.resolve(listOf(contact(true, false, "a"), contact(true, false, "b")))
        assertEquals(MatchState.AMBIGUOUS, r.state)
        assertEquals(2, r.candidateCount)
        assertNull(r.candidate)
    }

    @Test
    fun accountBeatsLeadButLosesToContact() {
        val r = NativeCallerResolver.resolve(listOf(account(), Candidate("LEAD", "l1", null, null, null, true, true, "PUBLIC")))
        assertEquals(MatchState.EXACT, r.state)
        assertEquals("ACCOUNT", r.candidate?.entityType)
    }

    @Test
    fun restrictedTaggedMarkerNeverLeaksIdentity() {
        val r = NativeCallerResolver.resolve(listOf(restrictedContact(), contact(false, false)))
        assertEquals(MatchState.RESTRICTED, r.state)
        assertNull(r.candidate)
    }

    @Test
    fun unknownWhenNothingMatches() {
        val r = NativeCallerResolver.resolve(emptyList())
        assertEquals(MatchState.UNKNOWN, r.state)
        assertEquals(0, r.candidateCount)
        assertNull(r.candidate)
    }

    @Test
    fun noFirstRowWinsOnTiedInputOrder() {
        val a = contact(true, false, "a")
        val b = contact(true, false, "b")
        val r1 = NativeCallerResolver.resolve(listOf(a, b))
        val r2 = NativeCallerResolver.resolve(listOf(b, a))
        assertEquals(MatchState.AMBIGUOUS, r1.state)
        assertEquals(r1.candidateCount, r2.candidateCount)
    }

    @Test
    fun duplicatesAreCollapsed() {
        val dup = contact(true, false, "same")
        val r = NativeCallerResolver.resolve(listOf(dup, dup))
        assertEquals(MatchState.EXACT, r.state)
    }
}
