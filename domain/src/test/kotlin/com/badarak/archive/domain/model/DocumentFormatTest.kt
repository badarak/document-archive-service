package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
}
