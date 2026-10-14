package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class ObjectKeyTest {

    @Test
    fun `a generated key is a random UUID under the documents prefix`() {
        val key = ObjectKey.generate()

        assertTrue(key.value.startsWith(ObjectKey.PREFIX))
        UUID.fromString(key.value.removePrefix(ObjectKey.PREFIX))
    }

    @Test
    fun `two generated keys differ`() {
        assertNotEquals(ObjectKey.generate(), ObjectKey.generate())
    }

    @Test
    fun `an existing key is rebuilt as is`() {
        assertEquals("documents/7d7d57e1", ObjectKey("documents/7d7d57e1").value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `rejects a blank key`(value: String) {
        val exception = assertThrows(InvalidDocumentException::class.java) { ObjectKey(value) }

        assertEquals("objectKey", exception.field)
    }

    @Test
    fun `rejects a key longer than the maximum length`() {
        val exception = assertThrows(InvalidDocumentException::class.java) { ObjectKey("x".repeat(ObjectKey.MAX_LENGTH + 1)) }

        assertEquals("objectKey", exception.field)
    }

    @Test
    fun `toString exposes the key`() {
        assertEquals("documents/7d7d57e1", ObjectKey("documents/7d7d57e1").toString())
    }
}
