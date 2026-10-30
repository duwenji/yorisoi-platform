package io.github.duwenji.yorisoi.kit.geo.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmTilesTest {
    @Test
    fun blockedOrBusyBecomesAMessageForTheUser() {
        assertEquals("OpenStreetMap の地図の利用を断られました", OsmTiles.errorOf(403).message)
        assertEquals("OpenStreetMap の地図が混み合っています", OsmTiles.errorOf(429).message)
        assertTrue(OsmTiles.errorOf(503).message!!.contains("HTTP 503"))
    }

    @Test
    fun aTileInBetweenStartsTheStreakAgain() {
        val streak = FailureStreak(limit = 3)
        assertFalse(streak.record(false))
        assertFalse(streak.record(false))
        assertFalse(streak.record(true))
        assertFalse(streak.record(false))
        assertFalse(streak.record(false))
        assertTrue(streak.record(false))
        // Reported once, then counted afresh.
        assertFalse(streak.record(false))
    }
}
