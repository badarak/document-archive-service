package com.badarak.archive.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `returns the format of a conforming document`() {
        assertEquals(DocumentFormat.PDF, policy.check(FileName.of("invoice.pdf"), "application/pdf", PDF_HEADER))
    }

    @ParameterizedTest
    @ValueSource(strings = ["application/pdf; charset=binary", "Application/PDF", " application/pdf "])
    fun `ignores the case and the parameters of the declared content type`(contentType: String) {
        assertEquals(DocumentFormat.PDF, policy.check(FileName.of("invoice.pdf"), contentType, PDF_HEADER))
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

    @Test
    fun `rejects content that does not match the extension`() {
        val exception = assertThrows(ContentMismatchException::class.java) {
            policy.check(FileName.of("invoice.pdf"), "application/pdf", "MZ\u0090\u0000".toByteArray(Charsets.ISO_8859_1))
        }

        assertEquals(DocumentFormat.PDF, exception.format)
    }

    @Test
    fun `accepts an e-mail whatever its content`() {
        assertEquals(DocumentFormat.EML, policy.check(FileName.of("message.eml"), "message/rfc822", "From: a@b.c".toByteArray()))
    }

    @Test
    fun `builds the policy from configured extensions`() {
        val configured = DocumentFormatPolicy.ofExtensions(listOf("pdf", " DOCX "))

        assertEquals(setOf(DocumentFormat.PDF, DocumentFormat.DOCX), configured.allowedFormats)
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
    }
}
