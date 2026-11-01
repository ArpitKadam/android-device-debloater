package io.github.sumyabazar1.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IndianCitiesTest {

    @Test
    public void listIsAlphabeticalAndHasMumbaiWithRealCoordinates() {
        String previous = "";
        Forecast.Place mumbai = null;
        for (Forecast.Place p : IndianCities.ALL) {
            assertTrue(p.name + " after " + previous, p.name.compareToIgnoreCase(previous) >= 0);
            previous = p.name;
            // every city sits inside India's bounding box
            assertTrue(p.name, p.latitude > 6 && p.latitude < 37 && p.longitude > 68 && p.longitude < 98);
            if (p.name.equals("Mumbai")) mumbai = p;
        }
        assertEquals(19.08, mumbai.latitude, 0.01);
        assertEquals(72.88, mumbai.longitude, 0.01);
        assertTrue(IndianCities.ALL.size() > 100);
    }
}
