package com.badarak.archive.domain.port.inbound

interface DownloadDocumentUseCase {

    /**
     * Opens the content of a document the caller may read, under the same rule as [GetDocumentUseCase.get]. The caller
     * must close [DocumentDownload.content].
     *
     * @throws com.badarak.archive.domain.model.DocumentNotFoundException when the caller may not read the document
     * @throws com.badarak.archive.domain.port.outbound.MissingObjectException when the document's content is missing
     * @throws com.badarak.archive.domain.port.outbound.ObjectStorageException when the storage cannot be reached
     */
    fun download(query: DocumentQuery): DocumentDownload
}
