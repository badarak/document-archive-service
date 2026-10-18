package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DocumentPageTest {

    @ParameterizedTest(name = "{0} documents in pages of {1} make {2} pages")
    @CsvSource("0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2", "100, 100, 1")
    fun `counts the pages needed for every matching document`(totalElements: Long, size: Int, totalPages: Long) {
        assertEquals(totalPages, DocumentPage(emptyList(), PageRequest(size = size), totalElements).totalPages)
    }
}
