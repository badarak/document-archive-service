package com.badarak.archive.infrastructure.adapter.persistence

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.DocumentRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component

/** Each call runs in its own transaction, the one Spring Data opens around save. */
@Component
class JpaDocumentRepositoryAdapter(private val repository: ArchivedDocumentJpaRepository) : DocumentRepository {

    override fun save(document: ArchivedDocument) {
        try {
            repository.save(document.toJpaEntity())
        } catch (e: DataAccessException) {
            throw DocumentPersistenceException("Could not save document ${document.id}", e)
        }
    }
}

private fun ArchivedDocument.toJpaEntity(): ArchivedDocumentJpaEntity = ArchivedDocumentJpaEntity(
    id = id.value,
    tenantId = tenantId.value,
    documentType = documentType.value,
    source = source,
    businessReference = businessReference,
    description = description,
    tags = tags,
    fileName = fileName,
    contentType = contentType,
    sizeBytes = sizeBytes,
    objectKey = objectKey.value,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
