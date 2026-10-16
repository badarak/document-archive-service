package com.badarak.archive.domain.model

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
    val owner: DocumentOwner,
    val metadata: DocumentMetadata,
    val file: StoredFile,
    val status: DocumentStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {

    init {
        if (updatedAt.isBefore(createdAt)) throw InvalidDocumentException("updatedAt", "must not be before createdAt")
    }

    fun markAvailable(at: Instant): ArchivedDocument = transitionTo(DocumentStatus.AVAILABLE, at)

    fun markFailed(at: Instant): ArchivedDocument = transitionTo(DocumentStatus.FAILED, at)

    private fun transitionTo(target: DocumentStatus, at: Instant): ArchivedDocument {
        if (!status.canTransitionTo(target)) throw InvalidStatusTransitionException(status, target)
        return ArchivedDocument(id, owner, metadata, file, target, createdAt, at)
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
