package com.badarak.archive.domain.service

import com.badarak.archive.domain.model.ArchivedDocument
import com.badarak.archive.domain.model.DocumentContent
import com.badarak.archive.domain.model.DocumentFormat
import com.badarak.archive.domain.model.DocumentFormatPolicy
import com.badarak.archive.domain.model.DocumentId
import com.badarak.archive.domain.model.DocumentOwner
import com.badarak.archive.domain.model.DocumentSizePolicy
import com.badarak.archive.domain.model.FileName
import com.badarak.archive.domain.model.ObjectKey
import com.badarak.archive.domain.port.inbound.ArchiveDocumentCommand
import com.badarak.archive.domain.port.inbound.ArchiveDocumentUseCase
import com.badarak.archive.domain.port.outbound.DocumentPersistenceException
import com.badarak.archive.domain.port.outbound.DocumentRepository
import com.badarak.archive.domain.port.outbound.ObjectStorage
import com.badarak.archive.domain.port.outbound.ObjectStorageException
import java.io.InputStream
import java.time.Clock

/**
 * Archives a document: validates it, records it as PENDING, uploads its content, then records it
 * as AVAILABLE.
 *
 * A refused document leaves no trace. When the upload or the final save fails, the document is
 * recorded as FAILED and the error is rethrown; if even that save fails, its error is attached to
 * the original one as suppressed and the document stays PENDING. A document is never AVAILABLE
 * without its content.
 */
class ArchiveDocumentService(
    private val documents: DocumentRepository,
    private val storage: ObjectStorage,
    private val formatPolicy: DocumentFormatPolicy,
    private val sizePolicy: DocumentSizePolicy,
    private val clock: Clock,
) : ArchiveDocumentUseCase {

    override fun archive(command: ArchiveDocumentCommand): ArchivedDocument {
        val file = command.file
        val stream = file.content.buffered()
        val fileName = FileName.of(file.fileName)
        sizePolicy.check(file.sizeBytes)
        formatPolicy.check(fileName, file.contentType, readHeader(stream))
        val pending = ArchivedDocument.create(
            id = DocumentId.generate(),
            owner = DocumentOwner(command.caller.tenantId, command.caller.source),
            metadata = command.metadata,
            content = DocumentContent(fileName.value, formatPolicy.mediaType(file.contentType), file.sizeBytes, ObjectKey.generate()),
            sizePolicy = sizePolicy,
            now = clock.instant(),
        )

        documents.save(pending)
        try {
            storage.store(pending.content.objectKey, stream, pending.content.sizeBytes, pending.content.contentType)
        } catch (e: ObjectStorageException) {
            recordFailure(pending, e)
            throw e
        }
        val available = pending.markAvailable(clock.instant())
        try {
            documents.save(available)
        } catch (e: DocumentPersistenceException) {
            recordFailure(pending, e)
            throw e
        }
        return available
    }

    private fun readHeader(stream: InputStream): ByteArray {
        stream.mark(DocumentFormat.HEADER_LENGTH)
        val header = stream.readNBytes(DocumentFormat.HEADER_LENGTH)
        stream.reset()
        return header
    }

    private fun recordFailure(pending: ArchivedDocument, cause: Exception) {
        try {
            documents.save(pending.markFailed(clock.instant()))
        } catch (e: DocumentPersistenceException) {
            cause.addSuppressed(e)
        }
    }
}
