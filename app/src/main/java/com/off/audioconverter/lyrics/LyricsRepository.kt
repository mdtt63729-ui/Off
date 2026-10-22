package com.off.audioconverter.lyrics

import com.off.audioconverter.music.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Lyrics lookup.
 *
 * LRCLIB is the source: it is free, needs no API key, and is the one that is
 * essentially always up. It answers with whole-line LRC timing (and a plain
 * text fallback), which is what the player's karaoke sweep is driven from.
 *
 * The lookup is two-step: an exact `get` keyed on the name, artist, album and
 * duration, then a `search` if that misses — the same name-based matching every
 * lyrics host uses, so titles are cleaned first (see [forLyricsSearch]).
 */
object LyricsRepository {

    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun fetch(track: Track, durationSec: Long): Lyrics = withContext(Dispatchers.IO) {
        cache[track.id]?.let { return@withContext it }

        val name = track.title.forLyricsSearch()
        val artist = track.artist.artistForLyricsSearch()
        val album = track.album?.forLyricsSearch()
        val duration = durationSec.takeIf { it > 0 } ?: track.durationSec.takeIf { it > 0 } ?: 0L

        val lyrics = lookupExact(name, artist, album, duration)
            ?: lookupSearch(name, artist, duration)
            ?: Lyrics.EMPTY

        if (!lyrics.isEmpty) cache[track.id] = lyrics
        lyrics
    }

    private fun lookupExact(name: String, artist: String, album: String?, duration: Long): Lyrics? {
        val url = buildString {
            append(BASE).append("/api/get?track_name=").append(enc(name))
            append("&artist_name=").append(enc(artist))
            if (!album.isNullOrBlank()) append("&album_name=").append(enc(album))
            if (duration > 0) append("&duration=").append(duration)
        }
        val json = requestJson(url) as? JSONObject ?: return null
        return fromJson(json, "LRCLIB")
    }

    private fun lookupSearch(name: String, artist: String, duration: Long): Lyrics? {
        val url = buildString {
            append(BASE).append("/api/search?track_name=").append(enc(name))
            if (artist.isNotBlank()) append("&artist_name=").append(enc(artist))
        }
        val array = requestJson(url) as? JSONArray ?: return null
        val candidates = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        if (candidates.isEmpty()) return null

        // Prefer a synced answer, then the closest duration.
        val best = candidates
            .sortedWith(
                compareByDescending<JSONObject> { !it.optString("syncedLyrics", "").isNullOrBlank() }
                    .thenBy { if (duration > 0) kotlin.math.abs(it.optLong("duration", 0L) - duration) else 0L }
            )
            .firstOrNull()
            ?: return null
        return fromJson(best, "LRCLIB")
    }

    private fun fromJson(json: JSONObject, source: String): Lyrics? {
        if (json.optBoolean("instrumental", false)) {
            return Lyrics(emptyList(), synced = false, source = source, instrumental = true)
        }
        val syncedRaw = json.optString("syncedLyrics", "")
        if (syncedRaw.isNotBlank()) {
            val lines = LrcParser.parse(syncedRaw)
            if (lines.isNotEmpty()) return Lyrics(lines, synced = true, source = source)
        }
        val plainRaw = json.optString("plainLyrics", "")
        val plain = LrcParser.plain(plainRaw)
        return if (plain.isEmpty()) null else Lyrics(plain, synced = false, source = source)
    }

    private fun requestJson(url: String): Any? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        // LRCLIB asks callers to identify themselves.
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            val body = connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            if (body.isNullOrBlank()) return null
            val trimmed = body.trim()
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private const val BASE = "https://lrclib.net"
    private const val USER_AGENT = "Off-Android/3.2.0 (https://github.com/mdtt63729-ui/Off)"
}
