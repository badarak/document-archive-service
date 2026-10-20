package com.badarak.archive.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class DocumentLifecycleTest {

    private val createdAt = Instant.parse("2026-10-12T09:15:00Z")
    private val later = Instant.parse("2026-10-12T09:15:05Z")

    @Nested
    inner class Start {

        @Test
        fun `a lifecycle starts pending, created and updated at the same instant`() {
            assertEquals(DocumentLifecycle(DocumentStatus.PENDING, createdAt, createdAt), DocumentLifecycle.start(createdAt))
        }
    }

    @Nested
    inner class Validation {

        @Test
        fun `a document cannot be updated before it was created`() {
            val exception = assertThrows(InvalidDocumentException::class.java) {
                DocumentLifecycle(DocumentStatus.AVAILABLE, createdAt, createdAt.minusSeconds(1))
            }

            assertEquals("updatedAt", exception.field)
        }

        @Test
        fun `a deleted document must have a deletion date`() {
            val exception = assertThrows(InvalidDocumentException::class.java) {
                DocumentLifecycle(DocumentStatus.DELETED, createdAt, later)
            }

            assertEquals("deletedAt", exception.field)
        }

        @ParameterizedTest
        @EnumSource(DocumentStatus::class, names = ["DELETED"], mode = EnumSource.Mode.EXCLUDE)
        fun `a document that is not deleted has no deletion date`(status: DocumentStatus) {
            val exception = assertThrows(InvalidDocumentException::class.java) { DocumentLifecycle(status, createdAt, later, later) }

            assertEquals("deletedAt", exception.field)
        }
    }

    @Nested
    inner class Transitions {

        @Test
        fun `a transition keeps the creation date and dates the update`() {
            val moved = DocumentLifecycle.start(createdAt).moveTo(DocumentStatus.AVAILABLE, later)

            assertEquals(DocumentLifecycle(DocumentStatus.AVAILABLE, createdAt, later), moved)
        }

        @Test
        fun `only the transition to deleted dates the deletion`() {
            val deleting = DocumentLifecycle(DocumentStatus.AVAILABLE, createdAt, createdAt).moveTo(DocumentStatus.DELETING, later)
            val deleted = deleting.moveTo(DocumentStatus.DELETED, later.plusSeconds(1))

            assertNull(deleting.deletedAt)
            assertEquals(later.plusSeconds(1), deleted.deletedAt)
        }

        @Test
        fun `a transition the status does not allow is refused`() {
            val exception = assertThrows(InvalidStatusTransitionException::class.java) {
                DocumentLifecycle.start(createdAt).moveTo(DocumentStatus.DELETED, later)
            }

            assertEquals(DocumentStatus.PENDING, exception.from)
            assertEquals(DocumentStatus.DELETED, exception.to)
        }
    }
}
