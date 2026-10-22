package com.off.audioconverter.lyrics

/**
 * Turns a YouTube/JioSaavn title into the name a lyrics database would have
 * indexed it under.
 *
 * Only credits and packaging are removed — `(feat. X)`, `[Official Video]`,
 * `- Topic`, `(Lyrics)`. Anything that names a *different recording* is kept:
 * `(Remix)`, `(Acoustic)`, `(Live)`, `(Remastered)`. Stripping those would turn
 * a search for one recording into a search for another, and wrong words that
 * scroll in time are worse than no words at all.
 */
fun String.forLyricsSearch(): String {
    var name = this
    CREDITS.forEach { pattern -> name = pattern.replace(name, " ") }
    return name.replace(WHITESPACE, " ").trim().trimEnd(',', '-', '–', '—').trim()
        .ifBlank { trim() }
}

/** Trims " - Topic" off an auto-generated artist channel name. */
fun String.artistForLyricsSearch(): String =
    removeSuffix(" - Topic").trim().ifBlank { trim() }

private val WHITESPACE = Regex("""\s+""")

private val CREDITS = listOf(
    Regex("""\s*[(\[]\s*(feat|ft|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
    Regex("""\s+(feat|ft|featuring)\.?\s+.*$""", RegexOption.IGNORE_CASE),
    Regex(
        """\s*[(\[]\s*(official\s*)?(music\s*)?""" +
            """(video|audio|visuali[sz]er|lyrics?\s*video|lyrics?|m/?v|hd|hq|4k|full\s*song)""" +
            """\s*[)\]]""",
        RegexOption.IGNORE_CASE
    ),
    Regex("""\s*[(\[]\s*official\s*[)\]]""", RegexOption.IGNORE_CASE)
)
