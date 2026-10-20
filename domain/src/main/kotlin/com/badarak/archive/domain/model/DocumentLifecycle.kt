package com.badarak.archive.domain.model

import java.time.Instant

/**
 * Where a document stands in its lifecycle: its status and the dates of its changes. A document is never updated before
 * it was created, and only a DELETED document has a deletion date. [moveTo] applies the transitions [DocumentStatus]
 * allows and returns a new lifecycle; the domain never reads the clock, so every date is supplied by the caller.
 */
data class DocumentLifecycle(val status: DocumentStatus, val createdAt: Instant, val updatedAt: Instant, val deletedAt: Instant? = null) {

    init {
        if (updatedAt.isBefore(createdAt)) throw InvalidDocumentException("updatedAt", "must not be before createdAt")
        if ((status == DocumentStatus.DELETED) != (deletedAt != null)) {
            throw InvalidDocumentException("deletedAt", "must be set if and only if the document is deleted")
        }
    }

    fun moveTo(target: DocumentStatus, at: Instant): DocumentLifecycle {
        if (!status.canTransitionTo(target)) throw InvalidStatusTransitionException(status, target)
        return DocumentLifecycle(target, createdAt, at, at.takeIf { target == DocumentStatus.DELETED })
    }

    companion object {
        fun start(at: Instant): DocumentLifecycle = DocumentLifecycle(DocumentStatus.INITIAL, at, at)
    }
}
