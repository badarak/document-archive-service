package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.DocumentPage

interface SearchDocumentsUseCase {

    /**
     * Returns the requested page of the AVAILABLE documents of the caller's tenant that match the criteria, newest first.
     * A page beyond the last one is empty.
     *
     * @throws com.badarak.archive.domain.port.outbound.DocumentPersistenceException when the document store cannot be reached
     */
    fun search(query: DocumentSearchQuery): DocumentPage
}
