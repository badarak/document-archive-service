package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentNotFoundException
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.port.inbound.GetDocumentQuery
import com.badarak.archive.domain.port.inbound.GetDocumentUseCase
import com.badarak.archive.domain.port.outbound.DocumentRepository

class GetDocumentService(private val documents: DocumentRepository) : GetDocumentUseCase {

    override fun get(query: GetDocumentQuery): ArchivedDocument = documents.findByIdAndTenant(query.id, query.caller.tenantId)
        ?.takeIf { it.status == DocumentStatus.AVAILABLE }
        ?: throw DocumentNotFoundException(query.id)
}
