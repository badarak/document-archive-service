package com.badarak.archive.domain.service

import com.badarak.archive.domain.port.inbound.DocumentDownload
import com.badarak.archive.domain.port.inbound.DocumentQuery
import com.badarak.archive.domain.port.inbound.DownloadDocumentUseCase
import com.badarak.archive.domain.port.inbound.GetDocumentUseCase
import com.badarak.archive.domain.port.outbound.ObjectStorage

/** Checks the access with [GetDocumentUseCase] before touching the storage, so both operations share one access rule. */
class DownloadDocumentService(private val getDocument: GetDocumentUseCase, private val storage: ObjectStorage) :
    DownloadDocumentUseCase {

    override fun download(query: DocumentQuery): DocumentDownload {
        val document = getDocument.get(query)
        return DocumentDownload(document, storage.open(document.content.objectKey))
    }
}
