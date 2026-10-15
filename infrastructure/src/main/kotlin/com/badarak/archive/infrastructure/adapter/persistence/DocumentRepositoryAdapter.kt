package com.badarak.archive.infrastructure.adapter.persistence

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentContent
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.DocumentRepository
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Repository

/**
 * Each call runs in its own transaction, the one Spring Data opens around it. Documents are rebuilt through their
 * constructor, so a row breaking a current domain rule fails to load.
 */
@Repository
class DocumentRepositoryAdapter(private val repository: SpringDataArchivedDocumentRepository) : DocumentRepository {

    override fun save(document: ArchivedDocument) {
        try {
            repository.save(document.toEntity())
        } catch (e: DataAccessException) {
            throw DocumentPersistenceException("Could not save document ${document.id}", e)
        }
    }

    override fun findByIdAndTenant(id: DocumentId, tenantId: TenantId): ArchivedDocument? = try {
        repository.findByIdAndTenantId(id.value, tenantId.value)?.toDomain()
    } catch (e: DataAccessException) {
        throw DocumentPersistenceException("Could not read document $id", e)
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

private fun ArchivedDocumentEntity.toDomain(): ArchivedDocument = ArchivedDocument(
    id = DocumentId(id),
    owner = DocumentOwner(TenantId(tenantId), source),
    metadata = DocumentMetadata(DocumentType(documentType), businessReference, description, tags),
    content = DocumentContent(fileName, contentType, sizeBytes, ObjectKey(objectKey)),
    status = DocumentStatus.valueOf(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
