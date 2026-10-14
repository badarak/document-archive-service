package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class ArchivedDocumentTest {

    private val createdAt = Instant.parse("2026-10-12T09:15:00Z")
    private val later = Instant.parse("2026-10-12T09:15:05Z")

    @Nested
    inner class Creation {

        @Test
        fun `a new document is pending and keeps the supplied metadata`() {
            val id = DocumentId.generate()

            val document = newDocument(id = id)

            assertEquals(id, document.id)
            assertEquals(TenantId("acme"), document.tenantId)
            assertEquals(DocumentType("INVOICE"), document.documentType)
            assertEquals("BILLING-SERVICE", document.source)
            assertEquals("INV-2026-000123", document.businessReference)
            assertEquals("Customer invoice", document.description)
            assertEquals(mapOf("customerId" to "CUST-456"), document.tags)
            assertEquals("invoice-123.pdf", document.fileName)
            assertEquals("application/pdf", document.contentType)
            assertEquals(248_120, document.sizeBytes)
            assertEquals(ObjectKey("documents/7d7d57e1"), document.objectKey)
            assertEquals(DocumentStatus.PENDING, document.status)
            assertEquals(createdAt, document.createdAt)
            assertEquals(createdAt, document.updatedAt)
        }

        @Test
        fun `optional metadata may be absent`() {
            val document = newDocument(businessReference = null, description = null, tags = emptyMap())

            assertEquals(null, document.businessReference)
            assertEquals(null, document.description)
            assertTrue(document.tags.isEmpty())
        }

        @Test
        fun `a document above the size limit is refused`() {
            assertThrows(DocumentTooLargeException::class.java) {
                newDocument(sizeBytes = 1_001, sizePolicy = DocumentSizePolicy(maxSizeBytes = 1_000))
            }
        }

        @Test
        fun `an empty document is refused`() {
            assertThrows(EmptyDocumentException::class.java) { newDocument(sizeBytes = 0) }
        }

        @Test
        fun `later changes to the supplied tags do not affect the document`() {
            val tags = mutableMapOf("customerId" to "CUST-456")
            val document = newDocument(tags = tags)

            tags["customerId"] = "CUST-999"

            assertEquals(mapOf("customerId" to "CUST-456"), document.tags)
        }
    }

    @Nested
    inner class Validation {

        @Test
        fun `blank mandatory fields are refused`() {
            assertInvalid("source") { newDocument(source = " ") }
            assertInvalid("fileName") { newDocument(fileName = "") }
            assertInvalid("contentType") { newDocument(contentType = " ") }
        }

        @Test
        fun `a blank business reference is refused when one is supplied`() {
            assertInvalid("businessReference") { newDocument(businessReference = " ") }
        }

        @Test
        fun `fields longer than their maximum length are refused`() {
            assertInvalid("source") { newDocument(source = "x".repeat(ArchivedDocument.SOURCE_MAX_LENGTH + 1)) }
            assertInvalid("businessReference") {
                newDocument(businessReference = "x".repeat(ArchivedDocument.BUSINESS_REFERENCE_MAX_LENGTH + 1))
            }
            assertInvalid("description") { newDocument(description = "x".repeat(ArchivedDocument.DESCRIPTION_MAX_LENGTH + 1)) }
            assertInvalid("fileName") { newDocument(fileName = "x".repeat(ArchivedDocument.FILE_NAME_MAX_LENGTH + 1)) }
            assertInvalid("contentType") { newDocument(contentType = "x".repeat(ArchivedDocument.CONTENT_TYPE_MAX_LENGTH + 1)) }
        }

        @Test
        fun `fields of exactly their maximum length are accepted`() {
            val document = newDocument(
                source = "x".repeat(ArchivedDocument.SOURCE_MAX_LENGTH),
                description = "x".repeat(ArchivedDocument.DESCRIPTION_MAX_LENGTH),
            )

            assertEquals(ArchivedDocument.SOURCE_MAX_LENGTH, document.source.length)
        }

        @Test
        fun `a document cannot be updated before it was created`() {
            assertInvalid("updatedAt") { existingDocument(status = DocumentStatus.AVAILABLE, updatedAt = createdAt.minusSeconds(1)) }
        }

        @Test
        fun `an existing document cannot be empty`() {
            assertThrows(EmptyDocumentException::class.java) { existingDocument(status = DocumentStatus.AVAILABLE, sizeBytes = 0) }
        }

        private fun assertInvalid(field: String, block: () -> Unit) {
            val exception = assertThrows(InvalidDocumentException::class.java) { block() }

            assertEquals(field, exception.field)
        }
    }

    @Nested
    inner class Transitions {

        @Test
        fun `a pending document becomes available`() {
            val pending = newDocument()

            val available = pending.markAvailable(later)

            assertEquals(DocumentStatus.AVAILABLE, available.status)
            assertEquals(later, available.updatedAt)
            assertEquals(createdAt, available.createdAt)
            assertEquals(pending.id, available.id)
            assertEquals(pending.tenantId, available.tenantId)
            assertEquals(pending.objectKey, available.objectKey)
        }

        @Test
        fun `a pending document becomes failed`() {
            val failed = newDocument().markFailed(later)

            assertEquals(DocumentStatus.FAILED, failed.status)
            assertEquals(later, failed.updatedAt)
        }

        @Test
        fun `a transition leaves the original document unchanged`() {
            val pending = newDocument()

            pending.markAvailable(later)

            assertEquals(DocumentStatus.PENDING, pending.status)
            assertEquals(createdAt, pending.updatedAt)
        }

        @ParameterizedTest
        @EnumSource(DocumentStatus::class, names = ["AVAILABLE", "FAILED"])
        fun `a document in a terminal status cannot change status`(status: DocumentStatus) {
            val document = existingDocument(status = status)

            val toAvailable = assertThrows(InvalidStatusTransitionException::class.java) { document.markAvailable(later) }
            val toFailed = assertThrows(InvalidStatusTransitionException::class.java) { document.markFailed(later) }

            assertEquals(status, toAvailable.from)
            assertEquals(DocumentStatus.AVAILABLE, toAvailable.to)
            assertEquals(status, toFailed.from)
            assertEquals(DocumentStatus.FAILED, toFailed.to)
        }
    }

    @Nested
    inner class Identity {

        @Test
        fun `documents are equal when they share the same identifier`() {
            val pending = newDocument()
            val available = pending.markAvailable(later)

            assertEquals(pending, available)
            assertEquals(pending.hashCode(), available.hashCode())
        }

        @Test
        fun `documents with different identifiers are not equal`() {
            assertNotEquals(newDocument(), newDocument())
            assertFalse(newDocument().equals("not a document"))
        }

        @Test
        fun `toString identifies the document without exposing its metadata`() {
            val document = newDocument()

            assertEquals("ArchivedDocument(id=${document.id}, documentType=INVOICE, status=PENDING)", document.toString())
        }
    }

    private fun newDocument(
        id: DocumentId = DocumentId.generate(),
        source: String = "BILLING-SERVICE",
        businessReference: String? = "INV-2026-000123",
        description: String? = "Customer invoice",
        tags: Map<String, String> = mapOf("customerId" to "CUST-456"),
        fileName: String = "invoice-123.pdf",
        contentType: String = "application/pdf",
        sizeBytes: Long = 248_120,
        objectKey: ObjectKey = ObjectKey("documents/7d7d57e1"),
        sizePolicy: DocumentSizePolicy = DocumentSizePolicy(),
    ): ArchivedDocument = ArchivedDocument.create(
        id = id,
        tenantId = TenantId("acme"),
        documentType = DocumentType("INVOICE"),
        source = source,
        businessReference = businessReference,
        description = description,
        tags = tags,
        fileName = fileName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        objectKey = objectKey,
        sizePolicy = sizePolicy,
        now = createdAt,
    )

    private fun existingDocument(status: DocumentStatus, sizeBytes: Long = 248_120, updatedAt: Instant = createdAt): ArchivedDocument =
        ArchivedDocument(
            id = DocumentId.generate(),
            tenantId = TenantId("acme"),
            documentType = DocumentType("INVOICE"),
            source = "BILLING-SERVICE",
            businessReference = null,
            description = null,
            tags = emptyMap(),
            fileName = "invoice-123.pdf",
            contentType = "application/pdf",
            sizeBytes = sizeBytes,
            objectKey = ObjectKey("documents/7d7d57e1"),
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
