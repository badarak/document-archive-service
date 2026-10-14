package com.badarak.archive.infrastructure.adapter.persistence

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentContent
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import java.time.Instant

class DocumentRepositoryAdapterTest {

    private val repository = mockk<SpringDataArchivedDocumentRepository>()
    private val adapter = DocumentRepositoryAdapter(repository)

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
        owner = DocumentOwner(TenantId("acme"), "billing-service"),
        metadata = DocumentMetadata(DocumentType("INVOICE"), null, null, emptyMap()),
        content = DocumentContent("invoice.pdf", "application/pdf", 1, ObjectKey.generate()),
        sizePolicy = DocumentSizePolicy(),
        now = Instant.parse("2026-10-14T09:15:00Z"),
    )
}
