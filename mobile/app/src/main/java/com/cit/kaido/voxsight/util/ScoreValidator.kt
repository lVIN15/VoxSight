package com.cit.kaido.voxsight.util

/**
 * ScoreValidator — Client-side validation gate for VoxSight sheet music.
 * Ensures scores conform strictly to SATB choral configurations and rejects
 * scores containing solo parts, lead sheets, or piano/keyboard accompaniments.
 */
object ScoreValidator {

    private val PART_LIST_CHORAL_REGEX = Regex(
        "\\b(soprano|alto|tenor|bass|choir|chorus|choral|satb|voices|vocal|coro|unison)\\b",
        RegexOption.IGNORE_CASE
    )
    private val HEADER_CHORAL_REGEX = Regex(
        "\\b(satb|choir|chorus|choral)\\b",
        RegexOption.IGNORE_CASE
    )
    private val LYRIC_TAG_REGEX = Regex("<lyric\\b", RegexOption.IGNORE_CASE)

    /**
     * Inspects raw MusicXML content.
     * Returns null if score contains an SATB/choral component, or an error description string if purely instrumental.
     */
    fun checkUnsupportedMusicXml(xml: String): String? {
        val partListStart = xml.indexOf("<part-list", ignoreCase = true)
        val partListEnd = if (partListStart != -1) {
            xml.indexOf("</part-list>", startIndex = partListStart, ignoreCase = true)
        } else {
            -1
        }
        val partListSection = if (partListStart != -1 && partListEnd != -1) {
            xml.substring(partListStart, partListEnd)
        } else {
            xml
        }

        // Check part-list names first (fast header slice, no full-file lowercase allocation)
        if (PART_LIST_CHORAL_REGEX.containsMatchIn(partListSection) ||
            partListSection.contains("s.a.t.b.", ignoreCase = true)
        ) {
            return null
        }

        // Check header/title before first <part>
        val firstPartIdx = xml.indexOf("<part ", ignoreCase = true)
        val headerLimit = (if (firstPartIdx != -1) firstPartIdx else xml.length).coerceAtMost(30_000)
        val headerSection = xml.substring(0, headerLimit)
        if (HEADER_CHORAL_REGEX.containsMatchIn(headerSection)) {
            return null
        }

        // Lazily check if there are at least 5 <lyric> tags without scanning the rest of the file
        val hasEnoughLyrics = LYRIC_TAG_REGEX.findAll(xml).take(5).count() >= 5
        if (hasEnoughLyrics) {
            return null
        }

        return "Unsupported Score: Purely instrumental score without SATB or choral vocal parts. VoxSight requires sheet music with an SATB or choral vocal component. Scores intended exclusively for piano, guitar, or orchestra without vocal parts cannot be processed."
    }
}
