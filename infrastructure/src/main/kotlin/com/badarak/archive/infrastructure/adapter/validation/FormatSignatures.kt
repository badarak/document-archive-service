package com.badarak.archive.infrastructure.adapter.validation

import com.badarak.archive.domain.model.DocumentFormat

/** The signature the first bytes of each format carry. */
internal object FormatSignatures {

    private val OLE2 = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
    private val ZIP = bytes(0x50, 0x4B, 0x03, 0x04)

    fun matches(format: DocumentFormat, header: ByteArray): Boolean = when (format) {
        DocumentFormat.PDF -> header.hasAt(0, "%PDF-")
        DocumentFormat.DOC -> header.hasAt(0, OLE2)
        DocumentFormat.DOCX -> header.hasAt(0, ZIP)
        DocumentFormat.MP3 -> header.hasAt(0, "ID3") || header.startsWithMpegFrame()
        DocumentFormat.WAV -> header.hasAt(0, "RIFF") && header.hasAt(8, "WAVE")
        DocumentFormat.M4A -> header.hasAt(4, "ftyp")
        DocumentFormat.OGG -> header.hasAt(0, "OggS")
        DocumentFormat.EML -> true
    }

    private fun ByteArray.startsWithMpegFrame(): Boolean = size >= 2 && this[0] == 0xFF.toByte() && (this[1].toInt() and 0xE0) == 0xE0
}
