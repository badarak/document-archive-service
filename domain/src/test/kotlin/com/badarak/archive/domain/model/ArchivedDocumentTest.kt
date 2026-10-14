package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class ArchivedDocumentTest {

    private val createdAt = Instant.parse("2026-10-12T09:15:00Z")
    private val later = Instant.parse("2026-10-12T09:15:05Z")

    private val owner = DocumentOwner(TenantId("acme"), "BILLING-SERVICE")
    private val metadata =
        DocumentMetadata(DocumentType("INVOICE"), "INV-2026-000123", "Customer invoice", mapOf("customerId" to "CUST-456"))
    private val content = DocumentContent("invoice-123.pdf", "application/pdf", 248_120, ObjectKey("documents/7d7d57e1"))

    @Nested
    inner class Creation {

        @Test
        fun `a new document is pending and keeps its owner, metadata and content`() {
            val id = DocumentId.generate()

            val document = newDocument(id = id)

            assertEquals(id, document.id)
            assertSame(owner, document.owner)
            assertSame(metadata, document.metadata)
            assertSame(content, document.content)
            assertEquals(DocumentStatus.PENDING, document.status)
            assertEquals(createdAt, document.createdAt)
            assertEquals(createdAt, document.updatedAt)
        }

        @Test
        fun `a document above the size limit is refused`() {
            assertThrows(DocumentTooLargeException::class.java) {
                newDocument(content = content.copy(sizeBytes = 1_001), sizePolicy = DocumentSizePolicy(maxSizeBytes = 1_000))
            }
        }
    }

    @Nested
    inner class Validation {

        @Test
        fun `a document cannot be updated before it was created`() {
            val exception = assertThrows(InvalidDocumentException::class.java) {
                existingDocument(status = DocumentStatus.AVAILABLE, updatedAt = createdAt.minusSeconds(1))
            }

            assertEquals("updatedAt", exception.field)
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
            assertSame(pending.owner, available.owner)
            assertSame(pending.metadata, available.metadata)
            assertSame(pending.content, available.content)
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
        content: DocumentContent = this.content,
        sizePolicy: DocumentSizePolicy = DocumentSizePolicy(),
    ): ArchivedDocument = ArchivedDocument.create(id, owner, metadata, content, sizePolicy, createdAt)

    private fun existingDocument(status: DocumentStatus, updatedAt: Instant = createdAt): ArchivedDocument =
        ArchivedDocument(DocumentId.generate(), owner, metadata, content, status, createdAt, updatedAt)
}
