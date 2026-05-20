package com.cit.kaido.voxsight.ui.screens.practice

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEFAULT_BPM = 96f

/**
 * Minimal MusicXML parser for prototype playback and score visualization.
 */
data class MusicXmlScore(
    val title: String,
    val composer: String,
    val notes: List<MusicXmlNote>,
    val totalSeconds: Int
)

data class MusicXmlNote(
    val step: String,
    val octave: Int,
    val durationDivisions: Int
)

fun parseMusicXmlScore(
    context: Context,
    uri: Uri,
    fallbackTitle: String
): MusicXmlScore? {
    val rawText = readMusicXmlText(context, uri) ?: return null

    val notes = mutableListOf<MusicXmlNote>()
    var title: String? = null
    var composer: String? = null
    var divisions = 1
    var totalDuration = 0

    var inNote = false
    var isRest = false
    var step: String? = null
    var octave: Int? = null
    var duration: Int? = null

    val parser = Xml.newPullParser()
    parser.setInput(StringReader(sanitizeXmlEntities(rawText)))

    return try {
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "work-title",
                        "movement-title",
                        "credit-words" -> {
                            if (title.isNullOrBlank()) {
                                title = parser.nextText().trim()
                            }
                        }
                        "creator" -> {
                            val type = parser.getAttributeValue(null, "type")
                            if (composer.isNullOrBlank() && type == "composer") {
                                composer = parser.nextText().trim()
                            }
                        }
                        "divisions" -> {
                            divisions = parser.nextText().toIntOrNull() ?: divisions
                        }
                        "note" -> {
                            inNote = true
                            isRest = false
                            step = null
                            octave = null
                            duration = null
                        }
                        "rest" -> if (inNote) {
                            isRest = true
                        }
                        "step" -> if (inNote) {
                            step = parser.nextText().trim()
                        }
                        "octave" -> if (inNote) {
                            octave = parser.nextText().toIntOrNull()
                        }
                        "duration" -> if (inNote) {
                            duration = parser.nextText().toIntOrNull()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "note" && inNote) {
                        val durationValue = duration ?: 0
                        totalDuration += durationValue

                        if (!isRest && step != null && octave != null) {
                            notes.add(
                                MusicXmlNote(
                                    step = step!!,
                                    octave = octave!!,
                                    durationDivisions = durationValue
                                )
                            )
                        }
                        inNote = false
                    }
                }
            }
            event = parser.next()
        }

        val beats = if (divisions > 0) {
            totalDuration.toFloat() / divisions
        } else {
            notes.size.toFloat()
        }
        val totalSeconds = max(1, (beats * 60f / DEFAULT_BPM).roundToInt())
        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: fallbackTitle.ifBlank { "Untitled Score" }
        val resolvedComposer = composer?.takeIf { it.isNotBlank() } ?: "Unknown Composer"

        MusicXmlScore(
            title = resolvedTitle,
            composer = resolvedComposer,
            notes = notes,
            totalSeconds = totalSeconds
        )
    } catch (_: Exception) {
        null
    }
}

private val xmlEntityPattern =
    Regex("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)")

private fun sanitizeXmlEntities(xml: String): String {
    // Guard against unescaped '&' in metadata fields that crash the XML parser.
    return xmlEntityPattern.replace(xml, "&amp;")
}

private fun readMusicXmlText(context: Context, uri: Uri): String? {
    val bytes = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    } ?: return null

    return if (looksLikeZip(bytes)) {
        extractXmlFromZip(bytes)
    } else {
        decodeXmlBytes(bytes)
    }
}

private fun looksLikeZip(bytes: ByteArray): Boolean {
    return bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
}

private fun extractXmlFromZip(bytes: ByteArray): String? {
    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val name = entry.name.lowercase()
            if (!entry.isDirectory && (name.endsWith(".xml") || name.endsWith(".musicxml"))) {
                val data = zis.readBytes()
                return decodeXmlBytes(data)
            }
            entry = zis.nextEntry
        }
    }
    return null
}

private fun decodeXmlBytes(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return String(bytes.copyOfRange(2, bytes.size), Charset.forName("UTF-16LE"))
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return String(bytes.copyOfRange(2, bytes.size), Charset.forName("UTF-16BE"))
    }
    return String(bytes, Charsets.UTF_8)
}
