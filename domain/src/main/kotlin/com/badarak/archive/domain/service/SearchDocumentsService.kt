package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.DocumentPage
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.port.inbound.DocumentSearchQuery
import com.badarak.archive.domain.port.inbound.SearchDocumentsUseCase
import com.badarak.archive.domain.port.outbound.DocumentRepository

class SearchDocumentsService(private val documents: DocumentRepository) : SearchDocumentsUseCase {

    override fun search(query: DocumentSearchQuery): DocumentPage =
        documents.search(query.caller.tenantId, DocumentStatus.AVAILABLE, query.criteria, query.page)
}
