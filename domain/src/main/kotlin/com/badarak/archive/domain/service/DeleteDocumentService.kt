package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.DocumentNotFoundException
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.port.inbound.DeleteDocumentUseCase
import com.badarak.archive.domain.port.inbound.DocumentQuery
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import java.time.Clock

class DeleteDocumentService(private val documents: DocumentRepository, private val storage: ObjectStorage, private val clock: Clock) :
    DeleteDocumentUseCase {

    override fun delete(query: DocumentQuery) {
        val document = documents.findByIdAndTenant(query.id, query.caller.tenantId) ?: throw DocumentNotFoundException(query.id)
        val deleting = when (document.status) {
            DocumentStatus.AVAILABLE -> document.markDeleting(clock.instant()).also { documents.save(it) }
            DocumentStatus.DELETING -> document
            DocumentStatus.DELETED -> return
            DocumentStatus.PENDING, DocumentStatus.FAILED -> throw DocumentNotFoundException(query.id)
        }
        storage.delete(deleting.file.objectKey)
        documents.save(deleting.markDeleted(clock.instant()))
    }
}
