package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.ArchivedDocument

/** Archives a document sent by a caller: its content goes to the object storage, its metadata to the document store. */
interface ArchiveDocumentUseCase {

    /** Returns the archived document, AVAILABLE. */
    fun archive(command: ArchiveDocumentCommand): ArchivedDocument
}
