package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentContentTest {

    private val key = ObjectKey("documents/7d7d57e1")

    @Test
    fun `keeps the file name, type, size and object key`() {
        val content = DocumentContent("invoice-123.pdf", "application/pdf", 248_120, key)

        assertEquals("invoice-123.pdf", content.fileName)
        assertEquals("application/pdf", content.contentType)
        assertEquals(248_120, content.sizeBytes)
        assertEquals(key, content.objectKey)
    }

    @Test
    fun `blank fields are refused`() {
        assertInvalid("fileName") { content(fileName = "") }
        assertInvalid("contentType") { content(contentType = " ") }
    }

    @Test
    fun `fields longer than their maximum length are refused`() {
        assertInvalid("fileName") { content(fileName = "x".repeat(DocumentContent.FILE_NAME_MAX_LENGTH + 1)) }
        assertInvalid("contentType") { content(contentType = "x".repeat(DocumentContent.CONTENT_TYPE_MAX_LENGTH + 1)) }
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `an empty content is refused`(sizeBytes: Long) {
        assertThrows(EmptyDocumentException::class.java) { content(sizeBytes = sizeBytes) }
    }

    private fun content(fileName: String = "invoice-123.pdf", contentType: String = "application/pdf", sizeBytes: Long = 248_120) =
        DocumentContent(fileName, contentType, sizeBytes, key)

    private fun assertInvalid(field: String, block: () -> Unit) {
        assertEquals(field, assertThrows(InvalidDocumentException::class.java) { block() }.field)
    }
}
