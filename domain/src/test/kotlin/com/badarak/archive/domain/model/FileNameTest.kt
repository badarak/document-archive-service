package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class FileNameTest {

    @Test
    fun `keeps a plain file name unchanged`() {
        assertEquals("invoice-123.pdf", FileName.of("invoice-123.pdf").value)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/home/alice/invoice.pdf", "C:\\Users\\alice\\invoice.pdf", "C:\\fakepath\\invoice.pdf", "../../etc/passwd",
            "docs/2026\\invoice.pdf", "invoice.pdf/", ".", "..", "/", "docs/", "C:\\docs\\",
        ],
    )
    fun `rejects a name that holds a path`(raw: String) {
        val exception = assertThrows(InvalidDocumentException::class.java) { FileName.of(raw) }

        assertEquals("fileName", exception.field)
        assertEquals("Invalid document fileName: must not contain a path", exception.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["inv\u0000oice.pdf", "invoice\r\n.pdf", "invoice\t.pdf", "invoice\u007F.pdf", "invoice\u0085.pdf"])
    fun `rejects a name that holds a control character`(raw: String) {
        val exception = assertThrows(InvalidDocumentException::class.java) { FileName.of(raw) }

        assertEquals("Invalid document fileName: must not contain control or invisible formatting characters", exception.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["invoice\u202Efdp.exe", "invoice\u202D.pdf", "in\u200Bvoice.pdf", "invoice\u2066.pdf", "\uFEFFinvoice.pdf"])
    fun `rejects a name that holds an invisible formatting character, such as a right-to-left override`(raw: String) {
        val exception = assertThrows(InvalidDocumentException::class.java) { FileName.of(raw) }

        assertEquals("fileName", exception.field)
    }

    @Test
    fun `trims surrounding spaces`() {
        assertEquals("invoice.pdf", FileName.of("  invoice.pdf ").value)
    }

    @Test
    fun `keeps dots, spaces and accents inside the name`() {
        assertEquals("Facture n\u00B0 12 - \u00E9t\u00E9..v2.pdf", FileName.of("Facture n\u00B0 12 - \u00E9t\u00E9..v2.pdf").value)
    }

    @Test
    fun `puts the name in Unicode NFC form`() {
        val decomposed = "re\u0301sume\u0301.pdf"

        assertEquals("r\u00E9sum\u00E9.pdf", FileName.of(decomposed).value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `rejects a name that holds no file name`(raw: String) {
        val exception = assertThrows(InvalidDocumentException::class.java) { FileName.of(raw) }

        assertEquals("fileName", exception.field)
    }

    @Test
    fun `accepts a name of the maximum length`() {
        val raw = "a".repeat(FileName.MAX_LENGTH)

        assertEquals(raw, FileName.of(raw).value)
    }

    @Test
    fun `rejects a name longer than the maximum length`() {
        val exception = assertThrows(InvalidDocumentException::class.java) { FileName.of("a".repeat(FileName.MAX_LENGTH + 1)) }

        assertEquals("fileName", exception.field)
    }

    @ParameterizedTest
    @CsvSource("invoice.pdf, pdf", "REPORT.DOCX, docx", "archive.tar.gz, gz", "README, ''", "trailing., ''")
    fun `exposes the lowercase extension`(raw: String, expected: String) {
        assertEquals(expected, FileName.of(raw).extension)
    }

    @Test
    fun `toString exposes the name`() {
        assertEquals("invoice.pdf", FileName.of("invoice.pdf").toString())
    }
}
