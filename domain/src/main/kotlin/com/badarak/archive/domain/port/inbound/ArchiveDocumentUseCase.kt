package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.ArchivedDocument

interface ArchiveDocumentUseCase {

    /** Stores the content of the document in the object storage and its metadata in the document store, then returns it AVAILABLE. */
    fun archive(command: ArchiveDocumentCommand): ArchivedDocument
}
