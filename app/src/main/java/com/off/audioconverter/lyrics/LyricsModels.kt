package com.off.audioconverter.lyrics

/** One line of lyrics and the stretch of the song it is sung over. */
data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(1L)

    /** How far through the line [positionMs] is, 0f..1f. Drives the karaoke sweep. */
    fun progressAt(positionMs: Long): Float =
        ((positionMs - startMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * Lyrics for a track, either time-synced (whole lines) or plain text.
 * [source] is shown under the lyrics so the user knows who answered.
 */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String,
    val instrumental: Boolean = false
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Index of the line being sung at [positionMs], or -1 before the first. */
    fun activeIndex(positionMs: Long): Int {
        if (lines.isEmpty() || !synced) return -1
        var low = 0
        var high = lines.size - 1
        var answer = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].startMs <= positionMs) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }

    companion object {
        val EMPTY = Lyrics(emptyList(), synced = false, source = "")
    }
}
