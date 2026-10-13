package com.badarak.archive.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentTypeTest {

    @ParameterizedTest
    @ValueSource(strings = ["INVOICE", "PAY_SLIP", "FORM_2042", "X"])
    fun `accepts uppercase business codes`(value: String) {
        assertEquals(value, DocumentType(value).value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "invoice", "Invoice", "2042_FORM", "_INVOICE", "PAY SLIP", "PAY-SLIP", "application/pdf"])
    fun `rejects values that are not uppercase business codes`(value: String) {
        val exception = assertThrows(InvalidDocumentTypeException::class.java) { DocumentType(value) }

        assertEquals(value, exception.value)
    }

    @Test
    fun `accepts a code of the maximum length`() {
        val value = "A".repeat(DocumentType.MAX_LENGTH)

        assertEquals(value, DocumentType(value).value)
    }

    @Test
    fun `rejects a code longer than the maximum length`() {
        assertThrows(InvalidDocumentTypeException::class.java) { DocumentType("A".repeat(DocumentType.MAX_LENGTH + 1)) }
    }

    @Test
    fun `toString exposes the code`() {
        assertEquals("INVOICE", DocumentType("INVOICE").toString())
    }
}
