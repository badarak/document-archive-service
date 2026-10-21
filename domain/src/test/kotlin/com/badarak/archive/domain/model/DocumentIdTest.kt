package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class DocumentIdTest {

    @Test
    fun `generate produces distinct identifiers`() {
        assertNotEquals(DocumentId.generate(), DocumentId.generate())
    }

    @Test
    fun `toString exposes the text representation of the UUID`() {
        val uuid = UUID.randomUUID()

        assertEquals(uuid.toString(), DocumentId(uuid).toString())
    }
}
