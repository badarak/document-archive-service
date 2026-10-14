package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentMetadataTest {

    @Test
    fun `keeps the supplied metadata`() {
        val metadata = DocumentMetadata(DocumentType("INVOICE"), "INV-2026-000123", "Customer invoice", mapOf("customerId" to "CUST-456"))

        assertEquals(DocumentType("INVOICE"), metadata.documentType)
        assertEquals("INV-2026-000123", metadata.businessReference)
        assertEquals("Customer invoice", metadata.description)
        assertEquals(mapOf("customerId" to "CUST-456"), metadata.tags)
    }

    @Test
    fun `optional metadata may be absent`() {
        val metadata = DocumentMetadata(DocumentType("INVOICE"), null, null, emptyMap())

        assertNull(metadata.businessReference)
        assertNull(metadata.description)
        assertTrue(metadata.tags.isEmpty())
    }

    @Test
    fun `later changes to the supplied tags do not affect the metadata`() {
        val tags = mutableMapOf("customerId" to "CUST-456")
        val metadata = DocumentMetadata(DocumentType("INVOICE"), null, null, tags)

        tags["customerId"] = "CUST-999"

        assertEquals(mapOf("customerId" to "CUST-456"), metadata.tags)
    }

    @Test
    fun `a blank business reference is refused when one is supplied`() {
        assertInvalid("businessReference") { metadata(businessReference = " ") }
    }

    @Test
    fun `fields longer than their maximum length are refused`() {
        assertInvalid("businessReference") { metadata(businessReference = "x".repeat(DocumentMetadata.BUSINESS_REFERENCE_MAX_LENGTH + 1)) }
        assertInvalid("description") { metadata(description = "x".repeat(DocumentMetadata.DESCRIPTION_MAX_LENGTH + 1)) }
    }

    @Test
    fun `fields of exactly their maximum length are accepted`() {
        val metadata = metadata(
            businessReference = "x".repeat(DocumentMetadata.BUSINESS_REFERENCE_MAX_LENGTH),
            description = "x".repeat(DocumentMetadata.DESCRIPTION_MAX_LENGTH),
        )

        assertEquals(DocumentMetadata.DESCRIPTION_MAX_LENGTH, metadata.description?.length)
    }

    private fun metadata(businessReference: String? = null, description: String? = null) =
        DocumentMetadata(DocumentType("INVOICE"), businessReference, description, emptyMap())

    private fun assertInvalid(field: String, block: () -> Unit) {
        assertEquals(field, assertThrows(InvalidDocumentException::class.java) { block() }.field)
    }
}
