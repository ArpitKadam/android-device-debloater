package dev.redmi9i.grandline.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeatherParseTest {
    private val sample = """
        {"current":{"time":"2026-11-02T12:00","temperature_2m":28.4,"apparent_temperature":31.6,
         "relative_humidity_2m":62,"weather_code":2,"wind_speed_10m":9.4,"is_day":1},
         "daily":{"time":["2026-11-02","2026-11-03","2026-11-04","2026-11-05"],
          "weather_code":[2,61,95,0],"temperature_2m_max":[31.2,30.0,29.5,32.4],"temperature_2m_min":[22.1,21.9,22.5,21.0]}}
    """.trimIndent()

    @Test fun parsesCurrentConditionsAndRounds() {
        val w = WeatherParse.forecast(sample)!!
        assertEquals(28, w.tempC); assertEquals(32, w.feelsC)
        assertEquals(62, w.humidity); assertEquals(9, w.windKmh)
        assertEquals(2, w.code); assertEquals(true, w.isDay)
    }

    @Test fun parsesFourDays() {
        val d = WeatherParse.forecast(sample)!!.days
        assertEquals(4, d.size)
        assertEquals(LocalDate.of(2026, 11, 3), d[1].date)
        assertEquals(30, d[1].maxC); assertEquals(22, d[1].minC); assertEquals(61, d[1].code)
    }

    @Test fun garbageGivesNull() {
        assertNull(WeatherParse.forecast("not json"))
        assertNull(WeatherParse.forecast("""{"current":{}}"""))
    }

    @Test fun codesMapToSkies() {
        assertEquals(Sky.Clear, WeatherParse.describe(0, true).sky)
        assertEquals(Sky.Moon, WeatherParse.describe(0, false).sky)
        assertEquals(Sky.PartlyCloudy, WeatherParse.describe(2, true).sky)
        assertEquals(Sky.Rain, WeatherParse.describe(81, true).sky)
        assertEquals(Sky.Snow, WeatherParse.describe(75, true).sky)
        assertEquals(Sky.Storm, WeatherParse.describe(99, true).sky)
        assertEquals(Sky.Fog, WeatherParse.describe(48, true).sky)
        assertNotNull(WeatherParse.describe(1234, true).label) // unknown codes still read sensibly
    }

    @Test fun geocodingTakesTheFirstResult() {
        val json = """{"results":[{"name":"Pune","latitude":18.5196,"longitude":73.8553,"admin1":"Maharashtra","country_code":"IN"},{"name":"Pune","latitude":1.0,"longitude":2.0}]}"""
        val p = WeatherParse.place(json)!!
        assertEquals("Pune, Maharashtra", p.name)
        assertEquals(18.5196, p.lat, 0.0001)
    }

    @Test fun geocodingNoResultsOrNoRegion() {
        assertNull(WeatherParse.place("""{"generationtime_ms":0.5}"""))
        assertEquals("Atlantis", WeatherParse.place("""{"results":[{"name":"Atlantis","latitude":1,"longitude":2}]}""")!!.name)
    }

    @Test fun placeSurvivesStorage() {
        val p = Place("Pune, Maharashtra", 18.5, 73.8)
        assertEquals(p, Place.decode(p.encode()))
        assertNull(Place.decode("junk"))
        assertNull(Place.decode(null))
    }
}
