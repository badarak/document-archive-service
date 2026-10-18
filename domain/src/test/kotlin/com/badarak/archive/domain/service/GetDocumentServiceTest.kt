package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentNotFoundException
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.inbound.AuthenticatedCaller
import com.badarak.archive.domain.port.inbound.DocumentQuery
import com.badarak.archive.domain.port.outbound.DocumentRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class GetDocumentServiceTest {

    private val documents = mockk<DocumentRepository>()
    private val service = GetDocumentService(documents)

    private val id = DocumentId.generate()
    private val acme = AuthenticatedCaller(TenantId("acme"), "billing-service")

    @Test
    fun `returns an available document of the caller's tenant`() {
        val document = document(DocumentStatus.AVAILABLE)
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns document

        assertSame(document, service.get(DocumentQuery(acme, id)))
    }

    @Test
    fun `a document that does not exist for the caller's tenant is not found`() {
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns null

        val exception = assertThrows(DocumentNotFoundException::class.java) { service.get(DocumentQuery(acme, id)) }

        assertEquals(id, exception.id)
    }

    @ParameterizedTest
    @EnumSource(DocumentStatus::class, names = ["AVAILABLE"], mode = EnumSource.Mode.EXCLUDE)
    fun `a document that is not available is not found`(status: DocumentStatus) {
        every { documents.findByIdAndTenant(id, TenantId("acme")) } returns document(status)

        assertThrows(DocumentNotFoundException::class.java) { service.get(DocumentQuery(acme, id)) }
    }

    private fun document(status: DocumentStatus) = ArchivedDocument(
        id = id,
        owner = DocumentOwner(TenantId("acme"), "billing-service"),
        metadata = DocumentMetadata(DocumentType("INVOICE"), null, null, emptyMap()),
        file = StoredFile("invoice.pdf", "application/pdf", 1, ObjectKey.generate()),
        status = status,
        createdAt = Instant.parse("2026-10-15T09:15:00Z"),
        updatedAt = Instant.parse("2026-10-15T09:15:00Z"),
        deletedAt = Instant.parse("2026-10-15T09:15:00Z").takeIf { status == DocumentStatus.DELETED },
    )
}
