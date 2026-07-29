package app.olauncher.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Fetches current weather for the device location via Open-Meteo (no API key).
 * Display is monochrome text suitable for the home header (e.g. "72° Clear").
 */
object WeatherHelper {

    private const val TAG = "WeatherHelper"
    private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30)

    data class WeatherSnapshot(
        val temperatureC: Double,
        val weatherCode: Int,
        val latitude: Double,
        val longitude: Double,
        val fetchedAtMs: Long = System.currentTimeMillis(),
    )

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun cachedDisplayText(prefs: Prefs): String? {
        if (prefs.weatherCacheText.isBlank()) return null
        val age = System.currentTimeMillis() - prefs.weatherCacheFetchedAt
        if (age in 0 until CACHE_TTL_MS * 4) return prefs.weatherCacheText
        return prefs.weatherCacheText.ifBlank { null }
    }

    suspend fun refresh(context: Context, prefs: Prefs, force: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!force && prefs.weatherCacheText.isNotBlank() &&
                now - prefs.weatherCacheFetchedAt < CACHE_TTL_MS
            ) {
                return@withContext prefs.weatherCacheText
            }
            if (!hasLocationPermission(context)) {
                return@withContext prefs.weatherCacheText.ifBlank { null }
            }
            val location = lastKnownLocation(context)
            if (location == null) {
                return@withContext prefs.weatherCacheText.ifBlank { null }
            }
            val snapshot = fetchOpenMeteo(location.latitude, location.longitude)
            if (snapshot == null) {
                return@withContext prefs.weatherCacheText.ifBlank { null }
            }
            val text = formatMonochrome(snapshot, useCelsius = preferCelsius(context))
            prefs.weatherCacheText = text
            prefs.weatherCacheFetchedAt = snapshot.fetchedAtMs
            prefs.weatherCacheLat = snapshot.latitude.toFloat()
            prefs.weatherCacheLon = snapshot.longitude.toFloat()
            text
        }

    fun formatMonochrome(snapshot: WeatherSnapshot, useCelsius: Boolean): String {
        val temp = if (useCelsius) {
            snapshot.temperatureC.roundToInt()
        } else {
            (snapshot.temperatureC * 9.0 / 5.0 + 32.0).roundToInt()
        }
        val condition = conditionLabel(snapshot.weatherCode)
        return "$temp° $condition"
    }

    private fun preferCelsius(context: Context): Boolean {
        val country = context.resources.configuration.locales[0]?.country
            ?: Locale.getDefault().country
        // Imperial temperature countries
        return country !in setOf("US", "LR", "MM")
    }

    private fun conditionLabel(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Fair"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        in 85..86 -> "Snow"
        in 95..99 -> "Storm"
        else -> "—"
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var best: Location? = null
        for (provider in providers) {
            try {
                if (!lm.isProviderEnabled(provider)) continue
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.time > best.time) best = loc
            } catch (_: Exception) {
            }
        }
        return best
    }

    private fun fetchOpenMeteo(lat: Double, lon: Double): WeatherSnapshot? {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&timezone=auto"
        )
        return try {
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.requestMethod = "GET"
            connection.connect()
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Open-Meteo HTTP ${connection.responseCode}")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            WeatherSnapshot(
                temperatureC = current.getDouble("temperature_2m"),
                weatherCode = current.getInt("weather_code"),
                latitude = lat,
                longitude = lon,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather", e)
            null
        }
    }
}
