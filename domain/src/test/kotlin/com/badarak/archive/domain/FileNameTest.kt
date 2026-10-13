package com.badarak.archive.domain

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
    @CsvSource(
        "/home/alice/invoice.pdf, invoice.pdf",
        "'C:\\Users\\alice\\invoice.pdf', invoice.pdf",
        "../../etc/passwd, passwd",
        "'docs/2026\\invoice.pdf', invoice.pdf",
    )
    fun `drops the path sent by the client`(raw: String, expected: String) {
        assertEquals(expected, FileName.of(raw).value)
    }

    @Test
    fun `trims surrounding spaces and removes control characters`() {
        assertEquals("invoice.pdf", FileName.of("  inv\u0000oice\r\n.pdf\t ").value)
    }

    @Test
    fun `puts the name in Unicode NFC form`() {
        val decomposed = "re\u0301sume\u0301.pdf"

        assertEquals("r\u00E9sum\u00E9.pdf", FileName.of(decomposed).value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", ".", "..", "/", "docs/", "C:\\docs\\", "\u0000\u0001"])
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

    @Test
    fun `measures the length after the path is dropped`() {
        val name = "a".repeat(FileName.MAX_LENGTH)

        assertEquals(name, FileName.of("/some/very/long/directory/$name").value)
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
