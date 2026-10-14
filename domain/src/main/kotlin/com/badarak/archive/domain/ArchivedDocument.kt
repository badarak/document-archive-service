package com.badarak.archive.domain

import java.time.Instant

/**
 * Metadata and archiving state of a document whose content lives in object storage.
 *
 * Instances are immutable: a status change returns a new instance. The domain never reads the
 * clock, so every timestamp is supplied by the caller.
 *
 * The constructor rebuilds a document in any valid state (for instance from persistence);
 * use [create] to archive a new one.
 */
class ArchivedDocument(
    val id: DocumentId,
    val tenantId: TenantId,
    val documentType: DocumentType,
    val source: String,
    val businessReference: String?,
    val description: String?,
    tags: Map<String, String>,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val objectKey: String,
    val status: DocumentStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val tags: Map<String, String> = tags.toMap()

    init {
        requireText("source", source, SOURCE_MAX_LENGTH)
        businessReference?.let { requireText("businessReference", it, BUSINESS_REFERENCE_MAX_LENGTH) }
        description?.let { requireMaxLength("description", it, DESCRIPTION_MAX_LENGTH) }
        requireText("fileName", fileName, FILE_NAME_MAX_LENGTH)
        requireText("contentType", contentType, CONTENT_TYPE_MAX_LENGTH)
        requireText("objectKey", objectKey, OBJECT_KEY_MAX_LENGTH)
        if (sizeBytes <= 0) throw EmptyDocumentException(sizeBytes)
        if (updatedAt.isBefore(createdAt)) throw InvalidDocumentException("updatedAt", "must not be before createdAt")
    }

    fun markAvailable(at: Instant): ArchivedDocument = transitionTo(DocumentStatus.AVAILABLE, at)

    fun markFailed(at: Instant): ArchivedDocument = transitionTo(DocumentStatus.FAILED, at)

    private fun transitionTo(target: DocumentStatus, at: Instant): ArchivedDocument {
        if (!status.canTransitionTo(target)) throw InvalidStatusTransitionException(status, target)
        return ArchivedDocument(
            id = id,
            tenantId = tenantId,
            documentType = documentType,
            source = source,
            businessReference = businessReference,
            description = description,
            tags = tags,
            fileName = fileName,
            contentType = contentType,
            sizeBytes = sizeBytes,
            objectKey = objectKey,
            status = target,
            createdAt = createdAt,
            updatedAt = at,
        )
    }

    override fun equals(other: Any?): Boolean = other is ArchivedDocument && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ArchivedDocument(id=$id, documentType=$documentType, status=$status)"

    companion object {
        const val SOURCE_MAX_LENGTH = 100
        const val BUSINESS_REFERENCE_MAX_LENGTH = 255
        const val DESCRIPTION_MAX_LENGTH = 1000
        const val FILE_NAME_MAX_LENGTH = 255
        const val CONTENT_TYPE_MAX_LENGTH = 255
        const val OBJECT_KEY_MAX_LENGTH = 512

        fun create(
            id: DocumentId,
            tenantId: TenantId,
            documentType: DocumentType,
            source: String,
            businessReference: String?,
            description: String?,
            tags: Map<String, String>,
            fileName: String,
            contentType: String,
            sizeBytes: Long,
            objectKey: String,
            sizePolicy: DocumentSizePolicy,
            now: Instant,
        ): ArchivedDocument {
            sizePolicy.check(sizeBytes)
            return ArchivedDocument(
                id = id,
                tenantId = tenantId,
                documentType = documentType,
                source = source,
                businessReference = businessReference,
                description = description,
                tags = tags,
                fileName = fileName,
                contentType = contentType,
                sizeBytes = sizeBytes,
                objectKey = objectKey,
                status = DocumentStatus.INITIAL,
                createdAt = now,
                updatedAt = now,
            )
        }

        private fun requireText(field: String, value: String, maxLength: Int) {
            if (value.isBlank()) throw InvalidDocumentException(field, "must not be blank")
            requireMaxLength(field, value, maxLength)
        }

        private fun requireMaxLength(field: String, value: String, maxLength: Int) {
            if (value.length > maxLength) throw InvalidDocumentException(field, "must not exceed $maxLength characters")
        }
    }
}
