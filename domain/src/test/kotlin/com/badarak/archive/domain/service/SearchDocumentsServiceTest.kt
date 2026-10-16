package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.DocumentPage
import com.badarak.archive.domain.model.DocumentSearchCriteria
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.PageRequest
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.inbound.AuthenticatedCaller
import com.badarak.archive.domain.port.inbound.DocumentSearchQuery
import com.badarak.archive.domain.port.outbound.DocumentRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SearchDocumentsServiceTest {

    private val documents = mockk<DocumentRepository>()
    private val service = SearchDocumentsService(documents)

    @Test
    fun `searches the available documents of the caller's tenant with the requested criteria and page`() {
        val criteria = DocumentSearchCriteria(documentType = DocumentType("INVOICE"))
        val request = PageRequest(2, 10)
        val page = DocumentPage(emptyList(), request, 0)
        val caller = AuthenticatedCaller(TenantId("acme"), "billing-service")
        every { documents.search(TenantId("acme"), DocumentStatus.AVAILABLE, criteria, request) } returns page

        assertSame(page, service.search(DocumentSearchQuery(caller, criteria, request)))
    }
}
