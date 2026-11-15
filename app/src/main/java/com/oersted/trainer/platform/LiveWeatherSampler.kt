package com.oersted.trainer.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import com.oersted.trainer.sim.LiveWeather
import com.oersted.trainer.sim.WxrWorld
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android side of liveWeather.ts: fetches the RainViewer frame list and tile, draws the tile into a 64x64 bitmap and
 * hands the pixels to the pure LiveWeather.analyze. Network or decode failures leave the previous cells in place, as the web does.
 * Not compiled in the sandbox (needs the Android SDK); verified by the GitHub Actions build.
 */
object LiveWeatherSampler {
    private const val MAPS_URL = "https://api.rainviewer.com/public/weather-maps.json"
    private const val DEFAULT_HOST = "https://tilecache.rainviewer.com"

    /** Returns the analysis, or null when any step fails (the caller keeps its previous weather). */
    fun sample(lat: Double, lon: Double, lastMetar: String): LiveWeather.Analysis? {
        return try {
        val maps = JSONObject(get(MAPS_URL) ?: return null)
        val frames = maps.optJSONObject("radar")?.optJSONArray("past") ?: return null
        if (frames.length() == 0) return null
        val latest = frames.getJSONObject(frames.length() - 1)
        val host = maps.optString("host", DEFAULT_HOST)
        val bytes = getBytes(LiveWeather.tileUrl(host, latest.getString("path"), lat, lon)) ?: return null
        val tile = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val small = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(tile, null, Rect(0, 0, 64, 64), null)
        val px = IntArray(64 * 64)
        small.getPixels(px, 0, 64, 0, 0, 64, 64)
        LiveWeather.analyze({ x, y ->
            val c = px[y * 64 + x]
            Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        }, lastMetar)
        } catch (e: Exception) {
            null
        }
    }

    private fun get(url: String): String? = getBytes(url)?.toString(Charsets.UTF_8)

    private fun getBytes(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
