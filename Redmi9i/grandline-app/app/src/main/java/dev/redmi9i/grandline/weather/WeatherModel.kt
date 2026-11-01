package dev.redmi9i.grandline.weather

import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/** What the picture should show. */
enum class Sky { Clear, Moon, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Snow, Storm }

data class Condition(val sky: Sky, val label: String, val flavor: String)

data class Day(val date: LocalDate, val code: Int, val maxC: Int, val minC: Int)

data class Weather(
    val tempC: Int, val feelsC: Int, val code: Int, val isDay: Boolean,
    val humidity: Int, val windKmh: Int, val days: List<Day>,
)

data class Place(val name: String, val lat: Double, val lon: Double) {
    /** Stored in preferences as one string. */
    fun encode() = "$name|$lat|$lon"

    companion object {
        fun decode(s: String?): Place? {
            val p = s?.split('|') ?: return null
            if (p.size != 3) return null
            return Place(p[0], p[1].toDoubleOrNull() ?: return null, p[2].toDoubleOrNull() ?: return null)
        }
    }
}

/** Turns Open-Meteo JSON into the models above. All pure, so it is unit tested. */
object WeatherParse {
    /** WMO weather code to a sky, a label and a line of Grand Line flavour. */
    fun describe(code: Int, isDay: Boolean): Condition = when (code) {
        0, 1 -> if (isDay) Condition(Sky.Clear, "Clear", "Fair winds and open skies.")
                else Condition(Sky.Moon, "Clear night", "A calm night on the sea.")
        2 -> Condition(Sky.PartlyCloudy, "Partly cloudy", "Light clouds on the horizon.")
        3 -> Condition(Sky.Cloudy, "Overcast", "Grey skies over the Grand Line.")
        45, 48 -> Condition(Sky.Fog, "Fog", "Thick fog. Sail slow.")
        51, 53, 55, 56, 57 -> Condition(Sky.Drizzle, "Drizzle", "A light drizzle on deck.")
        61, 63, 65, 66, 67, 80, 81, 82 -> Condition(Sky.Rain, "Rain", "Rain on the deck. Batten down.")
        71, 73, 75, 77, 85, 86 -> Condition(Sky.Snow, "Snow", "Cold winds off Drum Island.")
        95, 96, 99 -> Condition(Sky.Storm, "Thunderstorm", "Storm brewing. Hold fast!")
        else -> Condition(Sky.Cloudy, "Unsettled", "The sea is hard to read today.")
    }

    fun forecast(json: String): Weather? = runCatching {
        val root = JSONObject(json)
        val cur = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val max = daily.getJSONArray("temperature_2m_max")
        val min = daily.getJSONArray("temperature_2m_min")
        val days = (0 until times.length()).map {
            Day(LocalDate.parse(times.getString(it)), codes.getInt(it), max.getDouble(it).roundToInt(), min.getDouble(it).roundToInt())
        }
        Weather(
            tempC = cur.getDouble("temperature_2m").roundToInt(),
            feelsC = cur.getDouble("apparent_temperature").roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            humidity = cur.optInt("relative_humidity_2m", 0),
            windKmh = cur.optDouble("wind_speed_10m", 0.0).roundToInt(),
            days = days,
        )
    }.getOrNull()

    /** First match from the geocoding API, labelled "City, Region". */
    fun place(json: String): Place? = runCatching {
        val r = JSONObject(json).optJSONArray("results")?.optJSONObject(0) ?: return null
        val label = listOf(r.getString("name"), r.optString("admin1")).filter { it.isNotBlank() }.joinToString(", ")
        Place(label, r.getDouble("latitude"), r.getDouble("longitude"))
    }.getOrNull()
}
