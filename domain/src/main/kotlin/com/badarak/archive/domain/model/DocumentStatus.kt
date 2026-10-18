package com.badarak.archive.domain.model

/**
 * Lifecycle of an archived document.
 *
 * A document starts as [PENDING] while its content is being stored, then becomes either [AVAILABLE] or [FAILED]; a
 * failed archiving is retried as a new document. An available document is deleted in two steps: [DELETING] while its
 * content is being removed from the storage, then [DELETED], which keeps its metadata. [FAILED] and [DELETED] are
 * terminal.
 */
enum class DocumentStatus {
    PENDING,
    AVAILABLE,
    FAILED,
    DELETING,
    DELETED,
    ;

    fun canTransitionTo(target: DocumentStatus): Boolean = when (this) {
        PENDING -> target == AVAILABLE || target == FAILED
        AVAILABLE -> target == DELETING
        DELETING -> target == DELETED
        FAILED, DELETED -> false
    }

    companion object {
        val INITIAL = PENDING
    }
}
