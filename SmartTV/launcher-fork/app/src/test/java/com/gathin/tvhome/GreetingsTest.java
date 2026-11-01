package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GreetingsTest {

    @Test
    public void everyHourHasAPhraseAndSpansStartOnTheirHour() {
        assertEquals("Burning the midnight oil", Greetings.forHour(0));
        assertEquals("In the dead of night", Greetings.forHour(1));
        assertEquals("In the dead of night", Greetings.forHour(3));
        assertEquals("At first light", Greetings.forHour(4));
        assertEquals("Rise and shine", Greetings.forHour(8));
        assertEquals("Good morning", Greetings.forHour(11));
        assertEquals("High noon", Greetings.forHour(12));
        assertEquals("Good afternoon", Greetings.forHour(15));
        assertEquals("Golden hour", Greetings.forHour(17));
        assertEquals("Good evening", Greetings.forHour(19));
        assertEquals("Settle in", Greetings.forHour(21));
        assertEquals("Good night", Greetings.forHour(23));
    }
}
