package com.badarak.archive.infrastructure.adapter.persistence

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentMetadata
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentPage
import com.badarak.archive.domain.model.DocumentSearchCriteria
import com.badarak.archive.domain.model.DocumentStatus
import com.badarak.archive.domain.model.DocumentType
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.model.PageRequest
import com.badarak.archive.domain.model.StoredFile
import com.badarak.archive.domain.model.TenantId
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.DocumentRepository
import org.springframework.dao.DataAccessException
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Repository
import java.time.Instant
import org.springframework.data.domain.PageRequest as SpringPageRequest

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

    override fun search(tenantId: TenantId, status: DocumentStatus, criteria: DocumentSearchCriteria, page: PageRequest): DocumentPage =
        try {
            val result = repository.findAll(matching(tenantId, status, criteria), SpringPageRequest.of(page.page, page.size, NEWEST_FIRST))
            DocumentPage(result.content.map { it.toDomain() }, page, result.totalElements)
        } catch (e: DataAccessException) {
            throw DocumentPersistenceException("Could not search the documents of tenant $tenantId", e)
        }
}

private val NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id")

private fun matching(tenantId: TenantId, status: DocumentStatus, criteria: DocumentSearchCriteria) =
    Specification<ArchivedDocumentEntity> { root, _, builder ->
        builder.and(
            *listOfNotNull(
                builder.equal(root.get<String>("tenantId"), tenantId.value),
                builder.equal(root.get<String>("status"), status.name),
                criteria.documentType?.let { builder.equal(root.get<String>("documentType"), it.value) },
                criteria.source?.let { builder.equal(root.get<String>("source"), it) },
                criteria.businessReference?.let { builder.equal(root.get<String>("businessReference"), it) },
                criteria.createdFrom?.let { builder.greaterThanOrEqualTo(root.get<Instant>("createdAt"), it) },
                criteria.createdTo?.let { builder.lessThanOrEqualTo(root.get<Instant>("createdAt"), it) },
            ).toTypedArray(),
        )
    }

private fun ArchivedDocument.toEntity(): ArchivedDocumentEntity = ArchivedDocumentEntity(
    id = id.value,
    tenantId = owner.tenantId.value,
    documentType = metadata.documentType.value,
    source = owner.source,
    businessReference = metadata.businessReference,
    description = metadata.description,
    tags = metadata.tags,
    fileName = file.fileName,
    contentType = file.contentType,
    sizeBytes = file.sizeBytes,
    objectKey = file.objectKey.value,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun ArchivedDocumentEntity.toDomain(): ArchivedDocument = ArchivedDocument(
    id = DocumentId(id),
    owner = DocumentOwner(TenantId(tenantId), source),
    metadata = DocumentMetadata(DocumentType(documentType), businessReference, description, tags),
    file = StoredFile(fileName, contentType, sizeBytes, ObjectKey(objectKey)),
    status = DocumentStatus.valueOf(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
