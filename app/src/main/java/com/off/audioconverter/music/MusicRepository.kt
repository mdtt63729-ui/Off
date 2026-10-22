package com.off.audioconverter.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Fans a single query out to every enabled source at once and merges the results,
 * the way a "one search box" music app does.
 */
class MusicRepository(
    private val saavn: SaavnRepository = SaavnRepository(),
    private val youtube: YouTubeRepository = YouTubeRepository()
) {

    suspend fun search(
        query: String,
        sources: Set<MusicSource> = MusicSource.entries.toSet()
    ): SearchResponse = withContext(Dispatchers.IO) {
        coroutineScope {
            val jobs = sources.map { source ->
                source to async {
                    runCatching {
                        when (source) {
                            MusicSource.SAAVN -> saavn.search(query)
                            MusicSource.YOUTUBE -> youtube.search(query)
                        }
                    }
                }
            }
            val results = jobs.map { (source, job) ->
                val outcome = job.await()
                SourceResult(
                    source = source,
                    tracks = outcome.getOrDefault(emptyList()),
                    error = outcome.exceptionOrNull()?.let { friendly(it, source) }
                )
            }
            SearchResponse(query = query.trim(), results = results)
        }
    }

    /** Resolves a track into a directly playable/downloadable audio resource. */
    suspend fun resolveStream(track: Track): ResolvedAudio = withContext(Dispatchers.IO) {
        val direct = track.streamUrl
        if (!direct.isNullOrBlank()) {
            ResolvedAudio(
                url = direct,
                mimeType = track.mimeType ?: "audio/mpeg",
                extension = extensionFor(track.mimeType),
                bitrateKbps = track.bitrateKbps ?: 0
            )
        } else {
            when (track.source) {
                MusicSource.YOUTUBE -> youtube.resolveAudio(track)
                MusicSource.SAAVN -> error("This JioSaavn result has no direct stream available.")
            }
        }
    }

    private fun extensionFor(mimeType: String?): String = when {
        mimeType == null -> "mp3"
        mimeType.contains("mpeg") -> "mp3"
        mimeType.contains("mp4") || mimeType.contains("aac") -> "m4a"
        mimeType.contains("webm") || mimeType.contains("opus") -> "webm"
        mimeType.contains("ogg") -> "ogg"
        else -> "mp3"
    }

    private fun friendly(error: Throwable, source: MusicSource): String = when {
        error.message.isNullOrBlank() -> "${source.label} is unavailable right now."
        else -> "${source.label}: ${error.message}"
    }
}
