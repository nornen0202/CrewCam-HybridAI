package com.galaxycrew.crewcam

import org.junit.Assert.*
import org.junit.Test

class TeamRoundTest {
    private fun view(frame: Long, framing: String, safe: Boolean = true) =
        CameraView(frame, framing, safe, true, "ON_DEVICE", 5)

    private fun overlapping() = TeamRound(2, "orange ball").apply {
        observe(round, "A", view(101, "WIDE"))
        observe(round, "B", view(9001, "WIDE"))
    }
    private fun TeamRound.finishAssignments() {
        for (c in cameras) { assertTrue(accept(round, c)); assertTrue(complete(round, c)) }
        assertTrue(recheck())
    }
    @Test fun duplicateCoverageMovesOneCameraAndRequiresTwoFreshObservations() {
        val team = overlapping()
        assertEquals(listOf("HOLD", "TIGHTEN_CURRENT"), team.choices.map { it.adjustment })
        val old = team.round
        team.finishAssignments()
        assertFalse(team.observe(old, "A", view(109, "WIDE")))
        assertFalse(team.observe(team.round, "B", view(9001, "MEDIUM")))
        assertTrue(team.observe(team.round, "B", view(9010, "MEDIUM")))
        assertEquals(Phase.RECHECK, team.phase)
        assertTrue(team.observe(team.round, "A", view(106, "WIDE")))
        assertEquals(Phase.VERIFIED, team.phase)
    }
    @Test fun completeBeforeAcceptAndPartialCrewCannotOpenFreshCheck() {
        val team = overlapping()
        assertFalse(team.complete(team.round, "B"))
        team.accept(team.round, "A"); team.complete(team.round, "A")
        assertFalse(team.recheck()); assertFalse(team.accept(team.round, "C"))
    }
    @Test fun uncertainStaleDuplicateAndUnchangedFrameCannotWin() {
        val t = overlapping(); t.finishAssignments()
        t.observe(t.round, "A", view(102, "WIDE"))
        assertFalse(t.observe(t.round, "A", view(103, "CLOSE")))
        t.observe(t.round, "B", view(9010, "WIDE"))
        assertEquals(Phase.RETRY, t.phase)
        val unclear = TeamRound(2, "ball"); unclear.observe(unclear.round, "A", view(1, "UNCERTAIN"))
        assertEquals(Phase.RETRY, unclear.phase); assertTrue(unclear.choices.isEmpty())
    }
    @Test fun alreadyComplementaryViewsDoNotAskForMovement() {
        val t = TeamRound(2, "ball"); t.observe(t.round, "A", view(20, "WIDE"))
        t.observe(t.round, "B", view(5, "CLOSE"))
        assertEquals(listOf("HOLD", "HOLD"), t.choices.map { it.adjustment })
        assertEquals(Phase.ASSIGNED, t.phase)
    }
    @Test fun boundedCapacityRequiresAllPhysicalCameraSlotsInPolicy() {
        for (n in 2..8) {
            val t = TeamRound(n, "ball")
            t.cameras.forEachIndexed { i, c -> t.observe(t.round, c, view(1, if (i == 0) "WIDE" else "CLOSE")) }
            assertEquals(n, t.choices.size); assertEquals(Phase.ASSIGNED, t.phase)
            assertEquals(n - t.choices.map { it.framing }.distinct().size, t.choices.count { it.backup })
        }
        assertThrows(IllegalArgumentException::class.java) { TeamRound(9, "ball") }
    }
    @Test fun declineInvalidatesPendingReplies() {
        val t = overlapping(); t.fail("declined")
        assertFalse(t.accept(t.round, "A")); assertFalse(t.recheck())
        assertFalse(t.observe(t.round, "B", view(800, "MEDIUM")))
    }
}
