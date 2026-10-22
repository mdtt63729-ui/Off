package com.off.audioconverter.music

/** Where a track came from. */
enum class MusicSource(val label: String) {
    SAAVN("JioSaavn"),
    YOUTUBE("YouTube")
}

/**
 * A single playable/downloadable result, normalised across every source so the
 * rest of the app (player, downloads, library) does not care where it came from.
 */
data class Track(
    val id: String,
    val source: MusicSource,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    /** Direct playable URL when the source already exposes one (Saavn). */
    val streamUrl: String? = null,
    /** Source-specific identifier (Saavn id, YouTube video id). */
    val sourceId: String? = null,
    /** Canonical web page for the track. */
    val pageUrl: String? = null,
    val durationSec: Long = 0L,
    val bitrateKbps: Int? = null,
    val mimeType: String? = null
) {
    val subtitle: String get() = if (artist.isBlank()) source.label else artist
}

/** One source's slice of a combined search. */
data class SourceResult(
    val source: MusicSource,
    val tracks: List<Track>,
    val error: String? = null
)

/** A merged, cross-source search response. */
data class SearchResponse(
    val query: String,
    val results: List<SourceResult>
) {
    val tracks: List<Track> get() = results.flatMap { it.tracks }
    val hasAny: Boolean get() = results.any { it.tracks.isNotEmpty() }
}
