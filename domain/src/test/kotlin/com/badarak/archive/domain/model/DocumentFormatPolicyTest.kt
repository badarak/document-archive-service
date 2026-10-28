package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentFormatPolicyTest {

    private val policy = DocumentFormatPolicy()

    @Test
    fun `allows every known format by default`() {
        assertEquals(DocumentFormat.entries.toSet(), policy.allowedFormats)
    }

    @Test
    fun `returns the media type of a conforming document`() {
        assertEquals("application/pdf", policy.check(FileName.of("invoice.pdf"), "application/pdf", PDF_HEADER))
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/pdf; charset=binary", "Application/PDF", " application/pdf ", " application/pdf ; charset=binary"])
    fun `reduces the declared content type to its media type, whatever its case and parameters`(contentType: String) {
        assertEquals("application/pdf", policy.check(FileName.of("invoice.pdf"), contentType, PDF_HEADER))
    }

    @ParameterizedTest
    @ValueSource(strings = ["setup.exe", "photo.png", "README"])
    fun `rejects an unknown or missing extension`(fileName: String) {
        val exception = assertThrows(UnsupportedDocumentFormatException::class.java) {
            policy.check(FileName.of(fileName), "application/pdf", PDF_HEADER)
        }

        assertEquals(fileName, exception.fileName)
    }

    @Test
    fun `rejects a known format that is not allowed`() {
        val pdfOnly = DocumentFormatPolicy(setOf(DocumentFormat.PDF))

        val exception = assertThrows(UnsupportedDocumentFormatException::class.java) {
            pdfOnly.check(FileName.of("letter.eml"), "message/rfc822", ByteArray(0))
        }

        assertEquals(listOf("pdf"), exception.allowedExtensions)
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/octet-stream", "application/msword", "text/plain", ""])
    fun `rejects a declared content type that does not match the extension`(contentType: String) {
        val exception = assertThrows(ContentTypeMismatchException::class.java) {
            policy.check(FileName.of("invoice.pdf"), contentType, PDF_HEADER)
        }

        assertEquals(contentType, exception.declaredContentType)
        assertEquals(DocumentFormat.PDF, exception.format)
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/octet-stream", "text/plain", ""])
    fun `stores the media type of the format when a mismatching declaration is tolerated`(contentType: String) {
        val tolerant = DocumentFormatPolicy(rejectMimeMismatch = false)

        assertEquals("application/pdf", tolerant.check(FileName.of("invoice.pdf"), contentType, PDF_HEADER))
    }

    @Test
    fun `keeps a matching declaration when a mismatching one is tolerated`() {
        val tolerant = DocumentFormatPolicy(rejectMimeMismatch = false)

        assertEquals("audio/x-wav", tolerant.check(FileName.of("call.wav"), "audio/x-wav", WAV_HEADER))
    }

    @Test
    fun `never lets through content that does not match the extension, even when a mismatching declaration is tolerated`() {
        val tolerant = DocumentFormatPolicy(rejectMimeMismatch = false)

        assertThrows(ContentMismatchException::class.java) {
            tolerant.check(FileName.of("invoice.pdf"), "application/pdf", "MZ\u0090\u0000".toByteArray(Charsets.ISO_8859_1))
        }
    }

    @Test
    fun `rejects content that does not match the extension`() {
        val exception = assertThrows(ContentMismatchException::class.java) {
            policy.check(FileName.of("invoice.pdf"), "application/pdf", "MZ\u0090\u0000".toByteArray(Charsets.ISO_8859_1))
        }

        assertEquals(DocumentFormat.PDF, exception.format)
    }

    @Test
    fun `accepts an e-mail whatever its content`() {
        assertEquals("message/rfc822", policy.check(FileName.of("message.eml"), "message/rfc822", "From: a@b.c".toByteArray()))
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

    private companion object {
        val PDF_HEADER = "%PDF-1.7\n%".toByteArray(Charsets.US_ASCII)
        val WAV_HEADER = "RIFF\u0024\u0008\u0000\u0000WAVE".toByteArray(Charsets.ISO_8859_1)
    }
}
