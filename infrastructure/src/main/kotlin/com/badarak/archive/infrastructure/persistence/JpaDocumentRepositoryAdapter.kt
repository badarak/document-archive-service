package com.badarak.archive.infrastructure.persistence

import com.badarak.archive.application.port.DocumentPersistenceException
import com.badarak.archive.application.port.DocumentRepositoryPort
import com.badarak.archive.domain.ArchivedDocument
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component

/** Each call runs in its own transaction, the one Spring Data opens around save. */
@Component
class JpaDocumentRepositoryAdapter(private val repository: ArchivedDocumentJpaRepository) : DocumentRepositoryPort {

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
