package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentSizePolicyTest {

    private val policy = DocumentSizePolicy(maxSizeBytes = 1_000)

    @ParameterizedTest
    @ValueSource(longs = [1, 500, 1_000])
    fun `accepts a size between one byte and the limit`(sizeBytes: Long) {
        assertDoesNotThrow { policy.check(sizeBytes) }
    }

    @Test
    fun `rejects a size above the limit`() {
        val exception = assertThrows(DocumentTooLargeException::class.java) { policy.check(1_001) }

        assertEquals(1_001, exception.sizeBytes)
        assertEquals(1_000, exception.maxSizeBytes)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `rejects an empty document`(sizeBytes: Long) {
        val exception = assertThrows(EmptyDocumentException::class.java) { policy.check(sizeBytes) }

        assertEquals(sizeBytes, exception.sizeBytes)
    }

    @Test
    fun `defaults to a limit of 50 MiB`() {
        assertEquals(50L * 1024 * 1024, DocumentSizePolicy().maxSizeBytes)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1])
    fun `refuses a limit that is not positive`(maxSizeBytes: Long) {
        assertThrows(IllegalArgumentException::class.java) { DocumentSizePolicy(maxSizeBytes) }
    }
}
