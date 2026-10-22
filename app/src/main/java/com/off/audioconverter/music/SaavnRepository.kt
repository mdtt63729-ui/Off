package com.off.audioconverter.music

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Unofficial JioSaavn API client.
 *
 * JioSaavn has no public developer API, so this talks to a community mirror. Several
 * public mirrors exist and any one of them can go away, so a small list is tried in
 * order and the first that answers wins. All calls must stay off the main thread.
 */
class SaavnRepository(
    private val baseUrls: List<String> = DEFAULT_BASES
) {
    companion object {
        val DEFAULT_BASES = listOf(
            "https://saavn.sumit.co/api",
            "https://jiosaavn-api.vercel.app/api",
            "https://saavn.dev/api"
        )
    }

    /** Returns matching tracks, or throws if every mirror failed. */
    fun search(query: String): List<Track> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(term, Charsets.UTF_8.name())
        var lastError: Exception? = null
        for (base in baseUrls) {
            try {
                val json = request("$base/search/songs?query=$encoded&limit=25")
                return parse(json)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("JioSaavn search is unavailable right now.")
    }

    private fun parse(json: JSONObject): List<Track> {
        val data = json.optJSONObject("data") ?: json
        val results = data.optJSONArray("results")
            ?: data.optJSONArray("songs")
            ?: return emptyList()
        val out = ArrayList<Track>(results.length())
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val downloads = item.optJSONArray("downloadUrl") ?: item.optJSONArray("download_url")
            var bestUrl: String? = null
            var quality: String? = null
            var bestKbps = -1
            if (downloads != null) {
                for (j in 0 until downloads.length()) {
                    val option = downloads.optJSONObject(j) ?: continue
                    val link = option.optString("url", option.optString("link", ""))
                    val q = option.optString("quality", "")
                    if (!link.startsWith("http")) continue
                    val kbps = q.filter { it.isDigit() }.toIntOrNull() ?: 0
                    if (kbps >= bestKbps) { bestKbps = kbps; bestUrl = link; quality = q }
                }
            }
            val images = item.optJSONArray("image")
            val artwork = if (images != null && images.length() > 0) {
                val last = images.optJSONObject(images.length() - 1)
                last?.optString("url")?.takeIf { it.startsWith("http") }
                    ?: images.optString(images.length() - 1).takeIf { it.startsWith("http") }
            } else {
                item.optString("image", "").takeIf { it.startsWith("http") }
            }
            val artists = item.optJSONObject("artists")?.optString("primary")
                ?: item.optString("primaryArtists", item.optString("artist", ""))
            val album = item.optJSONObject("album")?.optString("name") ?: item.optString("album", "")
            val id = item.optString("id", "").ifBlank { "saavn-${item.optString("name")}-$i" }
            val duration = item.optString("duration", "0").toLongOrNull() ?: 0L
            val stream = item.optString("url", "").takeIf { it.startsWith("http") }
            out += Track(
                id = "saavn:$id",
                source = MusicSource.SAAVN,
                title = clean(item.optString("name", "Untitled")),
                artist = clean(artists).ifBlank { "Unknown artist" },
                album = clean(album).ifBlank { null },
                artworkUrl = artwork,
                streamUrl = bestUrl ?: stream,
                sourceId = id,
                pageUrl = item.optString("url", "").takeIf { it.startsWith("http") },
                durationSec = duration,
                bitrateKbps = bestKbps.takeIf { it > 0 },
                mimeType = "audio/mpeg"
            )
        }
        return out
    }

    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Off-Android/3.0")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("JioSaavn returned HTTP $code")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun clean(value: String): String = value
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()
}
