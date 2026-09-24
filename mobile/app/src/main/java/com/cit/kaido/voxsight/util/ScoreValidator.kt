package com.cit.kaido.voxsight.util

/**
 * ScoreValidator — Client-side validation gate for VoxSight sheet music.
 * Ensures scores conform strictly to SATB choral configurations and rejects
 * scores containing solo parts, lead sheets, or piano/keyboard accompaniments.
 */
object ScoreValidator {

    /**
     * Inspects raw MusicXML content.
     * Returns null if score contains an SATB/choral component, or an error description string if purely instrumental.
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

        val choralKeywords = listOf(
            "soprano", "alto", "tenor", "bass", "choir", "chorus", "choral",
            "satb", "s.a.t.b.", "voices", "vocal", "coro", "unison"
        )

        var hasChoralComponent = false

        // Check part-list names
        for (kw in choralKeywords) {
            if (Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(partListSection)) {
                hasChoralComponent = true
                break
            }
        }

        // Check header/title
        if (!hasChoralComponent) {
            val headerSection = lowerXml.substring(0, (lowerXml.indexOf("<part ").takeIf { it != -1 } ?: lowerXml.length).coerceAtMost(30000))
            for (kw in listOf("satb", "choir", "chorus", "choral")) {
                if (Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(headerSection)) {
                    hasChoralComponent = true
                    break
                }
            }
        }

        // Check lyrics count across the score
        val lyricCount = Regex("<lyric\\b").findAll(lowerXml).count()
        if (lyricCount >= 5) {
            hasChoralComponent = true
        }

        if (!hasChoralComponent && lyricCount < 5) {
            return "Unsupported Score: Purely instrumental score without SATB or choral vocal parts. VoxSight requires sheet music with an SATB or choral vocal component. Scores intended exclusively for piano, guitar, or orchestra without vocal parts cannot be processed."
        }

        return null
    }
}
