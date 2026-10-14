package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TenantIdTest {

    @ParameterizedTest
    @ValueSource(strings = ["acme", "7d7d57e1-4c50-4cb9-98b8-c5b60e8a2f01", "T"])
    fun `accepts a non-blank identifier`(value: String) {
        assertEquals(value, TenantId(value).value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t"])
    fun `rejects a blank identifier`(value: String) {
        val exception = assertThrows(InvalidTenantIdException::class.java) { TenantId(value) }

        assertEquals(value, exception.value)
    }

    @Test
    fun `accepts an identifier of the maximum length`() {
        val value = "t".repeat(TenantId.MAX_LENGTH)

        assertEquals(value, TenantId(value).value)
    }

    @Test
    fun `rejects an identifier longer than the maximum length`() {
        assertThrows(InvalidTenantIdException::class.java) { TenantId("t".repeat(TenantId.MAX_LENGTH + 1)) }
    }

    @Test
    fun `toString exposes the identifier`() {
        assertEquals("acme", TenantId("acme").toString())
    }
}
