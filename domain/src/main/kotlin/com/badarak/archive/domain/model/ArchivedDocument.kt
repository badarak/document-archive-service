package com.badarak.archive.domain.model

import java.time.Instant

/**
 * Metadata and archiving state of a document whose content lives in object storage.
 *
 * Instances are immutable: a status change returns a new instance. The domain never reads the
 * clock, so every timestamp is supplied by the caller. Only a DELETED document has a deletion date.
 *
 * The constructor rebuilds a document in any valid state (for instance from persistence);
 * use [create] to archive a new one.
 */
class ArchivedDocument(
    val id: DocumentId,
    val owner: DocumentOwner,
    val metadata: DocumentMetadata,
    val file: StoredFile,
    val status: DocumentStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {

    init {
        if (updatedAt.isBefore(createdAt)) throw InvalidDocumentException("updatedAt", "must not be before createdAt")
        if ((status == DocumentStatus.DELETED) != (deletedAt != null)) {
            throw InvalidDocumentException("deletedAt", "must be set if and only if the document is deleted")
        }
    }

    fun markAvailable(at: Instant): ArchivedDocument = transitionTo(DocumentStatus.AVAILABLE, at)

    fun markFailed(at: Instant): ArchivedDocument = transitionTo(DocumentStatus.FAILED, at)

    fun markDeleting(at: Instant): ArchivedDocument = transitionTo(DocumentStatus.DELETING, at)

    fun markDeleted(at: Instant): ArchivedDocument = transitionTo(DocumentStatus.DELETED, at)

    private fun transitionTo(target: DocumentStatus, at: Instant): ArchivedDocument {
        if (!status.canTransitionTo(target)) throw InvalidStatusTransitionException(status, target)
        val deletedAt = at.takeIf { target == DocumentStatus.DELETED }
        return ArchivedDocument(id, owner, metadata, file, target, createdAt, at, deletedAt)
    }

    override fun equals(other: Any?): Boolean = other is ArchivedDocument && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ArchivedDocument(id=$id, documentType=${metadata.documentType}, status=$status)"

    companion object {
        fun create(
            id: DocumentId,
            owner: DocumentOwner,
            metadata: DocumentMetadata,
            file: StoredFile,
            sizePolicy: DocumentSizePolicy,
            now: Instant,
        ): ArchivedDocument {
            sizePolicy.check(file.sizeBytes)
            return ArchivedDocument(id, owner, metadata, file, DocumentStatus.INITIAL, now, now)
        }
    }
}
