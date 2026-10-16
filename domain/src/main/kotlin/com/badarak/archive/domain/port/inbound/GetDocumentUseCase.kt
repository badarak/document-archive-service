package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.ArchivedDocument

interface GetDocumentUseCase {

    /**
     * Returns the document if it belongs to the caller's tenant and is AVAILABLE.
     *
     * @throws com.badarak.archive.domain.model.DocumentNotFoundException otherwise, whether the document does not exist,
     * belongs to another tenant or is in another status, so that its existence is never revealed
     */
    fun get(query: DocumentQuery): ArchivedDocument
}
