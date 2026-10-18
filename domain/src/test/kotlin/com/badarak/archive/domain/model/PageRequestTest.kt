package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PageRequestTest {

    @Test
    fun `asks for the first page of 20 documents by default`() {
        assertEquals(PageRequest(0, 20), PageRequest())
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 100])
    fun `accepts a size between 1 and 100`(size: Int) {
        assertEquals(size, PageRequest(size = size).size)
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 0, 101])
    fun `rejects a size out of bounds`(size: Int) {
        assertInvalid("size") { PageRequest(size = size) }
    }

    @Test
    fun `rejects a negative page`() {
        assertInvalid("page") { PageRequest(page = -1) }
    }

    private fun assertInvalid(parameter: String, block: () -> Unit) {
        assertEquals(parameter, assertThrows(InvalidSearchException::class.java) { block() }.parameter)
    }
}
