package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class DocumentFormatTest {

    @Test
    fun `finds a format by its extension, whatever the case`() {
        assertEquals(DocumentFormat.PDF, DocumentFormat.ofExtension("pdf"))
        assertEquals(DocumentFormat.DOCX, DocumentFormat.ofExtension("DOCX"))
    }

    @Test
    fun `the media type of a format is the first content type it accepts`() {
        assertEquals("application/pdf", DocumentFormat.PDF.mediaType)
        assertEquals("audio/mpeg", DocumentFormat.MP3.mediaType)
        assertEquals("audio/wav", DocumentFormat.WAV.mediaType)
    }

    @Test
    fun `knows no format for an unsupported or missing extension`() {
        assertNull(DocumentFormat.ofExtension("exe"))
        assertNull(DocumentFormat.ofExtension(""))
    }

    @Test
    fun `recognises each format from the first bytes of a real file`() {
        val headers = mapOf(
            DocumentFormat.PDF to ascii("%PDF-1.7\n%"),
            DocumentFormat.DOC to bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1, 0, 0, 0, 0),
            DocumentFormat.DOCX to bytes(0x50, 0x4B, 0x03, 0x04, 0x14, 0, 0x06, 0, 0x08, 0, 0, 0),
            DocumentFormat.MP3 to ascii("ID3") + bytes(0x04, 0, 0, 0, 0, 0, 0x23, 0, 0),
            DocumentFormat.WAV to ascii("RIFF") + bytes(0x24, 0x08, 0, 0) + ascii("WAVE"),
            DocumentFormat.M4A to bytes(0, 0, 0, 0x20) + ascii("ftypM4A "),
        )

        headers.forEach { (format, header) -> assertTrue(format.matchesContent(header), "$format") }
    }

    @Test
    fun `recognises an mp3 file without ID3 tag from its frame header`() {
        assertTrue(DocumentFormat.MP3.matchesContent(bytes(0xFF, 0xFB, 0x90, 0x64)))
    }

    @ParameterizedTest
    @EnumSource(DocumentFormat::class, names = ["EML"], mode = EnumSource.Mode.EXCLUDE)
    fun `rejects content that carries another signature`(format: DocumentFormat) {
        assertFalse(format.matchesContent(ascii("GIF89a......")))
    }

    @ParameterizedTest
    @EnumSource(DocumentFormat::class, names = ["EML"], mode = EnumSource.Mode.EXCLUDE)
    fun `rejects content too short to carry the signature`(format: DocumentFormat) {
        assertFalse(format.matchesContent(ascii("%P")))
    }

    @Test
    fun `wav needs both the RIFF container and the WAVE type`() {
        assertFalse(DocumentFormat.WAV.matchesContent(ascii("RIFF") + bytes(0x24, 0x08, 0, 0) + ascii("AVI ")))
    }

    @Test
    fun `cannot check the content of an e-mail`() {
        assertTrue(DocumentFormat.EML.matchesContent(ascii("anything")))
        assertTrue(DocumentFormat.EML.matchesContent(ByteArray(0)))
    }

    private fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
