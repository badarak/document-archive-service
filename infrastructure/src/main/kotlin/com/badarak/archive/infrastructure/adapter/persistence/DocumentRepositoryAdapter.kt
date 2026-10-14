package com.badarak.archive.infrastructure.adapter.persistence

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.DocumentRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Repository

/** Each call runs in its own transaction, the one Spring Data opens around save. */
@Repository
class DocumentRepositoryAdapter(private val repository: SpringDataArchivedDocumentRepository) : DocumentRepository {

    override fun save(document: ArchivedDocument) {
        try {
            repository.save(document.toEntity())
        } catch (e: DataAccessException) {
            throw DocumentPersistenceException("Could not save document ${document.id}", e)
        }
    }
}

private fun ArchivedDocument.toEntity(): ArchivedDocumentEntity = ArchivedDocumentEntity(
    id = id.value,
    tenantId = owner.tenantId.value,
    documentType = metadata.documentType.value,
    source = owner.source,
    businessReference = metadata.businessReference,
    description = metadata.description,
    tags = metadata.tags,
    fileName = content.fileName,
    contentType = content.contentType,
    sizeBytes = content.sizeBytes,
    objectKey = content.objectKey.value,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
