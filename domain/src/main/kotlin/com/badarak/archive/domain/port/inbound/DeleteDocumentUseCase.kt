package com.badarak.archive.domain.port.inbound

interface DeleteDocumentUseCase {

    /**
     * Removes the content of a document of the caller's tenant from the storage, then records the document as DELETED,
     * keeping its metadata. The document is first recorded as DELETING, so that it can no longer be read. Deleting a
     * DELETED document succeeds, and a document left DELETING by an interrupted deletion is finished, so that a deletion
     * can be replayed.
     *
     * @throws com.badarak.archive.domain.model.DocumentNotFoundException when the document does not exist, belongs to
     * another tenant, or is PENDING or FAILED
     * @throws com.badarak.archive.domain.port.outbound.ObjectStorageException when the storage cannot be reached; the
     * document then stays DELETING
     * @throws com.badarak.archive.domain.port.outbound.DocumentPersistenceException when the document store cannot be reached
     */
    fun delete(query: DocumentQuery)
}
