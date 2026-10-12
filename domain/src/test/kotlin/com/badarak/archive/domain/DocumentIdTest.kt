package com.badarak.archive.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class DocumentIdTest {

    @Test
    fun `generate produit des identifiants distincts`() {
        assertNotEquals(DocumentId.generate(), DocumentId.generate())
    }

    @Test
    fun `parse relit la representation textuelle d'un identifiant`() {
        val uuid = UUID.randomUUID()

        assertEquals(DocumentId(uuid), DocumentId.parse(uuid.toString()))
    }

    @Test
    fun `parse rejette une valeur qui n'est pas un UUID`() {
        assertThrows(IllegalArgumentException::class.java) { DocumentId.parse("pas-un-uuid") }
    }
}
