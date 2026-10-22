package com.off.audioconverter.lyrics

/**
 * A small LRC parser.
 *
 * Handles `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, several timestamps on one line
 * (`[00:12.00][01:20.00]chorus`), and the `[offset:+/-ms]` metadata tag. Line end
 * times are inferred from the next line's start, which is what makes the karaoke
 * sweep possible on line-synced files.
 */
object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private const val LAST_LINE_HOLD_MS = 5000L

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()

        val offsetMs = OFFSET.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val stamped = ArrayList<Pair<Long, String>>()
        for (line in raw.lines()) {
            val matches = TIMESTAMP.findAll(line).toList()
            if (matches.isEmpty()) continue
            val textStart = (matches.last().range.last + 1).coerceAtMost(line.length)
            val text = line.substring(textStart).trim()
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                val fraction = fractionToMillis(match.groupValues[3])
                val timeMs = minutes * 60_000L + seconds * 1_000L + fraction - offsetMs
                stamped += timeMs.coerceAtLeast(0L) to text
            }
        }
        if (stamped.isEmpty()) return emptyList()

        val ordered = stamped.sortedBy { it.first }
        return ordered.mapIndexed { index, (start, text) ->
            val end = if (index + 1 < ordered.size) ordered[index + 1].first else start + LAST_LINE_HOLD_MS
            LyricLine(startMs = start, endMs = end.coerceAtLeast(start + 200L), text = text)
        }
    }

    /** Plain lyrics (no timestamps) become unsynced lines. */
    fun plain(text: String?): List<LyricLine> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { LyricLine(startMs = 0L, endMs = 0L, text = it) }
    }

    private fun fractionToMillis(raw: String): Long = when {
        raw.isEmpty() -> 0L
        raw.length == 1 -> raw.toLongOrNull()?.times(100) ?: 0L
        raw.length == 2 -> raw.toLongOrNull()?.times(10) ?: 0L
        else -> raw.take(3).toLongOrNull() ?: 0L
    }
}
