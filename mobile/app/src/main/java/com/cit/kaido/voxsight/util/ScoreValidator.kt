package com.cit.kaido.voxsight.util

/**
 * ScoreValidator — Client-side validation gate for VoxSight sheet music.
 * Rejects purely non-vocal instrumental scores (e.g. solo piano, guitar, or string/orchestra-only)
 * while accepting all choral, SATB, divisi, solo+choir, and Audiveris OMR vocal scores.
 */
object ScoreValidator {

    // Backend SATB analyzer vocal stamps (excludes purely instrumental Accompaniment/OTHERS)
    private val STAMPED_VOCAL_VOICE_REGEX = Regex(
        """data-vx-voice\s*=\s*["'](S|S1|S2|A|A1|A2|T|T1|T2|B|B1|B2|Solo|SOLO|TUTTI|UNISON_[A-Z0-9]+|[1-5])["']"""
    )

    // Vocal & choral identifiers in <part-list> (including Audiveris default 'Voice' / 'Voice Oohs' / 'Choir Aahs')
    private val PART_LIST_VOCAL_REGEX = Regex(
        "\\b(soprano|sopr|sop|mezzo|alto|contralto|tenor|ten|baritone|choir|chorus|choral|satb|voice|voices|vocal|vocals|coro|unison|solo|cantor|descant|aahs|oohs|s1|s2|a1|a2|t1|t2|b1|b2)\\b",
        RegexOption.IGNORE_CASE
    )

    // Bass vocal check (matches 'Bass' unless qualified as an orchestral/band bass instrument)
    private val BASS_WORD_REGEX = Regex("\\bbass\\b", RegexOption.IGNORE_CASE)
    private val INSTRUMENTAL_BASS_REGEX = Regex(
        "\\b(double\\s+bass|string\\s+bass|upright\\s+bass|acoustic\\s+bass|electric\\s+bass|contrabass|contra[-\\s]bass|bass\\s+(?:guitar|clarinet|trombone|tuba|flute|drum|sax|saxophone|oon))\\b",
        RegexOption.IGNORE_CASE
    )

    // Short SATB part-name abbreviations: <part-name>S</part-name>, <part-name>S.</part-name>, etc.
    private val ABBREV_PART_NAME_REGEX = Regex(
        "<part-name[^>]*>\\s*(S|A|T|B|SA|TB|SATB)\\.?\\s*</part-name>",
        RegexOption.IGNORE_CASE
    )

    // Explicit non-vocal instrument names in <part-list>
    private val EXPLICIT_INSTRUMENTAL_PART_REGEX = Regex(
        "\\b(piano|pianoforte|acoustic\\s+grand|harpsichord|organ|keyboard|guitar|ukulele|banjo|violin|viola|violoncello|cello|flute|piccolo|oboe|clarinet|bassoon|saxophone|trumpet|cornet|horn|trombone|tuba|euphonium|timpani|percussion|drums|drumset|marimba|xylophone|harp|accordion)\\b",
        RegexOption.IGNORE_CASE
    )

    private val HEADER_CHORAL_REGEX = Regex(
        "\\b(satb|choir|chorus|choral|vocal|a\\s*cappella|acapella|hymn|anthem|psalm|cantata|motet)\\b",
        RegexOption.IGNORE_CASE
    )

    private val LYRIC_TAG_REGEX = Regex("<lyric\\b", RegexOption.IGNORE_CASE)

    /**
     * Inspects raw MusicXML content.
     * Returns null if the score contains vocal/choral parts, or an error message if purely instrumental.
     */
    fun checkUnsupportedMusicXml(xml: String): String? {
        if (xml.isBlank()) {
            return "Invalid Score: The imported MusicXML content is empty."
        }

        // 1. Trust backend SATB/vocal analysis stamps when present
        if (STAMPED_VOCAL_VOICE_REGEX.containsMatchIn(xml)) {
            return null
        }

        val partListStart = xml.indexOf("<part-list", ignoreCase = true)
        val partListEnd = if (partListStart != -1) {
            xml.indexOf("</part-list>", startIndex = partListStart, ignoreCase = true)
        } else {
            -1
        }
        val partListSection = if (partListStart != -1 && partListEnd != -1) {
            xml.substring(partListStart, partListEnd)
        } else {
            xml.take(15_000)
        }

        // 2. Check <part-list> for vocal/choral part or instrument names
        if (PART_LIST_VOCAL_REGEX.containsMatchIn(partListSection) ||
            ABBREV_PART_NAME_REGEX.containsMatchIn(partListSection) ||
            partListSection.contains("s.a.t.b", ignoreCase = true)
        ) {
            return null
        }
        if (BASS_WORD_REGEX.containsMatchIn(partListSection) &&
            !INSTRUMENTAL_BASS_REGEX.containsMatchIn(partListSection)
        ) {
            return null
        }

        // 3. Check header/credits before first <part>
        val firstPartIdx = xml.indexOf("<part ", ignoreCase = true)
        val headerLimit = (if (firstPartIdx != -1) firstPartIdx else xml.length).coerceAtMost(30_000)
        val headerSection = xml.substring(0, headerLimit)
        if (HEADER_CHORAL_REGEX.containsMatchIn(headerSection)) {
            return null
        }

        // 4. Any <lyric> element indicates sung vocal text
        if (LYRIC_TAG_REGEX.containsMatchIn(xml)) {
            return null
        }

        // 5. If <part-list> has unnamed/generic parts (e.g. P1..P4) and is NOT explicitly named as non-vocal instruments, allow it
        if (!EXPLICIT_INSTRUMENTAL_PART_REGEX.containsMatchIn(partListSection) &&
            !INSTRUMENTAL_BASS_REGEX.containsMatchIn(partListSection)
        ) {
            return null
        }

        return "Unsupported Score: Purely instrumental score without SATB or choral vocal parts. VoxSight requires sheet music with an SATB or choral vocal component. Scores intended exclusively for piano, guitar, or orchestra without vocal parts cannot be processed."
    }
}
