package com.badarak.archive.application.port

import com.badarak.archive.domain.ArchivedDocument

/** Stores the metadata and archiving state of documents. */
interface DocumentRepositoryPort {

    /** Inserts [document], or replaces the stored state of the document with the same identifier. */
    fun save(document: ArchivedDocument)
}
