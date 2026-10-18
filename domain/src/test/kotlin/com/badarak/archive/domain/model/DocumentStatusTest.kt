package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class DocumentStatusTest {

    @Test
    fun `a document starts as pending`() {
        assertEquals(DocumentStatus.PENDING, DocumentStatus.INITIAL)
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @MethodSource("transitions")
    fun `a document only moves along its lifecycle`(from: DocumentStatus, to: DocumentStatus, allowed: Boolean) {
        assertEquals(allowed, from.canTransitionTo(to))
    }

    companion object {
        private val ALLOWED = setOf(
            DocumentStatus.PENDING to DocumentStatus.AVAILABLE,
            DocumentStatus.PENDING to DocumentStatus.FAILED,
            DocumentStatus.AVAILABLE to DocumentStatus.DELETING,
            DocumentStatus.DELETING to DocumentStatus.DELETED,
        )

        @JvmStatic
        fun transitions(): List<Arguments> = DocumentStatus.entries.flatMap { from ->
            DocumentStatus.entries.map { to -> Arguments.of(from, to, (from to to) in ALLOWED) }
        }
    }
}
