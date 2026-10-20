package com.badarak.archive.domain.model

import java.time.Instant

/**
 * Metadata and archiving state of a document whose content lives in object storage.
 *
 * Instances are immutable: a status change returns a new instance, whose [lifecycle] enforces the allowed transitions and
 * the consistency of the dates. The status and the dates are also exposed directly, for readability.
 *
 * The constructor rebuilds a document in any valid state (for instance from persistence); use [create] to archive a new one.
 */
class ArchivedDocument(
    val id: DocumentId,
    val owner: DocumentOwner,
    val metadata: DocumentMetadata,
    val file: StoredFile,
    val lifecycle: DocumentLifecycle,
) {

    val status: DocumentStatus get() = lifecycle.status

    val createdAt: Instant get() = lifecycle.createdAt

    val updatedAt: Instant get() = lifecycle.updatedAt

    val deletedAt: Instant? get() = lifecycle.deletedAt

    fun markAvailable(at: Instant): ArchivedDocument = moveTo(DocumentStatus.AVAILABLE, at)

    fun markFailed(at: Instant): ArchivedDocument = moveTo(DocumentStatus.FAILED, at)

    fun markDeleting(at: Instant): ArchivedDocument = moveTo(DocumentStatus.DELETING, at)

    fun markDeleted(at: Instant): ArchivedDocument = moveTo(DocumentStatus.DELETED, at)

    private fun moveTo(target: DocumentStatus, at: Instant): ArchivedDocument =
        ArchivedDocument(id, owner, metadata, file, lifecycle.moveTo(target, at))

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
            return ArchivedDocument(id, owner, metadata, file, DocumentLifecycle.start(now))
        }
    }
}
