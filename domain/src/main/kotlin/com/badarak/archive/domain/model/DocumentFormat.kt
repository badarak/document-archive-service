package com.badarak.archive.domain.model

/**
 * File formats the service knows how to archive, identified by their extension.
 *
 * Each format lists the content types a caller may declare for it, the first one being its own [mediaType], and, when
 * the format has one, the signature its first bytes must carry. E-mails are plain text: their content cannot be checked.
 */
enum class DocumentFormat(val extension: String, val contentTypes: Set<String>, private val signature: ((ByteArray) -> Boolean)?) {
    PDF("pdf", setOf("application/pdf"), { it.hasAt(0, "%PDF-") }),
    DOC("doc", setOf("application/msword"), { it.hasAt(0, OLE2_SIGNATURE) }),
    DOCX("docx", setOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), { it.hasAt(0, ZIP_SIGNATURE) }),
    MP3("mp3", setOf("audio/mpeg", "audio/mp3"), { it.hasAt(0, "ID3") || it.startsWithMpegFrame() }),
    WAV("wav", setOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"), { it.hasAt(0, "RIFF") && it.hasAt(8, "WAVE") }),
    M4A("m4a", setOf("audio/mp4", "audio/x-m4a", "audio/m4a"), { it.hasAt(4, "ftyp") }),
    EML("eml", setOf("message/rfc822"), null),
    ;

    val mediaType: String
        get() = contentTypes.first()

    fun matchesContent(header: ByteArray): Boolean = signature?.invoke(header) ?: true

    companion object {
        const val HEADER_LENGTH = 12

        fun ofExtension(extension: String): DocumentFormat? = entries.find { it.extension == extension.lowercase() }
    }
}

private val OLE2_SIGNATURE = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
private val ZIP_SIGNATURE = bytes(0x50, 0x4B, 0x03, 0x04)

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

private fun ByteArray.hasAt(offset: Int, expected: String): Boolean = hasAt(offset, expected.toByteArray(Charsets.US_ASCII))

private fun ByteArray.hasAt(offset: Int, expected: ByteArray): Boolean =
    size >= offset + expected.size && expected.indices.all { this[offset + it] == expected[it] }

private fun ByteArray.startsWithMpegFrame(): Boolean = size >= 2 && this[0] == 0xFF.toByte() && (this[1].toInt() and 0xE0) == 0xE0
