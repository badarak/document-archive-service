package com.badarak.archive.infrastructure.persistence

import com.badarak.archive.application.port.DocumentPersistenceException
import com.badarak.archive.domain.ArchivedDocument
import com.badarak.archive.domain.DocumentId
import com.badarak.archive.domain.DocumentSizePolicy
import com.badarak.archive.domain.DocumentType
import com.badarak.archive.domain.ObjectKey
import com.badarak.archive.domain.TenantId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant

class JpaDocumentRepositoryAdapterTest {

    private val repository = mockk<ArchivedDocumentJpaRepository>()
    private val adapter = JpaDocumentRepositoryAdapter(repository)

    @Test
    fun `a database failure becomes a document persistence error`() {
        val failure = DataAccessResourceFailureException("Connection refused")
        every { repository.save(any()) } throws failure
        val document = document()

        val exception = assertThrows(DocumentPersistenceException::class.java) { adapter.save(document) }

        assertSame(failure, exception.cause)
        assertEquals("Could not save document ${document.id}", exception.message)
    }

    private fun document(): ArchivedDocument = ArchivedDocument.create(
        id = DocumentId.generate(),
        tenantId = TenantId("acme"),
        documentType = DocumentType("INVOICE"),
        source = "billing-service",
        businessReference = null,
        description = null,
        tags = emptyMap(),
        fileName = "invoice.pdf",
        contentType = "application/pdf",
        sizeBytes = 1,
        objectKey = ObjectKey.generate(),
        sizePolicy = DocumentSizePolicy(),
        now = Instant.parse("2026-10-14T09:15:00Z"),
    )
}
