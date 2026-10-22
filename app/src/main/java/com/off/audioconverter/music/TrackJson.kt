package com.off.audioconverter.music

import org.json.JSONArray
import org.json.JSONObject

/** Small JSON codec so tracks can be persisted in SharedPreferences. */
object TrackJson {

    fun toJson(track: Track): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("source", track.source.name)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        put("artworkUrl", track.artworkUrl)
        put("streamUrl", track.streamUrl)
        put("sourceId", track.sourceId)
        put("pageUrl", track.pageUrl)
        put("durationSec", track.durationSec)
        put("bitrateKbps", track.bitrateKbps ?: -1)
        put("mimeType", track.mimeType)
    }

    fun fromJson(json: JSONObject): Track = Track(
        id = json.optString("id"),
        source = MusicSource.entries.firstOrNull { it.name == json.optString("source") } ?: MusicSource.YOUTUBE,
        title = json.optString("title"),
        artist = json.optString("artist"),
        album = json.optString("album").takeIf { it.isNotBlank() },
        artworkUrl = json.optString("artworkUrl").takeIf { it.isNotBlank() },
        streamUrl = json.optString("streamUrl").takeIf { it.isNotBlank() },
        sourceId = json.optString("sourceId").takeIf { it.isNotBlank() },
        pageUrl = json.optString("pageUrl").takeIf { it.isNotBlank() },
        durationSec = json.optLong("durationSec", 0L),
        bitrateKbps = json.optInt("bitrateKbps", -1).takeIf { it > 0 },
        mimeType = json.optString("mimeType").takeIf { it.isNotBlank() }
    )

    fun listToJson(tracks: List<Track>): String {
        val array = JSONArray()
        tracks.forEach { array.put(toJson(it)) }
        return array.toString()
    }

    fun listFromJson(raw: String?): List<Track> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { add(fromJson(it)) }
                }
            }
        }.getOrDefault(emptyList())
    }
}
