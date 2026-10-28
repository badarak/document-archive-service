package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentFormatPolicyTest {

    private val policy = DocumentFormatPolicy()
    private val conforming = ContentInspection.Conforming

    @Test
    fun `allows every known format by default`() {
        assertEquals(DocumentFormat.entries.toSet(), policy.allowedFormats)
    }

    @Test
    fun `finds the format a file name announces by its extension`() {
        assertEquals(DocumentFormat.PDF, policy.formatOf(FileName.of("invoice.pdf")))
        assertEquals(DocumentFormat.DOCX, policy.formatOf(FileName.of("REPORT.DOCX")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["setup.exe", "photo.png", "README"])
    fun `rejects an unknown or missing extension`(fileName: String) {
        val exception = assertThrows(UnsupportedDocumentFormatException::class.java) { policy.formatOf(FileName.of(fileName)) }

        assertEquals(fileName, exception.fileName)
    }

    @Test
    fun `rejects a known format that is not allowed`() {
        val pdfOnly = DocumentFormatPolicy(setOf(DocumentFormat.PDF))

        val exception = assertThrows(UnsupportedDocumentFormatException::class.java) { pdfOnly.formatOf(FileName.of("letter.eml")) }

        assertEquals(listOf("pdf"), exception.allowedExtensions)
    }

    @Test
    fun `returns the media type of a conforming document`() {
        assertEquals("application/pdf", policy.check(DocumentFormat.PDF, "application/pdf", conforming))
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/pdf; charset=binary", "Application/PDF", " application/pdf ", " application/pdf ; charset=binary"])
    fun `reduces the declared content type to its media type, whatever its case and parameters`(contentType: String) {
        assertEquals("application/pdf", policy.check(DocumentFormat.PDF, contentType, conforming))
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/octet-stream", "application/msword", "text/plain", ""])
    fun `rejects a declared content type that does not match the format`(contentType: String) {
        val exception = assertThrows(ContentTypeMismatchException::class.java) { policy.check(DocumentFormat.PDF, contentType, conforming) }

        assertEquals(contentType, exception.declaredContentType)
        assertEquals(DocumentFormat.PDF, exception.format)
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/octet-stream", "text/plain", ""])
    fun `stores the media type of the format when a mismatching declaration is tolerated`(contentType: String) {
        val tolerant = DocumentFormatPolicy(rejectMimeMismatch = false)

        assertEquals("application/pdf", tolerant.check(DocumentFormat.PDF, contentType, conforming))
    }

    @Test
    fun `keeps a matching declaration when a mismatching one is tolerated`() {
        val tolerant = DocumentFormatPolicy(rejectMimeMismatch = false)

        assertEquals("audio/x-wav", tolerant.check(DocumentFormat.WAV, "audio/x-wav", conforming))
    }

    @Test
    fun `rejects content of another format, naming what it is`() {
        val exception = assertThrows(ContentMismatchException::class.java) {
            policy.check(DocumentFormat.PDF, "application/pdf", ContentInspection.OtherFormat("application/x-executable"))
        }

        assertEquals(DocumentFormat.PDF, exception.format)
        assertEquals("application/x-executable", exception.detectedMediaType)
        assertEquals("The content does not match a .pdf file: it is application/x-executable", exception.message)
    }

    @Test
    fun `rejects content of an unrecognised format`() {
        val exception = assertThrows(ContentMismatchException::class.java) {
            policy.check(DocumentFormat.PDF, "application/pdf", ContentInspection.OtherFormat(null))
        }

        assertNull(exception.detectedMediaType)
        assertEquals("The content does not match a .pdf file", exception.message)
    }

    @Test
    fun `never lets through content of another format, even when a mismatching declaration is tolerated`() {
        val tolerant = DocumentFormatPolicy(rejectMimeMismatch = false)

        assertThrows(ContentMismatchException::class.java) {
            tolerant.check(DocumentFormat.PDF, "application/octet-stream", ContentInspection.OtherFormat(null))
        }
    }

    @Test
    fun `builds the policy from configured extensions`() {
        val configured = DocumentFormatPolicy.ofExtensions(listOf("pdf", " DOCX "))

        assertEquals(setOf(DocumentFormat.PDF, DocumentFormat.DOCX), configured.allowedFormats)
        assertTrue(configured.rejectMimeMismatch)
    }

    @Test
    fun `builds a policy that tolerates a mismatching declaration from configuration`() {
        assertFalse(DocumentFormatPolicy.ofExtensions(listOf("pdf"), rejectMimeMismatch = false).rejectMimeMismatch)
    }

    @Test
    fun `rejects a configured extension the service does not know`() {
        val exception = assertThrows(IllegalArgumentException::class.java) { DocumentFormatPolicy.ofExtensions(listOf("pdf", "exe")) }

        assertEquals(
            "Unknown document extension 'exe': supported extensions are pdf, doc, docx, mp3, wav, m4a, eml",
            exception.message,
        )
    }

    @Test
    fun `requires at least one allowed format`() {
        assertThrows(IllegalArgumentException::class.java) { DocumentFormatPolicy(emptySet()) }
    }
}
