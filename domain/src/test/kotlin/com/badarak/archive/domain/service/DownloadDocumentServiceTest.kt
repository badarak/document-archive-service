package com.badarak.archive.domain.service

import com.badarak.archive.domain.fixture.aDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentNotFoundException
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.inbound.AuthenticatedCaller
import com.badarak.archive.domain.port.inbound.DocumentQuery
import com.badarak.archive.domain.port.inbound.GetDocumentUseCase
import com.badarak.archive.domain.port.outbound.MissingObjectException
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DownloadDocumentServiceTest {

    private val getDocument = mockk<GetDocumentUseCase>()
    private val storage = mockk<ObjectStorage>()
    private val service = DownloadDocumentService(getDocument, storage)

    private val query = DocumentQuery(AuthenticatedCaller(TenantId("acme"), "billing-service"), DocumentId.generate())
    private val key = ObjectKey("documents/7d7d57e1")
    private val document = aDocument(id = query.id, file = StoredFile("invoice.pdf", "application/pdf", 3, key))

    @Test
    fun `opens the stored content of a document the caller may read`() {
        val content = "abc".byteInputStream()
        every { getDocument.get(query) } returns document
        every { storage.open(key) } returns content

        val download = service.download(query)

        assertSame(document, download.document)
        assertSame(content, download.content)
    }

    @Test
    fun `the storage is not touched when the caller may not read the document`() {
        every { getDocument.get(query) } throws DocumentNotFoundException(query.id)

        assertThrows(DocumentNotFoundException::class.java) { service.download(query) }

        verify(exactly = 0) { storage.open(any()) }
    }

    @Test
    fun `a missing content is reported as such`() {
        every { getDocument.get(query) } returns document
        every { storage.open(key) } throws MissingObjectException(key, RuntimeException("NoSuchKey"))

        assertThrows(MissingObjectException::class.java) { service.download(query) }
    }

    @Test
    fun `an unreachable storage is reported as such`() {
        every { getDocument.get(query) } returns document
        every { storage.open(key) } throws ObjectStorageException("Storage down", RuntimeException())

        assertThrows(ObjectStorageException::class.java) { service.download(query) }
    }
}
