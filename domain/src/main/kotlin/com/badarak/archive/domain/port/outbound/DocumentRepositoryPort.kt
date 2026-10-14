package com.badarak.archive.domain.port.outbound

import com.badarak.archive.domain.model.ArchivedDocument

/** Stores the metadata and archiving state of documents. */
interface DocumentRepositoryPort {

    /**
     * Inserts [document], or replaces the stored state of the document with the same identifier.
     *
     * @throws DocumentPersistenceException when the store cannot be reached or refuses the document
     */
    fun save(document: ArchivedDocument)
}

/** The document store failed; the cause holds the underlying error. */
class DocumentPersistenceException(message: String, cause: Throwable) : RuntimeException(message, cause)
