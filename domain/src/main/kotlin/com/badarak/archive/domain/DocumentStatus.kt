package com.badarak.archive.domain

/**
 * Lifecycle of an archived document.
 *
 * A document starts as [PENDING] while its content is being stored, then becomes either
 * [AVAILABLE] or [FAILED]. Both are terminal: a failed archiving is retried as a new document.
 */
enum class DocumentStatus {
    PENDING,
    AVAILABLE,
    FAILED,
    ;

    fun canTransitionTo(target: DocumentStatus): Boolean = this == PENDING && target != PENDING

    companion object {
        val INITIAL = PENDING
    }
}
