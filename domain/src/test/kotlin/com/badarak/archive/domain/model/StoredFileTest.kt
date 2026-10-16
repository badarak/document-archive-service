package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StoredFileTest {

    private val key = ObjectKey("documents/7d7d57e1")

    @Test
    fun `keeps the file name, type, size and object key`() {
        val file = StoredFile("invoice-123.pdf", "application/pdf", 248_120, key)

        assertEquals("invoice-123.pdf", file.fileName)
        assertEquals("application/pdf", file.contentType)
        assertEquals(248_120, file.sizeBytes)
        assertEquals(key, file.objectKey)
    }

    @Test
    fun `blank fields are refused`() {
        assertInvalid("fileName") { file(fileName = "") }
        assertInvalid("contentType") { file(contentType = " ") }
    }

    @Test
    fun `fields longer than their maximum length are refused`() {
        assertInvalid("fileName") { file(fileName = "x".repeat(StoredFile.FILE_NAME_MAX_LENGTH + 1)) }
        assertInvalid("contentType") { file(contentType = "x".repeat(StoredFile.CONTENT_TYPE_MAX_LENGTH + 1)) }
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `an empty file is refused`(sizeBytes: Long) {
        assertThrows(EmptyDocumentException::class.java) { file(sizeBytes = sizeBytes) }
    }

    private fun file(fileName: String = "invoice-123.pdf", contentType: String = "application/pdf", sizeBytes: Long = 248_120) =
        StoredFile(fileName, contentType, sizeBytes, key)

    private fun assertInvalid(field: String, block: () -> Unit) {
        assertEquals(field, assertThrows(InvalidDocumentException::class.java) { block() }.field)
    }
}
