package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DocumentStatusTest {

    @Test
    fun `a document starts as pending`() {
        assertEquals(DocumentStatus.PENDING, DocumentStatus.INITIAL)
    }

    @ParameterizedTest
    @CsvSource(
        "PENDING, PENDING, false",
        "PENDING, AVAILABLE, true",
        "PENDING, FAILED, true",
        "AVAILABLE, PENDING, false",
        "AVAILABLE, AVAILABLE, false",
        "AVAILABLE, FAILED, false",
        "FAILED, PENDING, false",
        "FAILED, AVAILABLE, false",
        "FAILED, FAILED, false",
    )
    fun `only a pending document can move to another status`(from: DocumentStatus, to: DocumentStatus, allowed: Boolean) {
        assertEquals(allowed, from.canTransitionTo(to))
    }
}
