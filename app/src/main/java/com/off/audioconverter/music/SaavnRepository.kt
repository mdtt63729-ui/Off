package com.off.audioconverter.music

import org.json.JSONArray
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
            "https://saavn.dev/api",
            "https://jiosaavn-api.vercel.app/api"
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
                val parsed = parse(json)
                if (parsed.isNotEmpty()) return parsed
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (lastError != null) throw lastError
        return emptyList()
    }

    private fun parse(json: JSONObject): List<Track> {
        val data = json.optJSONObject("data") ?: json
        val results = data.optJSONArray("results")
            ?: data.optJSONArray("songs")
            ?: return emptyList()
        val out = ArrayList<Track>(results.length())
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue

            var bestUrl: String? = null
            var bestKbps = -1
            val downloads = item.optJSONArray("downloadUrl") ?: item.optJSONArray("download_url")
            if (downloads != null) {
                for (j in 0 until downloads.length()) {
                    val option = downloads.optJSONObject(j) ?: continue
                    val link = option.optString("url", option.optString("link", ""))
                    if (!link.startsWith("http")) continue
                    val q = option.optString("quality", "")
                    val kbps = q.filter { it.isDigit() }.toIntOrNull() ?: 0
                    if (kbps >= bestKbps) { bestKbps = kbps; bestUrl = link }
                }
            }

            val id = item.optString("id", "").ifBlank { "saavn-${item.optString("name")}-$i" }
            val direct = item.optString("url", "").takeIf { it.startsWith("http") }
            val duration = item.optString("duration", "0").toLongOrNull() ?: 0L

            out += Track(
                id = "saavn:$id",
                source = MusicSource.SAAVN,
                title = clean(item.optString("name", "Untitled")),
                artist = artistNames(item).ifBlank { "Unknown artist" },
                album = albumName(item).ifBlank { null },
                artworkUrl = artwork(item),
                streamUrl = bestUrl ?: direct,
                sourceId = id,
                pageUrl = item.optString("url", "").takeIf { it.startsWith("http") },
                durationSec = duration,
                bitrateKbps = bestKbps.takeIf { it > 0 },
                mimeType = "audio/mpeg"
            )
        }
        return out
    }

    /**
     * `artists` is an object whose `primary`/`featured`/`all` entries are *arrays of
     * artist objects* — reading them as strings is what produced raw JSON in the UI.
     */
    private fun artistNames(item: JSONObject): String {
        val artists = item.optJSONObject("artists")
        if (artists != null) {
            for (key in listOf("primary", "all", "featured")) {
                val array = artists.optJSONArray(key)
                if (array != null && array.length() > 0) {
                    val names = LinkedHashSet<String>()
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i)
                        val name = obj?.optString("name")?.takeIf { it.isNotBlank() }
                            ?: array.optString(i).takeIf { !it.startsWith("{") && !it.startsWith("[") }
                        if (!name.isNullOrBlank()) names += clean(name)
                    }
                    if (names.isNotEmpty()) return names.joinToString(", ")
                }
                val flat = artists.optString(key, "")
                if (flat.isNotBlank() && !flat.startsWith("[") && !flat.startsWith("{")) return clean(flat)
            }
        }
        for (key in listOf("primaryArtists", "artist", "singers", "subtitle")) {
            val flat = item.optString(key, "")
            if (flat.isNotBlank() && !flat.startsWith("[") && !flat.startsWith("{")) return clean(flat)
        }
        return ""
    }

    private fun albumName(item: JSONObject): String {
        item.optJSONObject("album")?.optString("name")?.takeIf { it.isNotBlank() }?.let { return clean(it) }
        val flat = item.optString("album", "")
        if (flat.isNotBlank() && !flat.startsWith("{") && !flat.startsWith("[")) return clean(flat)
        return ""
    }

    private fun artwork(item: JSONObject): String? {
        val images = item.optJSONArray("image")
        if (images != null && images.length() > 0) {
            for (i in images.length() - 1 downTo 0) {
                val obj = images.optJSONObject(i)
                val url = obj?.optString("url") ?: images.optString(i)
                if (url.startsWith("http")) return url
            }
        }
        return item.optString("image", "").takeIf { it.startsWith("http") }
    }

    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Off-Android/3.1")
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
        .replace(Regex("\\s+"), " ")
        .trim()

    @Suppress("unused")
    private fun emptyArray() = JSONArray()
}
