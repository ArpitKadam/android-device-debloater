package dev.redmi9i.grandline.weather

import dev.redmi9i.grandline.data.LayoutStore
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Open-Meteo (free, no key). Blocking calls: run them off the main thread. The last good answer is cached in preferences. */
class WeatherRepo(private val store: LayoutStore) {
    val place: Place? get() = Place.decode(store.weatherPlace)

    fun cached(): Weather? = store.weatherJson?.let { WeatherParse.forecast(it) }

    /** Older than 30 minutes, or never fetched. */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean = now - store.weatherAt > 30 * 60_000L

    /** Fetches and caches; returns null (and keeps the old cache) when offline or the answer is unreadable. */
    fun fetch(p: Place): Weather? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=4"
        val body = get(url) ?: return null
        val w = WeatherParse.forecast(body) ?: return null
        store.weatherJson = body
        store.weatherAt = System.currentTimeMillis()
        return w
    }

    /** Looks a city up by name and, if found, remembers it and drops the old cache. */
    fun setCity(name: String): Place? {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json") ?: return null
        val p = WeatherParse.place(body) ?: return null
        store.weatherPlace = p.encode()
        store.weatherJson = null
        store.weatherAt = 0
        return p
    }

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
    }.getOrNull()
}
