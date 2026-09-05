package com.cit.kaido.voxsight.util

/**
 * ScoreValidator — Client-side validation gate for VoxSight sheet music.
 * Ensures scores conform strictly to SATB choral configurations and rejects
 * scores containing solo parts, lead sheets, or piano/keyboard accompaniments.
 */
object ScoreValidator {

    /**
     * Inspects raw MusicXML content.
     * Returns null if score is valid SATB, or an error description string if unsupported.
     */
    fun checkUnsupportedMusicXml(xml: String): String? {
        val lowerXml = xml.lowercase()
        val partListStart = lowerXml.indexOf("<part-list")
        val partListEnd = lowerXml.indexOf("</part-list>")
        val partListSection = if (partListStart != -1 && partListEnd != -1) {
            lowerXml.substring(partListStart, partListEnd)
        } else {
            lowerXml
        }

        val accompKeywords = listOf(
            "piano", "pno", "keyboard", "kbd", "organ", "org",
            "accompaniment", "accomp", "acc.", "acc", "guitar", "gtr",
            "strings", "orchestra", "orch", "harp", "harpsichord", "celesta",
            "synthesizer", "synth", "continuo"
        )
        val soloKeywords = listOf(
            "solo", "soloist", "cantor", "leader", "voice solo", "vocal solo",
            "solo voice", "duet", "lead sheet", "fake book"
        )

        val found = mutableListOf<String>()

        // 1. Text & Metadata inspection (Titles, Credits, Directions)
        val headerSection = lowerXml.substring(0, (lowerXml.indexOf("<part ").takeIf { it != -1 } ?: lowerXml.length).coerceAtMost(30000))
        for (kw in soloKeywords) {
            if (Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(headerSection) && !headerSection.contains("soprano $kw")) {
                found.add("Solo notation '$kw'")
                break
            }
        }
        for (kw in accompKeywords) {
            if (Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(headerSection)) {
                found.add("Accompaniment notation '$kw'")
                break
            }
        }

        // 2. Score-part definitions inspection
        for (kw in soloKeywords) {
            if (Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(partListSection) && !partListSection.contains("soprano $kw")) {
                found.add("Solo voice ('$kw')")
                break
            }
        }
        for (kw in accompKeywords) {
            if (Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(partListSection)) {
                found.add("Accompaniment ('$kw')")
                break
            }
        }

        // 3. Structural Part Count Check
        val scorePartRegex = Regex("<score-part\\b")
        val partMatches = scorePartRegex.findAll(partListSection).count()
        if (partMatches == 1) {
            val hasMultiStaff = lowerXml.contains("<staves>2</staves>") || lowerXml.contains("<staves>4</staves>") || lowerXml.contains("<staff>2</staff>")
            if (!hasMultiStaff) {
                found.add("Single vocal melody / Solo lead sheet (only 1 staff detected; expected 2-4 SATB choral staves)")
            }
        } else if (partMatches > 4) {
            found.add("Too many parts ($partMatches parts; pure SATB choral scores support at most 4 voices, detected extra Solo or Accompaniment staves)")
        }

        // 4. Grand staff accompaniment without lyrics check
        if (lowerXml.contains("<staves>2</staves>")) {
            val lyricCount = Regex("<lyric\\b").findAll(lowerXml).count()
            if (lyricCount < 5) {
                found.add("Piano/Accompaniment grand staff ($lyricCount lyrics detected)")
            }
        }

        if (found.isNotEmpty()) {
            val uniqueFound = found.distinct()
            return "Unsupported Score: Detected ${uniqueFound.joinToString(", ")}. Scores containing Solo voices or Piano accompaniment staves are not supported. VoxSight is designed exclusively for SATB choral sheet music. Please upload an SATB vocal score."
        }

        return null
    }
}
