package com.badarak.archive.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class DocumentIdTest {

    @Test
    fun `generate produces distinct identifiers`() {
        assertNotEquals(DocumentId.generate(), DocumentId.generate())
    }

    @Test
    fun `parse reads back the text representation of an identifier`() {
        val uuid = UUID.randomUUID()

        assertEquals(DocumentId(uuid), DocumentId.parse(uuid.toString()))
    }

    @Test
    fun `toString exposes the text representation of the UUID`() {
        val uuid = UUID.randomUUID()

        assertEquals(uuid.toString(), DocumentId(uuid).toString())
    }

    @Test
    fun `parse rejects a value that is not a UUID`() {
        assertThrows(IllegalArgumentException::class.java) { DocumentId.parse("pas-un-uuid") }
    }
}
