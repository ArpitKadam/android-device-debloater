package dev.redmi9i.grandline.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewTest {
    @Test fun sameDayGivesSameMemberAndDaysCycle() {
        assertEquals(Crew.ofDay(20000), Crew.ofDay(20000))
        assertEquals(Crew.ofDay(20000), Crew.ofDay(20000L + Crew.members.size))
        assertNotEquals(Crew.ofDay(20000), Crew.ofDay(20001))
    }

    @Test fun lockNeverRepeatsTheLastPosterForAnyRoll() {
        for (last in Crew.members) for (roll in -25..25) assertNotEquals(last, Crew.nextFor(last, roll))
    }

    @Test fun lockCoversEveryoneElseAndWorksWithNoHistory() {
        val seen = (0 until 9).map { Crew.nextFor("Zoro", it) }.toSet()
        assertEquals(Crew.members.size - 1, seen.size)
        assertTrue(Crew.nextFor(null, -7) in Crew.members)
    }

    @Test fun labelReadsNaturally() = assertEquals("Tony Tony Chopper", Crew.label("Tony_Tony_Chopper"))
}
