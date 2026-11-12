package dev.gruebling.navi.core.data.mapping

import dev.gruebling.navi.core.model.Lyrics
import dev.gruebling.navi.core.model.LyricsLine
import dev.gruebling.navi.core.network.model.StructuredLyrics

/**
 * Picks the variant to show from `getLyricsBySongId`: synced before unsynced, then one in [preferredLang]
 * (ISO 639 code, e.g. "de"), then the first. Variants without any text are ignored.
 */
internal fun List<StructuredLyrics>.pickBest(preferredLang: String?): StructuredLyrics? =
    filter { variant -> variant.line.any { it.value.isNotBlank() } }
        .sortedWith(
            compareByDescending<StructuredLyrics> { it.synced && it.line.any { line -> line.start != null } }
                .thenByDescending { preferredLang != null && it.lang.equals(preferredLang, ignoreCase = true) },
        )
        .firstOrNull()

/** Applies the offset (positive = show sooner) and drops trailing empty lines. */
internal fun StructuredLyrics.toLyrics(): Lyrics? {
    val synced = synced && line.any { it.start != null }
    val lines = line.map { dto ->
        LyricsLine(
            startMs = if (synced) ((dto.start ?: 0) - offset).coerceAtLeast(0) else null,
            text = dto.value.trim(),
        )
    }.dropLastWhile { it.text.isEmpty() }
    if (lines.none { it.text.isNotEmpty() }) return null
    return Lyrics(
        synced = synced,
        lines = if (synced) lines.sortedBy { it.startMs } else lines,
        lang = lang?.takeUnless { it.equals("xxx", ignoreCase = true) || it.equals("und", ignoreCase = true) },
    )
}

private val LRC_TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val LRC_TAG = Regex("""^\[[a-zA-Z]+:.*]$""")

/**
 * Plain text from the classic `getLyrics`. Text with LRC timestamps (`[01:02.50]line`) becomes synced lyrics; a line
 * may carry several timestamps (repeated chorus). Metadata tags like `[ar:Artist]` are dropped.
 */
internal fun parsePlainLyrics(text: String?): Lyrics? {
    if (text.isNullOrBlank()) return null
    val rawLines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
    val timed = mutableListOf<LyricsLine>()
    var hasTimestamps = false
    for (raw in rawLines) {
        val stamps = LRC_TIME.findAll(raw).toList()
        if (stamps.isEmpty() || stamps.first().range.first != 0) continue
        hasTimestamps = true
        val lyric = raw.substring(stamps.last().range.last + 1).trim()
        for (stamp in stamps) {
            val (min, sec, frac) = stamp.destructured
            val fracMs = when (frac.length) {
                0 -> 0
                1 -> frac.toInt() * 100
                2 -> frac.toInt() * 10
                else -> frac.take(3).toInt()
            }
            timed += LyricsLine(startMs = (min.toLong() * 60 + sec.toLong()) * 1000 + fracMs, text = lyric)
        }
    }
    if (hasTimestamps && timed.any { it.text.isNotEmpty() }) {
        return Lyrics(synced = true, lines = timed.sortedBy { it.startMs }.dropLastWhile { it.text.isEmpty() })
    }
    val lines = rawLines
        .filterNot { LRC_TAG.matches(it.trim()) }
        .map { LyricsLine(startMs = null, text = it.trim()) }
        .dropWhile { it.text.isEmpty() }
        .dropLastWhile { it.text.isEmpty() }
    return if (lines.isEmpty()) null else Lyrics(synced = false, lines = lines)
}
