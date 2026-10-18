package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

class DocumentSearchCriteriaTest {

    private val morning = Instant.parse("2026-10-15T09:00:00Z")
    private val evening = Instant.parse("2026-10-15T18:00:00Z")

    @Test
    fun `no filter is set by default`() {
        val criteria = DocumentSearchCriteria()

        assertNull(criteria.documentType)
        assertNull(criteria.source)
        assertNull(criteria.businessReference)
        assertNull(criteria.createdFrom)
        assertNull(criteria.createdTo)
    }

    @Test
    fun `keeps every filter`() {
        val criteria = DocumentSearchCriteria(DocumentType("INVOICE"), "billing-service", "INV-1", morning, evening)

        assertEquals(DocumentType("INVOICE"), criteria.documentType)
        assertEquals("billing-service", criteria.source)
        assertEquals("INV-1", criteria.businessReference)
        assertEquals(morning, criteria.createdFrom)
        assertEquals(evening, criteria.createdTo)
    }

    @Test
    fun `accepts a period of a single instant`() {
        assertEquals(morning, DocumentSearchCriteria(createdFrom = morning, createdTo = morning).createdTo)
    }

    @Test
    fun `rejects a period that ends before it starts`() {
        assertInvalid("createdFrom") { DocumentSearchCriteria(createdFrom = evening, createdTo = morning) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `rejects a blank source`(source: String) {
        assertInvalid("source") { DocumentSearchCriteria(source = source) }
    }

    @Test
    fun `rejects a source longer than a stored one can be`() {
        assertInvalid("source") { DocumentSearchCriteria(source = "s".repeat(DocumentOwner.SOURCE_MAX_LENGTH + 1)) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `rejects a blank business reference`(reference: String) {
        assertInvalid("businessReference") { DocumentSearchCriteria(businessReference = reference) }
    }

    @Test
    fun `rejects a business reference longer than a stored one can be`() {
        val reference = "r".repeat(DocumentMetadata.BUSINESS_REFERENCE_MAX_LENGTH + 1)

        assertInvalid("businessReference") { DocumentSearchCriteria(businessReference = reference) }
    }

    private fun assertInvalid(parameter: String, block: () -> Unit) {
        assertEquals(parameter, assertThrows(InvalidSearchException::class.java) { block() }.parameter)
    }
}
